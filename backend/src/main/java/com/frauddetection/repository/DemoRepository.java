package com.frauddetection.repository;

import com.frauddetection.dto.DemoResponses.TableCounts;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class DemoRepository {

    private final JdbcTemplate jdbc;

    public DemoRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TableCounts counts() {
        return jdbc.queryForObject(
                """
                SELECT (SELECT COUNT(*) FROM fraud_detection.payment_transaction) AS transactions,
                       (SELECT COUNT(*) FROM fraud_detection.risk_assessment)     AS assessments,
                       (SELECT COUNT(*) FROM fraud_detection.rule_match)          AS rule_matches,
                       (SELECT COUNT(*) FROM fraud_detection.fraud_alert)         AS alerts
                """,
                (rs, rowNum) -> new TableCounts(
                        rs.getLong("transactions"),
                        rs.getLong("assessments"),
                        rs.getLong("rule_matches"),
                        rs.getLong("alerts")
                )
        );
    }

    public boolean requestExists(String requestId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM fraud_detection.payment_transaction WHERE request_id = ?)",
                Boolean.class, requestId));
    }

    // A device this card has used before, so the demo does not depend on rule R4.
    public Optional<Long> findKnownDevice(Long cardId) {
        return jdbc.query(
                """
                SELECT device_id FROM fraud_detection.payment_transaction
                WHERE card_id = ? ORDER BY occurred_at DESC, transaction_id DESC LIMIT 1
                """,
                (rs, rowNum) -> rs.getLong("device_id"), cardId).stream().findFirst();
    }

    public Long findAnyDevice() {
        return jdbc.queryForObject("SELECT MIN(device_id) FROM fraud_detection.device", Long.class);
    }

    public Long findAnyMerchant() {
        return jdbc.queryForObject("SELECT MIN(merchant_id) FROM fraud_detection.merchant", Long.class);
    }

    public Long findAnyLocation() {
        return jdbc.queryForObject("SELECT MIN(location_id) FROM fraud_detection.location", Long.class);
    }

    /**
     * Installs a trigger that makes the next fraud_alert insert fail. It must be called
     * inside the demo transaction: PostgreSQL DDL is transactional, so the function and
     * trigger disappear again when that transaction rolls back.
     */
    public void installAlertInsertFault() {
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION fraud_detection.demo_fail_alert_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$
                DECLARE
                    tx_id   BIGINT;
                    matches INTEGER;
                BEGIN
                    SELECT transaction_id INTO tx_id
                    FROM fraud_detection.risk_assessment WHERE assessment_id = NEW.assessment_id;
                    SELECT COUNT(*) INTO matches
                    FROM fraud_detection.rule_match WHERE assessment_id = NEW.assessment_id;
                    RAISE EXCEPTION 'Forced failure at step 7 (alert insert). Already written inside this transaction: transaction %, assessment %, % rule match(es).',
                        tx_id, NEW.assessment_id, matches;
                END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER demo_force_alert_failure
                BEFORE INSERT ON fraud_detection.fraud_alert
                FOR EACH ROW EXECUTE FUNCTION fraud_detection.demo_fail_alert_insert()
                """);
    }

    public void lockCard(Long cardId) {
        jdbc.query("SELECT card_id FROM fraud_detection.card WHERE card_id = ? FOR UPDATE",
                (rs, rowNum) -> rs.getLong(1), cardId);
    }

    public void sleep(long millis) {
        jdbc.query("SELECT pg_sleep(?)", (rs, rowNum) -> 0, millis / 1000.0);
    }

    public record VelocityRule(int windowSeconds, int minimumEventCount) {}

    public VelocityRule findVelocityRule() {
        return jdbc.queryForObject(
                """
                SELECT event_window_seconds, minimum_event_count
                FROM fraud_detection.fraud_rule WHERE rule_code = 'R3'
                """,
                (rs, rowNum) -> new VelocityRule(rs.getInt(1), rs.getInt(2)));
    }

    public long countRecentAttempts(Long cardId, int windowSeconds) {
        Long n = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM fraud_detection.payment_transaction
                WHERE card_id = ? AND occurred_at >= CURRENT_TIMESTAMP - make_interval(secs => ?)
                """,
                Long.class, cardId, windowSeconds);
        return n == null ? 0 : n;
    }
}
