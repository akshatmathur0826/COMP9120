# Fraud Detection Database Design

## Scope and design boundary

This database supports a fictional, explainable transaction-review workflow. It records card-payment attempts, evaluates deterministic fraud rules, retains an assessment for every transaction, and gives analysts an auditable alert queue. A risk label is a review priority, not a payment-authorisation decision or proof of fraud. The system uses AUD and synthetic data only.

The model deliberately excludes real fund movements, card authorisation, PAN/CVV storage, live banking/geolocation data, identity verification, production authentication, and machine-learning claims.

## Conceptual design - ER model

### Entities

| Entity | Purpose | Identifier |
|---|---|---|
| Customer | Person who owns one or more accounts | customer_id |
| Account | Customer financial account represented in the simulation | account_id |
| Card | Tokenised card linked to one account | card_id |
| Merchant | Business receiving a transaction | merchant_id |
| Device | Device fingerprint associated with a transaction | device_id |
| Location | City and country context supplied for a transaction | location_id |
| PaymentTransaction | One fictional card-payment attempt | transaction_id |
| FraudRule | Versioned, explainable rule definition and threshold data | rule_id |
| RiskAssessment | Immutable score/risk snapshot for one transaction | assessment_id |
| RuleMatch | Evidence that one rule matched one assessment | assessment_id + rule_id |
| FraudAlert | Analyst-review item created only when score is at least 30 | alert_id |
| Analyst | Team member who owns or changes an alert review | analyst_id |
| AlertReview | Immutable history of an alert-status transition | review_id |

### Relationships, cardinality, and participation

Use these statements to draw the final diagram in your lecturer's ER notation. The participation notes are just as important as the cardinalities.

| Relationship | Cardinality | Participation / meaning |
|---|---|---|
| Customer owns Account | Customer 1 : N Account | Every Account must have one Customer; a Customer may have zero or many Accounts. |
| Account has Card | Account 1 : N Card | Every Card must belong to one Account; an Account may have zero or many Cards. |
| Card initiates PaymentTransaction | Card 1 : N PaymentTransaction | Every PaymentTransaction must reference exactly one Card; a Card may have zero or many transactions. |
| Merchant receives PaymentTransaction | Merchant 1 : N PaymentTransaction | Every PaymentTransaction must reference one Merchant. |
| Device is used for PaymentTransaction | Device 1 : N PaymentTransaction | Every PaymentTransaction must reference one Device; a Device can be shared. |
| Location describes PaymentTransaction | Location 1 : N PaymentTransaction | Every PaymentTransaction must reference one supplied Location. |
| PaymentTransaction receives RiskAssessment | PaymentTransaction 1 : 1 RiskAssessment | Mandatory on both sides after successful intake: every stored transaction has exactly one assessment, and every assessment is for one transaction. |
| RiskAssessment matches FraudRule | M : N through RuleMatch | An assessment may match zero or many rules; a rule may match zero or many assessments. RuleMatch is mandatory for each recorded match. |
| RiskAssessment creates FraudAlert | RiskAssessment 1 : 0..1 FraudAlert | Every alert belongs to one assessment; only scores of at least 30 create an alert. |
| Analyst is assigned FraudAlert | Analyst 1 : N FraudAlert | Assignment is optional when an alert is first created; an analyst can own many alerts. |
| FraudAlert has AlertReview | FraudAlert 1 : N AlertReview | An alert may have no history when still open; every review records one alert and one analyst. |
| Analyst performs AlertReview | Analyst 1 : N AlertReview | Every review must identify the analyst who made the status change. |

### Key modelling decisions and assumptions

1. `RiskAssessment` is separate from `FraudAlert`. A no-rule/low-risk transaction still needs a recorded evaluation so the system is auditable. An alert is a workflow object, not the assessment itself.
2. `RuleMatch` resolves the genuine many-to-many relationship between assessments and rules. It also preserves the observed value and human-readable explanation that made a flag understandable.
3. `Device` is separate from `Card`: the same device may be used with many cards, and "new device" means no *earlier* transaction for that specific card/device pair.
4. Location belongs to each transaction, not to the merchant, because the supplied simulated location describes the attempt context.
5. An `Analyst` entity has been added to the initial architecture. It is necessary for the promised analyst-workload report and creates an accountable review history.
6. No full PAN or CVV is stored. `card_token` is a safe fictional identifier.
7. Timestamps use `timestamptz`; PostgreSQL stores an absolute time, while the UI can display Australia/Sydney. Historical comparisons use `(occurred_at, transaction_id)` so equal timestamps have a deterministic order.

