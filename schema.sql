-- Fraud Detection Database - PostgreSQL schema
-- Run this file once against an empty PostgreSQL database, then run seed.sql.
-- The application should use the fraud_detection schema (or set search_path accordingly).

CREATE SCHEMA IF NOT EXISTS fraud_detection;
SET search_path TO fraud_detection;

CREATE TABLE customer (
    customer_id      BIGSERIAL PRIMARY KEY,
    full_name        VARCHAR(120) NOT NULL,
    email            VARCHAR(254) NOT NULL UNIQUE,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_customer_email_nonblank CHECK (length(btrim(email)) > 3)
);

CREATE TABLE account (
    account_id       BIGSERIAL PRIMARY KEY,
    customer_id      BIGINT NOT NULL REFERENCES customer(customer_id) ON DELETE RESTRICT,
    account_number   VARCHAR(32) NOT NULL UNIQUE,
    opened_at        TIMESTAMPTZ NOT NULL,
    status           VARCHAR(16) NOT NULL,
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_account_number_nonblank CHECK (length(btrim(account_number)) > 0)
);

CREATE TABLE card (
    card_id          BIGSERIAL PRIMARY KEY,
    account_id       BIGINT NOT NULL REFERENCES account(account_id) ON DELETE RESTRICT,
    card_token       VARCHAR(80) NOT NULL UNIQUE,
    status           VARCHAR(16) NOT NULL,
    issued_at        TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_card_status CHECK (status IN ('ACTIVE', 'BLOCKED', 'EXPIRED', 'REPLACED')),
    CONSTRAINT ck_card_token_nonblank CHECK (length(btrim(card_token)) > 0)
    -- Deliberately do not store a card PAN or CVV in this prototype.
);

CREATE TABLE merchant (
    merchant_id      BIGSERIAL PRIMARY KEY,
    merchant_name    VARCHAR(160) NOT NULL,
    category         VARCHAR(80) NOT NULL,
    merchant_ref     VARCHAR(64) NOT NULL UNIQUE,
    CONSTRAINT ck_merchant_name_nonblank CHECK (length(btrim(merchant_name)) > 0)
);

CREATE TABLE device (
    device_id          BIGSERIAL PRIMARY KEY,
    device_fingerprint VARCHAR(128) NOT NULL UNIQUE,
    first_seen_at      TIMESTAMPTZ NOT NULL,
    CONSTRAINT ck_device_fingerprint_nonblank CHECK (length(btrim(device_fingerprint)) > 0)
);

CREATE TABLE location (
    location_id       BIGSERIAL PRIMARY KEY,
    city              VARCHAR(100) NOT NULL,
    country_code      CHAR(2) NOT NULL,
    CONSTRAINT uq_location_city_country UNIQUE (city, country_code),
    CONSTRAINT ck_location_country_code CHECK (country_code ~ '^[A-Z]{2}$')
);

CREATE TABLE analyst (
    analyst_id        BIGSERIAL PRIMARY KEY,
    display_name      VARCHAR(120) NOT NULL,
    email             VARCHAR(254) NOT NULL UNIQUE,
    active            BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT ck_analyst_email_nonblank CHECK (length(btrim(email)) > 3)
);

CREATE TABLE fraud_rule (
    rule_id                BIGSERIAL PRIMARY KEY,
    rule_code              VARCHAR(20) NOT NULL UNIQUE,
    title                  VARCHAR(160) NOT NULL,
    description            TEXT NOT NULL,
    severity_weight        INTEGER NOT NULL,
    active                 BOOLEAN NOT NULL DEFAULT TRUE,
    rule_set_version       VARCHAR(32) NOT NULL,
    minimum_amount         NUMERIC(12,2),
    minimum_history_count  INTEGER,
    historical_multiplier  NUMERIC(8,2),
    event_window_seconds   INTEGER,
    minimum_event_count    INTEGER,
    CONSTRAINT ck_rule_code CHECK (rule_code ~ '^R[0-9]+$'),
    CONSTRAINT ck_rule_weight CHECK (severity_weight > 0),
    CONSTRAINT ck_rule_min_amount CHECK (minimum_amount IS NULL OR minimum_amount > 0),
    CONSTRAINT ck_rule_min_history CHECK (minimum_history_count IS NULL OR minimum_history_count > 0),
    CONSTRAINT ck_rule_multiplier CHECK (historical_multiplier IS NULL OR historical_multiplier > 0),
    CONSTRAINT ck_rule_window CHECK (event_window_seconds IS NULL OR event_window_seconds > 0),
    CONSTRAINT ck_rule_min_events CHECK (minimum_event_count IS NULL OR minimum_event_count > 0)
);

CREATE TABLE payment_transaction (
    transaction_id    BIGSERIAL PRIMARY KEY,
    request_id        VARCHAR(80) NOT NULL UNIQUE,
    card_id           BIGINT NOT NULL REFERENCES card(card_id) ON DELETE RESTRICT,
    merchant_id       BIGINT NOT NULL REFERENCES merchant(merchant_id) ON DELETE RESTRICT,
    device_id         BIGINT NOT NULL REFERENCES device(device_id) ON DELETE RESTRICT,
    location_id       BIGINT NOT NULL REFERENCES location(location_id) ON DELETE RESTRICT,
    amount            NUMERIC(12,2) NOT NULL,
    occurred_at       TIMESTAMPTZ NOT NULL,
    auth_outcome      VARCHAR(12) NOT NULL,
    recorded_at       TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_transaction_amount CHECK (amount > 0),
    CONSTRAINT ck_transaction_auth_outcome CHECK (auth_outcome IN ('APPROVED', 'DECLINED')),
    CONSTRAINT ck_transaction_request_id_nonblank CHECK (length(btrim(request_id)) > 0)
);

