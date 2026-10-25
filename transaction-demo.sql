-- Fraud Detection Database - multi-step transaction demonstration (psql)
--
-- Shows the intake and review transactions as plain SQL, so the report and video can
-- evidence transaction boundaries, commit, failure and rollback. The Spring service
-- (TransactionProcessingService / AlertReviewService) runs the same steps in one
-- @Transactional method; the rule evaluation in Part 1 mirrors its Java logic.
--
-- Run on a freshly seeded database for the documented results:
--     psql -d fraud_demo -f seed.sql
--     psql -d fraud_demo -f transaction-demo.sql
--
-- Part 1 COMMITs one new transaction (Chloe, AUD 6,200, unseen device: R1 + R4, HIGH).
-- Parts 2 and 3 fail on purpose and ROLL BACK, leaving no trace.
-- Reload seed.sql afterwards to return to the demo baseline.

\set ON_ERROR_STOP off
\set ON_ERROR_ROLLBACK off
\pset pager off
SET search_path TO fraud_detection;

\echo
\echo '================================================================'
\echo ' Baseline row counts'
\echo '================================================================'
SELECT (SELECT COUNT(*) FROM payment_transaction) AS transactions,
       (SELECT COUNT(*) FROM risk_assessment)     AS assessments,
       (SELECT COUNT(*) FROM rule_match)          AS rule_matches,
       (SELECT COUNT(*) FROM fraud_alert)         AS alerts,
       (SELECT COUNT(*) FROM alert_review)        AS reviews;

SELECT 'demo-' || to_char(clock_timestamp(), 'YYYYMMDDHH24MISSMS') AS rid \gset

\echo
\echo '================================================================'
\echo ' Part 1 - Successful intake: one transaction, all-or-nothing'
\echo ' Chloe (card 1003) spends AUD 6,200 at Tech Hub on device 2,'
\echo ' which her card has never used.'
\echo '================================================================'

BEGIN;

\echo '-- Step 1: lock the card row so concurrent intakes for this card queue behind us'
SELECT c.card_id, c.status AS card_status, a.status AS account_status
FROM card c JOIN account a ON a.account_id = c.account_id
WHERE c.card_id = 1003
FOR UPDATE OF c, a;

\echo '-- Step 2: idempotency check (expect 0 rows)'
SELECT transaction_id FROM payment_transaction WHERE request_id = :'rid';

\echo '-- Step 3: record the transaction'
INSERT INTO payment_transaction
    (request_id, card_id, merchant_id, device_id, location_id, amount, occurred_at, auth_outcome)
VALUES (:'rid', 1003, 3, 2, 2, 6200.00, CURRENT_TIMESTAMP, 'APPROVED')
RETURNING transaction_id AS tx_id \gset
\echo 'transaction_id =' :tx_id

\echo '-- Step 4: evaluate every active rule against EARLIER history only'
CREATE TEMP TABLE demo_evaluation ON COMMIT DROP AS
WITH t AS (
    SELECT * FROM payment_transaction WHERE transaction_id = :tx_id
),
approved_history AS (
    SELECT COUNT(*) AS n, AVG(h.amount) AS avg_amount
    FROM payment_transaction h, t
    WHERE h.card_id = t.card_id
      AND h.auth_outcome = 'APPROVED'
      AND (h.occurred_at, h.transaction_id) < (t.occurred_at, t.transaction_id)
)
SELECT r.rule_id, r.rule_code, r.title, r.severity_weight, r.rule_set_version,
       CASE r.rule_code
           WHEN 'R1' THEN t.amount >= r.minimum_amount
           WHEN 'R2' THEN COALESCE(hist.n >= r.minimum_history_count
                                   AND t.amount >= GREATEST(r.minimum_amount,
                                                            r.historical_multiplier * hist.avg_amount), FALSE)
           WHEN 'R3' THEN (SELECT COUNT(*) FROM payment_transaction h
                           WHERE h.card_id = t.card_id
                             AND h.occurred_at >= t.occurred_at - make_interval(secs => r.event_window_seconds)
                             AND (h.occurred_at, h.transaction_id) <= (t.occurred_at, t.transaction_id))
                          >= r.minimum_event_count
           WHEN 'R4' THEN t.amount >= r.minimum_amount
                          AND NOT EXISTS (SELECT 1 FROM payment_transaction h
                                          WHERE h.card_id = t.card_id
                                            AND h.device_id = t.device_id
                                            AND (h.occurred_at, h.transaction_id) < (t.occurred_at, t.transaction_id))
           WHEN 'R5' THEN (SELECT COUNT(*) FROM payment_transaction h
                           WHERE h.card_id = t.card_id
                             AND h.auth_outcome = 'DECLINED'
                             AND h.occurred_at >= t.occurred_at - make_interval(secs => r.event_window_seconds)
                             AND (h.occurred_at, h.transaction_id) <= (t.occurred_at, t.transaction_id))
                          >= r.minimum_event_count
       END AS matched,
       t.amount AS observed_value,
       hist.n AS prior_approved_count,
       ROUND(hist.avg_amount, 2) AS prior_approved_average