## Logical design - relational model

Notation: **PK** = primary key; FK = foreign key; UQ = candidate/unique key.

```text
CUSTOMER(
  **customer_id**, full_name, email UQ, created_at
)

ACCOUNT(
  **account_id**, customer_id FK -> CUSTOMER.customer_id,
  account_number UQ, opened_at, status
)

CARD(
  **card_id**, account_id FK -> ACCOUNT.account_id,
  card_token UQ, status, issued_at
)

MERCHANT(
  **merchant_id**, merchant_name, category, merchant_ref UQ
)

DEVICE(
  **device_id**, device_fingerprint UQ, first_seen_at
)

LOCATION(
  **location_id**, city, country_code, UQ(city, country_code)
)

ANALYST(
  **analyst_id**, display_name, email UQ, active
)

FRAUD_RULE(
  **rule_id**, rule_code UQ, title, description, severity_weight, active,
  rule_set_version, minimum_amount, minimum_history_count,
  historical_multiplier, event_window_seconds, minimum_event_count
)

PAYMENT_TRANSACTION(
  **transaction_id**, request_id UQ,
  card_id FK -> CARD.card_id, merchant_id FK -> MERCHANT.merchant_id,
  device_id FK -> DEVICE.device_id, location_id FK -> LOCATION.location_id,
  amount, occurred_at, auth_outcome, recorded_at
)

RISK_ASSESSMENT(
  **assessment_id**, transaction_id FK UQ -> PAYMENT_TRANSACTION.transaction_id,
  score, risk_level, assessed_at, rule_set_version
)

RULE_MATCH(
  **assessment_id** FK -> RISK_ASSESSMENT.assessment_id,
  **rule_id** FK -> FRAUD_RULE.rule_id,
  observed_value, explanation
)

FRAUD_ALERT(
  **alert_id**, assessment_id FK UQ -> RISK_ASSESSMENT.assessment_id,
  assigned_analyst_id FK -> ANALYST.analyst_id,
  status, created_at, reviewed_at, analyst_note
)

ALERT_REVIEW(
  **review_id**, alert_id FK -> FRAUD_ALERT.alert_id,
  changed_by_analyst_id FK -> ANALYST.analyst_id,
  changed_at, old_status, new_status, note
)
```

`RULE_MATCH` has the composite primary key `(assessment_id, rule_id)`. The unique foreign key `RISK_ASSESSMENT.transaction_id` implements the required 1:1 assessment relationship. The unique foreign key `FRAUD_ALERT.assessment_id` implements the 0..1 alert relationship.

## Functional dependencies and normalisation

### Main functional dependencies

| Relation | Functional dependencies |
|---|---|
| CUSTOMER | customer_id -> full_name, email, created_at; email -> customer_id |
| ACCOUNT | account_id -> customer_id, account_number, opened_at, status; account_number -> account_id |
| CARD | card_id -> account_id, card_token, status, issued_at; card_token -> card_id |
| MERCHANT | merchant_id -> merchant_name, category, merchant_ref; merchant_ref -> merchant_id |
| DEVICE | device_id -> device_fingerprint, first_seen_at; device_fingerprint -> device_id |
| LOCATION | location_id -> city, country_code; (city, country_code) -> location_id |
| FRAUD_RULE | rule_id -> rule_code and all rule attributes; rule_code -> rule_id and all rule attributes |
| PAYMENT_TRANSACTION | transaction_id -> request_id, card_id, merchant_id, device_id, location_id, amount, occurred_at, auth_outcome, recorded_at; request_id -> transaction_id |
| RISK_ASSESSMENT | assessment_id -> transaction_id, score, risk_level, assessed_at, rule_set_version; transaction_id -> assessment_id and the remaining attributes |
| RULE_MATCH | (assessment_id, rule_id) -> observed_value, explanation |
| FRAUD_ALERT | alert_id -> assessment_id, assigned_analyst_id, status, created_at, reviewed_at, analyst_note; assessment_id -> alert_id and the remaining attributes |
| ALERT_REVIEW | review_id -> alert_id, changed_by_analyst_id, changed_at, old_status, new_status, note |

### 1NF

Every relation has atomic scalar attributes and a declared key. Lists of triggered rules are not stored in a transaction column; each match is one `RULE_MATCH` row. Similarly, alert-history entries are separate `ALERT_REVIEW` rows rather than a repeated note field.

