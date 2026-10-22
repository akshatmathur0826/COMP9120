-- Fraud Detection Database - report-ready PostgreSQL queries
-- Each query is read-only. Parameters use PostgreSQL's $1, $2 notation where appropriate.

SET search_path TO fraud_detection;

-- Q1. Accounts with repeated alerts. Supports an analyst prioritising customers/accounts
-- with recurring suspicious activity. $1 = inclusive start time; $2 = exclusive end time.
SELECT
    a.account_id,
    a.account_number,
    c.customer_id,
    c.full_name AS customer_name,
    COUNT(fa.alert_id) AS alert_count,
    COUNT(*) FILTER (WHERE ra.risk_level = 'HIGH') AS high_risk_alert_count,
    MAX(fa.created_at) AS latest_alert_at
FROM account a
JOIN customer c ON c.customer_id = a.customer_id
JOIN card cd ON cd.account_id = a.account_id
JOIN payment_transaction t ON t.card_id = cd.card_id
JOIN risk_assessment ra ON ra.transaction_id = t.transaction_id
JOIN fraud_alert fa ON fa.assessment_id = ra.assessment_id
WHERE fa.created_at >= $1
  AND fa.created_at < $2
GROUP BY a.account_id, a.account_number, c.customer_id, c.full_name
HAVING COUNT(fa.alert_id) >= 2
ORDER BY alert_count DESC, latest_alert_at DESC;

-- Q2. Merchants associated with high-risk transactions. Supports pattern analysis by
-- merchant/category. $1 = inclusive start time; $2 = exclusive end time.
SELECT
    m.merchant_id,
    m.merchant_name,
    m.category,
    COUNT(*) AS high_risk_transaction_count,
    ROUND(SUM(t.amount), 2) AS high_risk_amount,
    COUNT(fa.alert_id) AS alerts_created
FROM merchant m
JOIN payment_transaction t ON t.merchant_id = m.merchant_id
JOIN risk_assessment ra ON ra.transaction_id = t.transaction_id
LEFT JOIN fraud_alert fa ON fa.assessment_id = ra.assessment_id
WHERE ra.risk_level = 'HIGH'
  AND t.occurred_at >= $1
  AND t.occurred_at < $2
GROUP BY m.merchant_id, m.merchant_name, m.category
ORDER BY high_risk_transaction_count DESC, high_risk_amount DESC;

-- Q3. Transactions greater than each card's historical average. Uses a correlated
-- subquery and explicitly excludes the current and later events by (occurred_at, id).
-- $1 = minimum number of earlier approved transactions; $2 = multiplier.
SELECT
    t.transaction_id,
    t.occurred_at,
    cd.card_token,
    c.full_name AS customer_name,
    t.amount,
    history.prior_approved_count,
    ROUND(history.prior_approved_average, 2) AS prior_approved_average,
    ROUND(t.amount / NULLIF(history.prior_approved_average, 0), 2) AS amount_to_average_ratio
FROM payment_transaction t
JOIN card cd ON cd.card_id = t.card_id
JOIN account a ON a.account_id = cd.account_id
JOIN customer c ON c.customer_id = a.customer_id
CROSS JOIN LATERAL (
    SELECT
        COUNT(*) AS prior_approved_count,
        AVG(h.amount) AS prior_approved_average
    FROM payment_transaction h
    WHERE h.card_id = t.card_id
      AND h.auth_outcome = 'APPROVED'
      AND (h.occurred_at, h.transaction_id) < (t.occurred_at, t.transaction_id)
) AS history
WHERE history.prior_approved_count >= $1
  AND t.amount >= GREATEST(500.00, $2 * history.prior_approved_average)
ORDER BY amount_to_average_ratio DESC, t.occurred_at DESC;

-- Q4. Most frequently triggered fraud rules. Supports dashboard rule-performance
-- evidence. The left joins deliberately retain active rules with zero matches.
SELECT
    fr.rule_code,
    fr.title,
    fr.severity_weight,
    COUNT(rm.assessment_id) AS match_count,
    COUNT(DISTINCT fa.alert_id) AS linked_alert_count,
    ROUND(AVG(rm.observed_value), 2) AS average_observed_value
FROM fraud_rule fr
LEFT JOIN rule_match rm ON rm.rule_id = fr.rule_id
LEFT JOIN risk_assessment ra ON ra.assessment_id = rm.assessment_id
LEFT JOIN fraud_alert fa ON fa.assessment_id = ra.assessment_id
WHERE fr.active = TRUE
GROUP BY fr.rule_id, fr.rule_code, fr.title, fr.severity_weight
ORDER BY match_count DESC, fr.severity_weight DESC, fr.rule_code;

