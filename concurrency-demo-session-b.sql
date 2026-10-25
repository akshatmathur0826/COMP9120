-- Fraud Detection Database - concurrency demonstration, SESSION B (run while A waits)
-- See concurrency-demo-session-a.sql for instructions.

\pset pager off
SET search_path TO fraud_detection;

BEGIN;

\echo '[B] Velocity history read WITHOUT the lock (READ COMMITTED snapshot):'
\echo '    A''s attempt is uncommitted, so it is invisible here. Two intakes reading'
\echo '    like this at the same time could each miss the R3 five-attempt threshold.'
SELECT COUNT(*) AS attempts_last_10_min_without_lock
FROM payment_transaction
WHERE card_id = 1002 AND occurred_at >= CURRENT_TIMESTAMP - INTERVAL '10 minutes';

\echo
\echo '[B] Requesting the card lock. This WAITS until session A commits ...'
SELECT clock_timestamp() AS b_requested_at \gset
SELECT card_id, status FROM card WHERE card_id = 1002 FOR UPDATE;
SELECT to_char(clock_timestamp(), 'HH24:MI:SS.MS') AS b_locked_at,
       date_trunc('millisecond', clock_timestamp() - :'b_requested_at'::timestamptz) AS b_waited;

\echo '[B] Velocity history read AFTER acquiring the lock: A''s attempt is now counted.'
SELECT COUNT(*) AS attempts_last_10_min_with_lock
FROM payment_transaction
WHERE card_id = 1002 AND occurred_at >= CURRENT_TIMESTAMP - INTERVAL '10 minutes';

ROLLBACK;
\echo '[B] Done (rolled back; B wrote nothing).'