CREATE TABLE risk_assessment (
    assessment_id      BIGSERIAL PRIMARY KEY,
    transaction_id     BIGINT NOT NULL UNIQUE REFERENCES payment_transaction(transaction_id) ON DELETE RESTRICT,
    score               INTEGER NOT NULL,
    risk_level          VARCHAR(8) NOT NULL,
    assessed_at         TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    rule_set_version    VARCHAR(32) NOT NULL,
    CONSTRAINT ck_assessment_score CHECK (score >= 0),
    CONSTRAINT ck_assessment_level CHECK (
        (score BETWEEN 0 AND 29 AND risk_level = 'LOW') OR
        (score BETWEEN 30 AND 59 AND risk_level = 'MEDIUM') OR
        (score >= 60 AND risk_level = 'HIGH')
    )
);

CREATE TABLE rule_match (
    assessment_id      BIGINT NOT NULL REFERENCES risk_assessment(assessment_id) ON DELETE RESTRICT,
    rule_id            BIGINT NOT NULL REFERENCES fraud_rule(rule_id) ON DELETE RESTRICT,
    observed_value     NUMERIC(18,4),
    explanation        TEXT NOT NULL,
    PRIMARY KEY (assessment_id, rule_id),
    CONSTRAINT ck_rule_match_explanation_nonblank CHECK (length(btrim(explanation)) > 0)
);

CREATE TABLE fraud_alert (
    alert_id                 BIGSERIAL PRIMARY KEY,
    assessment_id            BIGINT NOT NULL UNIQUE REFERENCES risk_assessment(assessment_id) ON DELETE RESTRICT,
    assigned_analyst_id      BIGINT REFERENCES analyst(analyst_id) ON DELETE RESTRICT,
    status                   VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    created_at               TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at              TIMESTAMPTZ,
    analyst_note             TEXT,
    CONSTRAINT ck_alert_status CHECK (status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'DISMISSED')),
    CONSTRAINT ck_alert_closure_evidence CHECK (
        (status IN ('OPEN', 'INVESTIGATING') AND reviewed_at IS NULL) OR
        (status IN ('RESOLVED', 'DISMISSED') AND reviewed_at IS NOT NULL
         AND analyst_note IS NOT NULL AND length(btrim(analyst_note)) > 0)
    )
);

CREATE TABLE alert_review (
    review_id              BIGSERIAL PRIMARY KEY,
    alert_id               BIGINT NOT NULL REFERENCES fraud_alert(alert_id) ON DELETE RESTRICT,
    changed_by_analyst_id  BIGINT NOT NULL REFERENCES analyst(analyst_id) ON DELETE RESTRICT,
    changed_at             TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    old_status             VARCHAR(16) NOT NULL,
    new_status             VARCHAR(16) NOT NULL,
    note                   TEXT,
    CONSTRAINT ck_review_statuses CHECK (
        old_status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'DISMISSED') AND
        new_status IN ('OPEN', 'INVESTIGATING', 'RESOLVED', 'DISMISSED') AND
        old_status <> new_status
    ),
    CONSTRAINT ck_review_terminal_note CHECK (
        new_status NOT IN ('RESOLVED', 'DISMISSED') OR
        (note IS NOT NULL AND length(btrim(note)) > 0)
    )
);

-- History/rule-evaluation indexes. The primary keys and unique constraints already
-- create indexes for identifiers such as request_id, card_token and assessment_id.
CREATE INDEX ix_transaction_card_time
    ON payment_transaction (card_id, occurred_at, transaction_id);
CREATE INDEX ix_transaction_device_time
    ON payment_transaction (device_id, occurred_at, transaction_id);
CREATE INDEX ix_transaction_merchant_time
    ON payment_transaction (merchant_id, occurred_at DESC);
CREATE INDEX ix_alert_open_queue
    ON fraud_alert (status, created_at DESC)
    WHERE status IN ('OPEN', 'INVESTIGATING');
CREATE INDEX ix_rule_match_rule
    ON rule_match (rule_id);
CREATE INDEX ix_alert_review_alert_time
    ON alert_review (alert_id, changed_at DESC);

COMMENT ON TABLE risk_assessment IS
    'Immutable evaluation snapshot: every transaction receives one assessment, whether or not it generates an alert.';
COMMENT ON TABLE rule_match IS
    'Associative relation recording each distinct rule matched by an assessment and its explainable evidence.';
COMMENT ON COLUMN fraud_rule.rule_set_version IS
    'Version label frozen into risk_assessment so past decisions remain explainable after future threshold changes.';
COMMENT ON COLUMN payment_transaction.request_id IS
    'Client-supplied idempotency key. A duplicate must be rejected or return the original result, never create another transaction.';

