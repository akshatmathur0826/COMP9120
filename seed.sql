-- Compact deterministic seed profile for the Fraud Detection Database.
-- Run after schema.sql. It intentionally resets only the fraud_detection demo data.

SET search_path TO fraud_detection;
BEGIN;

TRUNCATE TABLE
    alert_review,
    fraud_alert,
    rule_match,
    risk_assessment,
    payment_transaction,
    fraud_rule,
    analyst,
    location,
    device,
    merchant,
    card,
    account,
    customer
RESTART IDENTITY CASCADE;

INSERT INTO customer (customer_id, full_name, email, created_at) VALUES
    (1, 'Alice Nguyen', 'alice.nguyen@example.test', '2026-01-10 09:00:00+00'),
    (2, 'Ben Carter',   'ben.carter@example.test',   '2026-01-12 09:00:00+00'),
    (3, 'Chloe Singh',  'chloe.singh@example.test',  '2026-01-15 09:00:00+00');

INSERT INTO account (account_id, customer_id, account_number, opened_at, status) VALUES
    (101, 1, 'ACCT-ALICE-001', '2026-01-10 09:05:00+00', 'ACTIVE'),
    (102, 2, 'ACCT-BEN-001',   '2026-01-12 09:05:00+00', 'ACTIVE'),
    (103, 3, 'ACCT-CHLOE-001', '2026-01-15 09:05:00+00', 'ACTIVE');

INSERT INTO card (card_id, account_id, card_token, status, issued_at) VALUES
    (1001, 101, 'tok_alice_primary', 'ACTIVE', '2026-01-10 09:10:00+00'),
    (1002, 102, 'tok_ben_primary',   'ACTIVE', '2026-01-12 09:10:00+00'),
    (1003, 103, 'tok_chloe_primary', 'ACTIVE', '2026-01-15 09:10:00+00');

INSERT INTO merchant (merchant_id, merchant_name, category, merchant_ref) VALUES
    (1, 'Harbour Grocer',        'GROCERIES',   'MER-HG-001'),
    (2, 'City Cafe',             'DINING',      'MER-CC-001'),
    (3, 'Tech Hub Electronics',  'ELECTRONICS', 'MER-TH-001'),
    (4, 'Metro Fuel',            'FUEL',        'MER-MF-001'),
    (5, 'Luxury Watch House',    'LUXURY',      'MER-LW-001');

INSERT INTO device (device_id, device_fingerprint, first_seen_at) VALUES
    (1, 'dev_alice_phone', '2026-08-01 00:00:00+00'),
    (2, 'dev_ben_phone',   '2026-08-01 00:00:00+00'),
    (3, 'dev_chloe_phone', '2026-08-01 00:00:00+00'),
    (4, 'dev_alice_new',   '2026-09-20 01:00:00+00');

INSERT INTO location (location_id, city, country_code) VALUES
    (1, 'Sydney', 'AU'),
    (2, 'Melbourne', 'AU'),
    (3, 'Brisbane', 'AU');

INSERT INTO analyst (analyst_id, display_name, email, active) VALUES
    (1, 'Jordan Lee', 'jordan.lee@example.test', TRUE),
    (2, 'Priya Rao',  'priya.rao@example.test',  TRUE);

INSERT INTO fraud_rule (
    rule_id, rule_code, title, description, severity_weight, active, rule_set_version,
    minimum_amount, minimum_history_count, historical_multiplier, event_window_seconds, minimum_event_count
) VALUES
    (1, 'R1', 'Large amount',
     'Amount is at least AUD 5,000.', 40, TRUE, '2026.1', 5000.00, NULL, NULL, NULL, NULL),
    (2, 'R2', 'High versus history',
     'At least five earlier approved transactions and amount is at least max(AUD 500, three times the prior average).',
     30, TRUE, '2026.1', 500.00, 5, 3.00, NULL, NULL),
    (3, 'R3', 'Transaction velocity',
     'At least five attempts on one card in the preceding ten-minute window, including the new attempt.',
     30, TRUE, '2026.1', NULL, NULL, NULL, 600, 5),
    (4, 'R4', 'New device and high amount',
     'No earlier transaction for this card and device pair, and amount is at least AUD 1,000.',
     30, TRUE, '2026.1', 1000.00, NULL, NULL, NULL, NULL),
    (5, 'R5', 'Repeated declines',
     'At least three declined attempts on one card in the preceding five-minute window, including the new attempt.',
     25, TRUE, '2026.1', NULL, NULL, NULL, 300, 3);

