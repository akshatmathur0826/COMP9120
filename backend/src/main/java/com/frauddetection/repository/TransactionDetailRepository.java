package com.frauddetection.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
public class TransactionDetailRepository {

    private final JdbcTemplate jdbc;

    public TransactionDetailRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record TransactionHeader(
            Long transactionId,
            String requestId,
            Long cardId,
            String cardToken,
            String accountNumber,
            String customerName,
            String merchantName,
            String merchantCategory,
            String deviceFingerprint,
            String city,
            String countryCode,
            BigDecimal amount,
            OffsetDateTime occurredAt,
            String authOutcome,
            Long assessmentId,
            Integer score,
            String riskLevel,
            String ruleSetVersion,
            Long alertId,
            String alertStatus
    ) {}

    public Optional<TransactionHeader> findHeader(Long transactionId) {
        String sql = """
                SELECT
                    t.transaction_id,
                    t.request_id,
                    c.card_id,
                    c.card_token,
                    a.account_number,
                    cu.full_name AS customer_name,
                    m.merchant_name,
                    m.category AS merchant_category,
                    d.device_fingerprint,
                    l.city,
                    l.country_code,
                    t.amount,
                    t.occurred_at,
                    t.auth_outcome,
                    ra.assessment_id,
                    ra.score,
                    ra.risk_level,
                    ra.rule_set_version,
                    fa.alert_id,
                    fa.status AS alert_status
                FROM fraud_detection.payment_transaction t
                JOIN fraud_detection.card c
                    ON c.card_id = t.card_id
                JOIN fraud_detection.account a
                    ON a.account_id = c.account_id
                JOIN fraud_detection.customer cu
                    ON cu.customer_id = a.customer_id
                JOIN fraud_detection.merchant m
                    ON m.merchant_id = t.merchant_id
                JOIN fraud_detection.device d
                    ON d.device_id = t.device_id
                JOIN fraud_detection.location l
                    ON l.location_id = t.location_id
                LEFT JOIN fraud_detection.risk_assessment ra
                    ON ra.transaction_id = t.transaction_id
                LEFT JOIN fraud_detection.fraud_alert fa
                    ON fa.assessment_id = ra.assessment_id
                WHERE t.transaction_id = ?
                """;

        return jdbc.query(sql, (rs, rowNum) ->
                new TransactionHeader(
                        rs.getLong("transaction_id"),
                        rs.getString("request_id"),
                        rs.getLong("card_id"),
                        rs.getString("card_token"),
                        rs.getString("account_number"),
                        rs.getString("customer_name"),
                        rs.getString("merchant_name"),
                        rs.getString("merchant_category"),
                        rs.getString("device_fingerprint"),
                        rs.getString("city"),
                        rs.getString("country_code"),
                        rs.getBigDecimal("amount"),
                        rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getString("auth_outcome"),
                        rs.getObject("assessment_id", Long.class),
                        rs.getObject("score", Integer.class),
                        rs.getString("risk_level"),
                        rs.getString("rule_set_version"),
                        rs.getObject("alert_id", Long.class),
                        rs.getString("alert_status")
                ), transactionId).stream().findFirst();
    }
}