### 2NF

All non-key attributes are fully dependent on their whole key. The only essential composite key is `RULE_MATCH(assessment_id, rule_id)`, and both `observed_value` and `explanation` describe that exact assessment-rule pair. A rule's title/weight belongs in `FRAUD_RULE`, not in `RULE_MATCH`; assessment score belongs in `RISK_ASSESSMENT`, not in `RULE_MATCH`.

### 3NF

Transitive facts are held once in their owning relation: `merchant_name` and `category` depend on `merchant_id`, not on transaction ID; customer details depend on `customer_id`, reached through Account and Card; rule properties depend on `rule_id`. Therefore `PAYMENT_TRANSACTION` stores only foreign keys, avoiding update anomalies and inconsistent copies.

`RISK_ASSESSMENT.score` and `risk_level` are intentionally stored derived snapshots. They are the result of the rules effective at intake time, and the accompanying `rule_set_version` plus `RULE_MATCH` evidence preserves historical explainability if rule configuration changes later. This is a justified, controlled denormalisation rather than accidental duplication.

## Integrity controls

| Category | Implementation |
|---|---|
| Entity integrity | Primary keys on every relation. |
| Candidate-key integrity | Unique email, account number, card token, merchant reference, device fingerprint, `(city, country_code)`, rule code, request ID, and the two 1:1 foreign keys. |
| Referential integrity | Non-null transaction foreign keys; `ON DELETE RESTRICT` protects historical core data. |
| Domain integrity | `amount > 0`, non-negative score, positive rule thresholds/weights, two-letter country codes, and enumerated status/risk/outcome values. |
| Lifecycle integrity | A RESOLVED/DISMISSED alert requires `reviewed_at` and a nonblank analyst note. A terminal history transition also needs a note. |
| Idempotency | Unique `request_id` prevents a retry from creating duplicate payment attempts. The service should return the already-created result for a known request ID. |
| Cross-row integrity | Service transaction logic checks the sum of rule weights, rule applicability, allowed alert transitions, and history-dependent rules; a simple `CHECK` cannot aggregate other rows. |

## Index rationale

`ix_transaction_card_time(card_id, occurred_at, transaction_id)` supports prior-history and velocity lookups, including deterministic event-time tie-breaking. `ix_transaction_device_time` supports new-device investigations. `ix_transaction_merchant_time` supports merchant pattern reports. The partial `ix_alert_open_queue` keeps the current analyst queue fast without indexing finalised alerts unnecessarily. `ix_rule_match_rule` supports grouped rule-frequency reporting. These indexes are evidence-based for the planned queries; no speculative indexes have been added.

## Transaction and concurrency handoff

The Spring service should make the intake workflow one database transaction:

```text
BEGIN
  validate request ID, referenced rows, and card/account status
  lock the Card row: SELECT ... FOR UPDATE
  insert PAYMENT_TRANSACTION
  read only earlier card history and evaluate active rules
  insert RISK_ASSESSMENT and one RULE_MATCH row per hit
  insert FRAUD_ALERT when score >= 30
COMMIT
```

Any failure must roll back the transaction row, assessment, matches, and alert together. Locking the card before reading history serialises two simultaneous submissions for the same card, preventing both from missing the velocity threshold. The trade-off is brief same-card queuing; transactions for different cards remain concurrent.

For alert updates, the service should permit only `OPEN -> INVESTIGATING`, `OPEN -> DISMISSED`, `INVESTIGATING -> RESOLVED`, and `INVESTIGATING -> DISMISSED`; insert the `ALERT_REVIEW` row and update `FRAUD_ALERT` atomically.

## Validation checklist

Run these after loading the seed profile and capture the results for the report:

1. Normal transaction: a LOW assessment with no alert.
2. R1 only: a AUD 5,000 transaction produces MEDIUM and an alert.
3. R1 + R4: the seeded AUD 6,200 new-device purchase produces HIGH and an alert.
4. Velocity boundary: four attempts do not match R3; the fifth does.
5. Insufficient history: a large amount with fewer than five earlier approved transactions does not match R2.
6. Duplicate request ID, zero/negative amount, and invalid foreign key all fail.
7. A terminal alert status without a note fails the DDL constraint.
8. Force an alert insert failure inside intake and confirm no transaction/assessment/match remains.
9. Submit two same-card transactions concurrently and confirm the service's card lock makes the velocity result deterministic.