-- Alice has six normal approved transactions, then two alerting purchases.
-- Ben has a five-attempt burst. Chloe has a repeated-decline example.
INSERT INTO payment_transaction (
    transaction_id, request_id, card_id, merchant_id, device_id, location_id,
    amount, occurred_at, auth_outcome, recorded_at
) VALUES
    (10001, 'req-alice-001', 1001, 1, 1, 1,   45.00, '2026-09-01 00:30:00+00', 'APPROVED', '2026-09-01 00:30:01+00'),
    (10002, 'req-alice-002', 1001, 2, 1, 1,   73.50, '2026-09-03 02:15:00+00', 'APPROVED', '2026-09-03 02:15:01+00'),
    (10003, 'req-alice-003', 1001, 4, 1, 1,   68.00, '2026-09-05 01:20:00+00', 'APPROVED', '2026-09-05 01:20:01+00'),
    (10004, 'req-alice-004', 1001, 1, 1, 1,   91.25, '2026-09-08 00:10:00+00', 'APPROVED', '2026-09-08 00:10:01+00'),
    (10005, 'req-alice-005', 1001, 2, 1, 1,   36.00, '2026-09-11 03:00:00+00', 'APPROVED', '2026-09-11 03:00:01+00'),
    (10006, 'req-alice-006', 1001, 1, 1, 1,   54.75, '2026-09-15 01:45:00+00', 'APPROVED', '2026-09-15 01:45:01+00'),
    (10007, 'req-alice-007', 1001, 3, 4, 2, 6200.00, '2026-09-20 01:00:00+00', 'APPROVED', '2026-09-20 01:00:01+00'),
    (10008, 'req-alice-008', 1001, 5, 1, 1, 5200.00, '2026-09-25 02:00:00+00', 'APPROVED', '2026-09-25 02:00:01+00'),
    (10009, 'req-ben-001',   1002, 1, 2, 1,   25.00, '2026-09-10 01:10:00+00', 'APPROVED', '2026-09-10 01:10:01+00'),
    (10010, 'req-ben-002',   1002, 2, 2, 1,   33.00, '2026-09-11 01:10:00+00', 'APPROVED', '2026-09-11 01:10:01+00'),
    (10011, 'req-ben-003',   1002, 4, 2, 1,   18.00, '2026-09-12 01:10:00+00', 'APPROVED', '2026-09-12 01:10:01+00'),
    (10012, 'req-ben-004',   1002, 1, 2, 1,   22.00, '2026-09-13 01:10:00+00', 'APPROVED', '2026-09-13 01:10:01+00'),
    (10013, 'req-ben-005',   1002, 2, 2, 1,   30.00, '2026-09-14 01:10:00+00', 'APPROVED', '2026-09-14 01:10:01+00'),
    (10014, 'req-ben-006',   1002, 2, 2, 1,   15.00, '2026-09-21 00:00:00+00', 'APPROVED', '2026-09-21 00:00:01+00'),
    (10015, 'req-ben-007',   1002, 1, 2, 1,   12.00, '2026-09-21 00:02:00+00', 'APPROVED', '2026-09-21 00:02:01+00'),
    (10016, 'req-ben-008',   1002, 4, 2, 1,   25.00, '2026-09-21 00:04:00+00', 'APPROVED', '2026-09-21 00:04:01+00'),
    (10017, 'req-ben-009',   1002, 2, 2, 1,   18.00, '2026-09-21 00:06:00+00', 'APPROVED', '2026-09-21 00:06:01+00'),
    (10018, 'req-ben-010',   1002, 1, 2, 1,   20.00, '2026-09-21 00:08:00+00', 'APPROVED', '2026-09-21 00:08:01+00'),
    (10019, 'req-chloe-001', 1003, 1, 3, 3,   42.00, '2026-09-17 01:00:00+00', 'APPROVED', '2026-09-17 01:00:01+00'),
    (10020, 'req-chloe-002', 1003, 2, 3, 3,   61.00, '2026-09-18 01:00:00+00', 'APPROVED', '2026-09-18 01:00:01+00'),
    (10021, 'req-chloe-003', 1003, 3, 3, 3,  410.00, '2026-09-23 02:00:00+00', 'DECLINED', '2026-09-23 02:00:01+00'),
    (10022, 'req-chloe-004', 1003, 3, 3, 3,  410.00, '2026-09-23 02:02:00+00', 'DECLINED', '2026-09-23 02:02:01+00'),
    (10023, 'req-chloe-005', 1003, 3, 3, 3,  410.00, '2026-09-23 02:04:00+00', 'DECLINED', '2026-09-23 02:04:01+00');

-- Every transaction receives one assessment. Most are LOW, no-rule snapshots.
INSERT INTO risk_assessment (assessment_id, transaction_id, score, risk_level, assessed_at, rule_set_version)
SELECT 40000 + transaction_id, transaction_id, 0, 'LOW', recorded_at, '2026.1'
FROM payment_transaction;

