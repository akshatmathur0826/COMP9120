# Database Test Cases and Expected Evidence

Load `schema.sql` and `seed.sql` before running these checks. The table gives the team repeatable evidence for the SQL, integrity, and transaction parts of the report.

## Seed-query checks

| ID | Execute | Expected result | Requirement evidenced |
|---|---|---|---|
| Q1 | Query Q1 with start `2026-09-01 00:00:00+00` and end `2026-10-01 00:00:00+00` | One row for Alice Nguyen's account, with `alert_count = 2` and `high_risk_alert_count = 2`. | Repeated alert analysis. |
| Q2 | Query Q2 over September 2026 | Tech Hub Electronics and Luxury Watch House each have one high-risk transaction. | Merchant pattern analysis. |
| Q3 | Query Q3 with history count `5` and multiplier `3.0` | The seeded AUD 6,200 and AUD 5,200 Alice transactions appear. | Correlated historical aggregation. |
| Q4 | Query Q4 | R1 and R2 have two matches each; R3, R4, and R5 have one each. | Grouping and rule-performance dashboard. |
| Q5 | Query Q5 with attempt count `5` and interval `INTERVAL '10 minutes'` | Ben Carter's transaction `10018` appears with five attempts in the window. | Velocity pattern detection. |
| Q6 | Query Q6 with both filters NULL | Three alerts appear: 9001 OPEN, 9002 RESOLVED, 9003 INVESTIGATING. | Analyst alert queue. |
| Q7 | Query Q7 | Jordan Lee has two active alerts; Priya Rao has one resolved alert. | Analyst workload. |
| Q8 | Query Q8 with transaction ID `10018` | `attempts_in_preceding_ten_minutes = 5`. | Rule-level velocity evidence. |

## Constraint checks

Run each invalid statement in a separate transaction, expect an error, and roll it back. Capture the error text or a screenshot for the report appendix.

```sql
SET search_path TO fraud_detection;

-- Amount must be positive.
BEGIN;
INSERT INTO payment_transaction
    (request_id, card_id, merchant_id, device_id, location_id, amount, occurred_at, auth_outcome)
VALUES
    ('req-invalid-zero', 1001, 1, 1, 1, 0, CURRENT_TIMESTAMP, 'APPROVED');
ROLLBACK;

-- Client request IDs are idempotency keys and must be unique.
BEGIN;
INSERT INTO payment_transaction
    (request_id, card_id, merchant_id, device_id, location_id, amount, occurred_at, auth_outcome)
VALUES
    ('req-alice-001', 1001, 1, 1, 1, 10.00, CURRENT_TIMESTAMP, 'APPROVED');
ROLLBACK;

-- A terminal alert requires an analyst note and review timestamp.
BEGIN;
UPDATE fraud_alert
SET status = 'RESOLVED', reviewed_at = NULL, analyst_note = NULL
WHERE alert_id = 9001;
ROLLBACK;

-- Transaction foreign keys must reference real entities.
BEGIN;
INSERT INTO payment_transaction
    (request_id, card_id, merchant_id, device_id, location_id, amount, occurred_at, auth_outcome)
VALUES
    ('req-invalid-card', 999999, 1, 1, 1, 10.00, CURRENT_TIMESTAMP, 'APPROVED');
ROLLBACK;
```

## Service-level transaction tests

These are implemented as Spring Boot integration tests in `backend/src/test/java/com/frauddetection/TransactionIntegrityTests.java` (14 tests; run instructions in the README). The database constraints still provide a second line of defence.

| ID | Scenario | Pass condition |
|---|---|---|
| T1 | Submit a normal valid transaction | Exactly one transaction and LOW assessment are committed; no `RULE_MATCH` or `FRAUD_ALERT` rows exist for it. |
| T2 | Submit a AUD 5,000 transaction from a known device | R1 match, score 40, MEDIUM assessment, and one alert are committed atomically. |
| T3 | Submit AUD 6,200 from an unseen device for Alice | R1 + R2 + R4 matches, score 100, HIGH assessment, and one alert are committed atomically. |
| T4 | Submit four rapid attempts, then the fifth | R3 does not match before the fifth; it matches at the fifth attempt. Test the exact 10-minute boundary agreed by the team. |
| T5 | Force an exception after assessment creation but before alert creation | No rows for the new transaction, assessment, matches, or alert survive after rollback. |
| T6 | Submit the same `requestId` twice | The second request returns/retrieves the original result or receives a controlled duplicate response; it must not create a second transaction. |
| T7 | Submit two same-card attempts concurrently | `SELECT ... FOR UPDATE` on the card serialises history reads; velocity evaluation is deterministic. |
| T8 | Attempt invalid review transitions | The service rejects transitions outside OPEN -> INVESTIGATING/DISMISSED and INVESTIGATING -> RESOLVED/DISMISSED; terminal transitions carry a note and write `ALERT_REVIEW`. |

How each test proves its pass condition:

| ID | Test method | Notes |
|---|---|---|
| T1 | `normalTransaction` | Checks the assessment, rule-match and alert row counts directly. |
| T2 | `largeAmountBoundary` | AUD 4,999.99 does not match; AUD 5,000.00 matches R1 only, because Chloe has fewer than five earlier approvals (no R2). |
| T3 | `newDeviceLargeAmount` | Also `newDeviceBoundary`: R4 at AUD 1,000.00 but not 999.99. |
| T4 | `velocityBoundary`, `velocityWindowEdge` | Fifth attempt matches. The window is inclusive at exactly 10:00 and excludes the first attempt at 10:01. `repeatedDeclines` covers R5. |
| T5 | `forcedAlertFailureRollsBackEverything` | A test-only trigger makes the alert insert fail. Transaction, assessment, match and alert counts are unchanged, and the same request succeeds once the fault is removed. `reviewAuditFailureRollsBackStatus` does the same for the review transaction. |
| T6 | `duplicateRequestId` | The second submission is rejected and the row count is unchanged. |
| T7 | `concurrentSameCardIntakeIsSerialised` | Six simultaneous attempts on one card at the same instant; exactly attempts 5 and 6 match R3. A test-only trigger pauses each intake after its insert so the race is real. With the `FOR UPDATE` card lock removed, this test fails with zero R3 matches. |
| T8 | `alertReviewTransitions` | Invalid transitions, the missing-note rule, audit rows and terminal states. |
| - | `blockedCardRejected`, `databaseConstraints` | Card status check, and CHECK/FK constraints enforced even when the service is bypassed. |

`transaction-demo.sql` and the two `concurrency-demo-session-*.sql` scripts show the same behaviour interactively in `psql` for the report and video.

## Large dataset checks (`seed-large.sql`)

- All eight queries in `queries.sql` return non-empty results. For example, Q1 finds 32 accounts with repeated alerts, Q3 finds 124 transactions above their card's history, and Q7 spreads the workload across five analysts.
- Every transaction has exactly one assessment; an alert exists if and only if the score is at least 30; every score equals the sum of its matched rule weights.
- Re-evaluating all 5 rules for all 6,378 transactions in SQL (the same logic as Part 1 of `transaction-demo.sql`) agrees with the stored rule matches in all 31,890 checks.

