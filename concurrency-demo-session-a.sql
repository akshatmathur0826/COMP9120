-- Fraud Detection Database - concurrency demonstration, SESSION A (run first)
--
-- Shows why intake locks the card row (SELECT ... FOR UPDATE) before reading history.
-- Open two terminals on the same database:
--     Terminal 1:  psql -d fraud_demo -f concurrency-demo-session-a.sql
--     Terminal 2:  psql -d fraud_demo -f concurrency-demo-session-b.sql   (when A pauses)
-- A takes Ben's card lock and records an attempt, then waits for Enter. B shows it
-- cannot see A's uncommitted attempt, then blocks on the same lock until A commits.
-- Each run adds one attempt for card 1002; reload seed.sql to reset.

\pset pager off
SET search_path TO fraud_detection;

BEGIN;

\echo '[A] Locking card 1002 (Ben) ...'
SELECT card_id, status FROM card WHERE card_id = 1002 FOR UPDATE;
SELECT to_char(clock_timestamp(), 'HH24:MI:SS.MS') AS a_locked_at;

\echo '[A] Recording an attempt inside the open transaction (not yet committed)'
INSERT INTO payment_transaction
    (request_id, card_id, merchant_id, device_id, location_id, amount, occurred_at, auth_outcome)
VALUES ('demo-lock-a-' || to_char(clock_timestamp(), 'YYYYMMDDHH24MISSMS'),
        1002, 2, 2, 1, 15.00, CURRENT_TIMESTAMP, 'APPROVED')
RETURNING transaction_id AS a_tx \gset
INSERT INTO risk_assessment (transaction_id, score, risk_level, rule_set_version)
VALUES (:a_tx, 0, 'LOW', '2026.1');

\echo
\prompt '[A] Holding the card lock. Start session B in another terminal, then press Enter here to COMMIT ... ' continue_a

COMMIT;
SELECT to_char(clock_timestamp(), 'HH24:MI:SS.MS') AS a_committed_at;
\echo '[A] Committed and released the lock. Session B now continues.'