FROM fraud_rule r CROSS JOIN t CROSS JOIN approved_history hist
WHERE r.active;

SELECT rule_code, title, severity_weight, matched, prior_approved_count, prior_approved_average
FROM demo_evaluation ORDER BY rule_id;

\echo '-- Step 5: store the assessment snapshot (score = sum of matched weights)'
INSERT INTO risk_assessment (transaction_id, score, risk_level, rule_set_version)
SELECT :tx_id, s.score,
       CASE WHEN s.score >= 60 THEN 'HIGH' WHEN s.score >= 30 THEN 'MEDIUM' ELSE 'LOW' END,
       (SELECT MIN(rule_set_version) FROM demo_evaluation)
FROM (SELECT COALESCE(SUM(severity_weight) FILTER (WHERE matched), 0)::INT AS score
      FROM demo_evaluation) s
RETURNING assessment_id AS assessment_id, score AS score, risk_level \gset
\echo 'assessment_id =' :assessment_id ' score =' :score ' risk =' :risk_level

\echo '-- Step 6: one RULE_MATCH row per matched rule'
INSERT INTO rule_match (assessment_id, rule_id, observed_value, explanation)
SELECT :assessment_id, rule_id, observed_value,
       rule_code || ' (' || title || ') matched on amount AUD ' || observed_value
FROM demo_evaluation
WHERE matched;

\echo '-- Step 7: raise an alert because score >= 30'
INSERT INTO fraud_alert (assessment_id, status)
SELECT :assessment_id, 'OPEN'
WHERE :score >= 30
RETURNING alert_id;

COMMIT;
\echo '-- COMMIT: all seven steps become visible together'

SELECT t.transaction_id, t.request_id, t.amount, ra.score, ra.risk_level,
       STRING_AGG(fr.rule_code, ' + ' ORDER BY fr.rule_code) AS matched_rules,
       fa.alert_id, fa.status AS alert_status
FROM payment_transaction t
JOIN risk_assessment ra ON ra.transaction_id = t.transaction_id
LEFT JOIN rule_match rm ON rm.assessment_id = ra.assessment_id
LEFT JOIN fraud_rule fr ON fr.rule_id = rm.rule_id
LEFT JOIN fraud_alert fa ON fa.assessment_id = ra.assessment_id
WHERE t.transaction_id = :tx_id
GROUP BY t.transaction_id, t.request_id, t.amount, ra.score, ra.risk_level, fa.alert_id, fa.status;

\echo
\echo '================================================================'
\echo ' Part 2 - Failure during intake rolls EVERYTHING back'
\echo ' The alert insert fails (closed alert with no note violates'
\echo ' ck_alert_closure_evidence) after the transaction, assessment'
\echo ' and rule match were already written inside the transaction.'
\echo '================================================================'

SELECT (SELECT COUNT(*) FROM payment_transaction) AS tx_before,
       (SELECT COUNT(*) FROM risk_assessment)     AS assessments_before,
       (SELECT COUNT(*) FROM rule_match)          AS matches_before,
       (SELECT COUNT(*) FROM fraud_alert)         AS alerts_before \gset

