package com.frauddetection.repository;

import com.frauddetection.dto.CreateTransactionRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class TransactionProcessingRepository {

    private final JdbcTemplate jdbc;

    public TransactionProcessingRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record CardState(
            Long cardId,
            String cardStatus,
            String accountStatus
    ) {}

    public record RuleConfig(
            Long ruleId,
            String ruleCode,
            int weight,
            String version,
            BigDecimal minimumAmount,
            Integer minimumHistoryCount,
            BigDecimal historicalMultiplier,
            Integer windowSeconds,
            Integer minimumEventCount
    ) {}

    public record HistorySummary(
            long count,
            BigDecimal average
    ) {}

    public Optional<CardState> lockCard(Long cardId) {
        String sql = """
                SELECT c.card_id, c.status AS card_status,
                       a.status AS account_status
                FROM fraud_detection.card c
                JOIN fraud_detection.account a
                    ON a.account_id = c.account_id
                WHERE c.card_id = ?
                FOR UPDATE OF c, a
                """;

        return jdbc.query(sql, (rs, rowNum) ->
                new CardState(
                        rs.getLong("card_id"),
                        rs.getString("card_status"),
                        rs.getString("account_status")
                ), cardId).stream().findFirst();
    }

    public Optional<Long> findTransactionIdByRequestId(String requestId) {
        return jdbc.query(
                """
                SELECT transaction_id
                FROM fraud_detection.payment_transaction
                WHERE request_id = ?
                """,
                (rs, rowNum) -> rs.getLong("transaction_id"),
                requestId
        ).stream().findFirst();
    }

    public List<RuleConfig> findActiveRules() {
        return jdbc.query(
                """
                SELECT *
                FROM fraud_detection.fraud_rule
                WHERE active = TRUE
                ORDER BY rule_id
                """,
                (rs, rowNum) -> new RuleConfig(
                        rs.getLong("rule_id"),
                        rs.getString("rule_code"),
                        rs.getInt("severity_weight"),
                        rs.getString("rule_set_version"),
                        rs.getBigDecimal("minimum_amount"),
                        rs.getObject("minimum_history_count", Integer.class),
                        rs.getBigDecimal("historical_multiplier"),
                        rs.getObject("event_window_seconds", Integer.class),
                        rs.getObject("minimum_event_count", Integer.class)
                )
        );
    }

    public Long insertTransaction(CreateTransactionRequest request) {
        return jdbc.queryForObject(
                """
                INSERT INTO fraud_detection.payment_transaction
                    (request_id, card_id, merchant_id, device_id,
                     location_id, amount, occurred_at, auth_outcome)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING transaction_id
                """,
                Long.class,
                request.requestId(),
                request.cardId(),
                request.merchantId(),
                request.deviceId(),
                request.locationId(),
                request.amount(),
                request.occurredAt(),
                request.authOutcome().name()
        );
    }

    public HistorySummary findApprovedHistory(
            Long cardId, OffsetDateTime time, Long transactionId) {

        return jdbc.queryForObject(
                """
                SELECT COUNT(*) AS history_count,
                       AVG(amount) AS average_amount
                FROM fraud_detection.payment_transaction
                WHERE card_id = ?
                  AND auth_outcome = 'APPROVED'
                  AND (occurred_at, transaction_id) < (?, ?)
                """,
                (rs, rowNum) -> new HistorySummary(
                        rs.getLong("history_count"),
                        rs.getBigDecimal("average_amount")
                ),
                cardId, time, transactionId
        );
    }

    public boolean hasEarlierDeviceUse(
            Long cardId, Long deviceId,
            OffsetDateTime time, Long transactionId) {

        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM fraud_detection.payment_transaction
                    WHERE card_id = ?
                      AND device_id = ?
                      AND (occurred_at, transaction_id) < (?, ?)
                )
                """,
                Boolean.class,
                cardId, deviceId, time, transactionId
        ));
    }

    public long countAttempts(
            Long cardId, OffsetDateTime time,
            Long transactionId, int windowSeconds,
            boolean declinedOnly) {

        String sql = """
                SELECT COUNT(*)
                FROM fraud_detection.payment_transaction
                WHERE card_id = ?
                  AND occurred_at >= ?
                  AND (occurred_at, transaction_id) <= (?, ?)
                """;

        if (declinedOnly) {
            sql += " AND auth_outcome = 'DECLINED'";
        }

        Long count = jdbc.queryForObject(
                sql,
                Long.class,
                cardId,
                time.minusSeconds(windowSeconds),
                time,
                transactionId
        );

        return count == null ? 0L : count;
    }

    public Long insertAssessment(
            Long transactionId, int score,
            String riskLevel, String version) {

        return jdbc.queryForObject(
                """
                INSERT INTO fraud_detection.risk_assessment
                    (transaction_id, score, risk_level, rule_set_version)
                VALUES (?, ?, ?, ?)
                RETURNING assessment_id
                """,
                Long.class,
                transactionId, score, riskLevel, version
        );
    }

    public void insertRuleMatch(
            Long assessmentId, Long ruleId,
            BigDecimal observedValue, String explanation) {

        jdbc.update(
                """
                INSERT INTO fraud_detection.rule_match
                    (assessment_id, rule_id, observed_value, explanation)
                VALUES (?, ?, ?, ?)
                """,
                assessmentId, ruleId, observedValue, explanation
        );
    }

    public Long insertAlert(Long assessmentId) {
        return jdbc.queryForObject(
                """
                INSERT INTO fraud_detection.fraud_alert
                    (assessment_id, status)
                VALUES (?, 'OPEN')
                RETURNING alert_id
                """,
                Long.class,
                assessmentId
        );
    }
}