-- Q5. Customers with rapid transaction sequences. The correlated count identifies
-- attempts in the last ten minutes for the same card. It uses the full event-ordering
-- tuple so equal timestamps remain deterministic; $1 = number of attempts and
-- $2 = interval such as INTERVAL '10 minutes'.
SELECT
    c.customer_id,
    c.full_name AS customer_name,
    a.account_number,
    t.card_id,
    t.transaction_id AS threshold_transaction_id,
    t.occurred_at AS threshold_occurred_at,
    velocity.attempts_in_window
FROM payment_transaction t
CROSS JOIN LATERAL (
    SELECT COUNT(*) AS attempts_in_window
    FROM payment_transaction h
    WHERE h.card_id = t.card_id
      AND h.occurred_at >= t.occurred_at - $2
      AND (h.occurred_at, h.transaction_id) <= (t.occurred_at, t.transaction_id)
) AS velocity
JOIN card cd ON cd.card_id = t.card_id
JOIN account a ON a.account_id = cd.account_id
JOIN customer c ON c.customer_id = a.customer_id
WHERE velocity.attempts_in_window >= $1
ORDER BY velocity.attempts_in_window DESC, t.occurred_at DESC, t.transaction_id DESC;

-- Q6. Analyst alert queue. Supports the fraud analyst's day-to-day review action.
-- $1 may be NULL for all statuses; $2 may be NULL for a selected rule code.
SELECT
    fa.alert_id,
    fa.status,
    fa.created_at,
    ra.risk_level,
    ra.score,
    t.transaction_id,
    t.amount,
    t.occurred_at,
    c.full_name AS customer_name,
    m.merchant_name,
    an.display_name AS assigned_analyst,
    STRING_AGG(fr.rule_code, ', ' ORDER BY fr.rule_code) AS matched_rules
FROM fraud_alert fa
JOIN risk_assessment ra ON ra.assessment_id = fa.assessment_id
JOIN payment_transaction t ON t.transaction_id = ra.transaction_id
JOIN card cd ON cd.card_id = t.card_id
JOIN account a ON a.account_id = cd.account_id
JOIN customer c ON c.customer_id = a.customer_id
JOIN merchant m ON m.merchant_id = t.merchant_id
LEFT JOIN analyst an ON an.analyst_id = fa.assigned_analyst_id
JOIN rule_match rm ON rm.assessment_id = ra.assessment_id
JOIN fraud_rule fr ON fr.rule_id = rm.rule_id
WHERE ($1::VARCHAR IS NULL OR fa.status = $1)
  AND ($2::VARCHAR IS NULL OR fr.rule_code = $2)
GROUP BY
    fa.alert_id, fa.status, fa.created_at, ra.risk_level, ra.score,
    t.transaction_id, t.amount, t.occurred_at, c.full_name, m.merchant_name, an.display_name
ORDER BY
    CASE ra.risk_level WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END DESC,
    fa.created_at ASC;

-- Q7. Analyst workload. Supports supervisor balancing of active investigations.
SELECT
    an.analyst_id,
    an.display_name,
    COUNT(fa.alert_id) FILTER (WHERE fa.status IN ('OPEN', 'INVESTIGATING')) AS active_alerts,
    COUNT(fa.alert_id) FILTER (WHERE fa.status = 'RESOLVED') AS resolved_alerts,
    COUNT(fa.alert_id) FILTER (WHERE fa.status = 'DISMISSED') AS dismissed_alerts
FROM analyst an
LEFT JOIN fraud_alert fa ON fa.assigned_analyst_id = an.analyst_id
WHERE an.active = TRUE
GROUP BY an.analyst_id, an.display_name
ORDER BY active_alerts DESC, an.display_name;

-- Q8. Exact velocity-rule evidence for one candidate transaction. $1 = transaction ID.
-- The tuple comparison ensures only earlier attempts plus the candidate itself are counted.
SELECT
    candidate.transaction_id,
    candidate.card_id,
    candidate.occurred_at,
    COUNT(history.transaction_id) AS attempts_in_preceding_ten_minutes
FROM payment_transaction candidate
JOIN payment_transaction history
  ON history.card_id = candidate.card_id
 AND (history.occurred_at, history.transaction_id) <= (candidate.occurred_at, candidate.transaction_id)
 AND history.occurred_at >= candidate.occurred_at - INTERVAL '10 minutes'
WHERE candidate.transaction_id = $1
GROUP BY candidate.transaction_id, candidate.card_id, candidate.occurred_at;