BEGIN;
SELECT card_id FROM card WHERE card_id = 1001 FOR UPDATE;

INSERT INTO payment_transaction
    (request_id, card_id, merchant_id, device_id, location_id, amount, occurred_at, auth_outcome)
VALUES (:'rid' || '-fail', 1001, 5, 1, 1, 7000.00, CURRENT_TIMESTAMP, 'APPROVED')
RETURNING transaction_id AS fail_tx \gset

INSERT INTO risk_assessment (transaction_id, score, risk_level, rule_set_version)
VALUES (:fail_tx, 40, 'MEDIUM', '2026.1')
RETURNING assessment_id AS fail_assessment \gset

INSERT INTO rule_match (assessment_id, rule_id, observed_value, explanation)
VALUES (:fail_assessment, 1, 7000.00, 'R1 matched on amount AUD 7000.00');

\echo '-- Inside the transaction, the partial work is visible to this session only:'
SELECT COUNT(*) AS rows_for_this_request FROM payment_transaction WHERE request_id = :'rid' || '-fail';

\echo '-- Step 7 fails (expect ERROR ... ck_alert_closure_evidence):'
INSERT INTO fraud_alert (assessment_id, status, reviewed_at, analyst_note)
VALUES (:fail_assessment, 'RESOLVED', NULL, NULL);

\echo '-- Any further statement is refused (expect ERROR: current transaction is aborted):'
SELECT 1;

ROLLBACK;
\echo '-- ROLLBACK: nothing from this attempt survives'

SELECT (SELECT COUNT(*) FROM payment_transaction) - :tx_before          AS tx_change,
       (SELECT COUNT(*) FROM risk_assessment)     - :assessments_before AS assessment_change,
       (SELECT COUNT(*) FROM rule_match)          - :matches_before     AS match_change,
       (SELECT COUNT(*) FROM fraud_alert)         - :alerts_before      AS alert_change,
       (SELECT COUNT(*) FROM payment_transaction WHERE request_id = :'rid' || '-fail') AS rows_for_this_request;

\echo
\echo '================================================================'
\echo ' Part 3 - Alert review is atomic too'
\echo ' Alert 9003 (INVESTIGATING): the audit row is written, then the'
\echo ' status update fails because the closing note is missing.'
\echo '================================================================'

SELECT alert_id, status, analyst_note,
       (SELECT COUNT(*) FROM alert_review WHERE alert_id = 9003) AS review_rows
FROM fraud_alert WHERE alert_id = 9003;

BEGIN;
SELECT status FROM fraud_alert WHERE alert_id = 9003 FOR UPDATE;

INSERT INTO alert_review (alert_id, changed_by_analyst_id, old_status, new_status, note)
VALUES (9003, 1, 'INVESTIGATING', 'RESOLVED', 'Customer confirmed the purchases.');

\echo '-- Expect ERROR ... ck_alert_closure_evidence (analyst_note left NULL):'
UPDATE fraud_alert
SET status = 'RESOLVED', reviewed_at = CURRENT_TIMESTAMP, analyst_note = NULL
WHERE alert_id = 9003;

ROLLBACK;
\echo '-- ROLLBACK: the audit row is gone and the alert is unchanged'

SELECT alert_id, status, analyst_note,
       (SELECT COUNT(*) FROM alert_review WHERE alert_id = 9003) AS review_rows
FROM fraud_alert WHERE alert_id = 9003;

\echo
\echo '================================================================'
\echo ' Final row counts (only Part 1 added rows)'
\echo '================================================================'
SELECT (SELECT COUNT(*) FROM payment_transaction) AS transactions,
       (SELECT COUNT(*) FROM risk_assessment)     AS assessments,
       (SELECT COUNT(*) FROM rule_match)          AS rule_matches,
       (SELECT COUNT(*) FROM fraud_alert)         AS alerts,
       (SELECT COUNT(*) FROM alert_review)        AS reviews;