UPDATE risk_assessment
SET score = 100, risk_level = 'HIGH'
WHERE transaction_id = 10007;
UPDATE risk_assessment
SET score = 70, risk_level = 'HIGH'
WHERE transaction_id = 10008;
UPDATE risk_assessment
SET score = 30, risk_level = 'MEDIUM'
WHERE transaction_id = 10018;
UPDATE risk_assessment
SET score = 25, risk_level = 'LOW'
WHERE transaction_id = 10023;

INSERT INTO rule_match (assessment_id, rule_id, observed_value, explanation) VALUES
    (50007, 1, 6200.00, 'Amount AUD 6,200.00 is at least the R1 threshold of AUD 5,000.00.'),
    (50007, 2, 6200.00, 'Six earlier approved transactions average AUD 61.42; the amount exceeds three times that average and AUD 500.'),
    (50007, 4, 6200.00, 'No earlier transaction exists for card 1001 and device dev_alice_new; amount is at least AUD 1,000.00.'),
    (50008, 1, 5200.00, 'Amount AUD 5,200.00 is at least the R1 threshold of AUD 5,000.00.'),
    (50008, 2, 5200.00, 'Seven earlier approved transactions provide sufficient history; the amount exceeds the R2 threshold.'),
    (50018, 3,    5.00, 'This is the fifth card 1002 attempt from 00:00 through 00:08 UTC, within ten minutes.'),
    (50023, 5,    3.00, 'This is the third declined attempt for card 1003 within five minutes.');

INSERT INTO fraud_alert (
    alert_id, assessment_id, assigned_analyst_id, status, created_at, reviewed_at, analyst_note
) VALUES
    (9001, 50007, 1, 'OPEN',          '2026-09-20 01:00:02+00', NULL, NULL),
    (9002, 50008, 2, 'RESOLVED',      '2026-09-25 02:00:02+00', '2026-09-25 05:30:00+00',
     'Customer confirmed this simulated luxury purchase during analyst review.'),
    (9003, 50018, 1, 'INVESTIGATING', '2026-09-21 00:08:02+00', NULL, NULL);

INSERT INTO alert_review (
    review_id, alert_id, changed_by_analyst_id, changed_at, old_status, new_status, note
) VALUES
    (1, 9002, 2, '2026-09-25 02:20:00+00', 'OPEN', 'INVESTIGATING', 'Reviewed transaction and contacted the simulated customer.'),
    (2, 9002, 2, '2026-09-25 05:30:00+00', 'INVESTIGATING', 'RESOLVED',
     'Customer confirmed the purchase; alert resolved.'),
    (3, 9003, 1, '2026-09-21 00:10:00+00', 'OPEN', 'INVESTIGATING', 'Investigating rapid sequence of small purchases.');

-- Advance BIGSERIAL sequences after explicit deterministic IDs.
SELECT setval(pg_get_serial_sequence('fraud_detection.customer', 'customer_id'), (SELECT MAX(customer_id) FROM customer), TRUE);
SELECT setval(pg_get_serial_sequence('fraud_detection.account', 'account_id'), (SELECT MAX(account_id) FROM account), TRUE);
SELECT setval(pg_get_serial_sequence('fraud_detection.card', 'card_id'), (SELECT MAX(card_id) FROM card), TRUE);
SELECT setval(pg_get_serial_sequence('fraud_detection.merchant', 'merchant_id'), (SELECT MAX(merchant_id) FROM merchant), TRUE);
SELECT setval(pg_get_serial_sequence('fraud_detection.device', 'device_id'), (SELECT MAX(device_id) FROM device), TRUE);
SELECT setval(pg_get_serial_sequence('fraud_detection.location', 'location_id'), (SELECT MAX(location_id) FROM location), TRUE);
SELECT setval(pg_get_serial_sequence('fraud_detection.analyst', 'analyst_id'), (SELECT MAX(analyst_id) FROM analyst), TRUE);
SELECT setval(pg_get_serial_sequence('fraud_detection.fraud_rule', 'rule_id'), (SELECT MAX(rule_id) FROM fraud_rule), TRUE);
SELECT setval(pg_get_serial_sequence('fraud_detection.payment_transaction', 'transaction_id'), (SELECT MAX(transaction_id) FROM payment_transaction), TRUE);
SELECT setval(pg_get_serial_sequence('fraud_detection.risk_assessment', 'assessment_id'), (SELECT MAX(assessment_id) FROM risk_assessment), TRUE);
SELECT setval(pg_get_serial_sequence('fraud_detection.fraud_alert', 'alert_id'), (SELECT MAX(alert_id) FROM fraud_alert), TRUE);
SELECT setval(pg_get_serial_sequence('fraud_detection.alert_review', 'review_id'), (SELECT MAX(review_id) FROM alert_review), TRUE);

COMMIT;

