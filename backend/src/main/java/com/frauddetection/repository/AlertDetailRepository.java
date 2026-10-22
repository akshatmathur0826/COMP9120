package com.frauddetection.repository;

import com.frauddetection.dto.AlertDetailResponse;
import com.frauddetection.dto.AlertDetailResponse.MatchedRule;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AlertDetailRepository {

    private final JdbcTemplate jdbc;

    public AlertDetailRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record AlertHeader(
            Long alertId,
            Long assessmentId,
            Long transactionId,
            String customerName,
            String merchantName,
            BigDecimal amount,
            OffsetDateTime occurredAt,
            String authOutcome,
            int score,
            String riskLevel,
            String ruleSetVersion,
            String status,
            String assignedAnalyst,
            OffsetDateTime createdAt,
            OffsetDateTime reviewedAt,
            String analystNote
    ) {}

    public Optional<AlertHeader> findHeader(Long alertId) {
        String sql = """
                SELECT
                    fa.alert_id,
                    ra.assessment_id,
                    t.transaction_id,
                    cu.full_name AS customer_name,
                    m.merchant_name,
                    t.amount,
                    t.occurred_at,
                    t.auth_outcome,
                    ra.score,
                    ra.risk_level,
                    ra.rule_set_version,
                    fa.status,
                    an.display_name AS assigned_analyst,
                    fa.created_at,
                    fa.reviewed_at,
                    fa.analyst_note
                FROM fraud_detection.fraud_alert fa
                JOIN fraud_detection.risk_assessment ra
                    ON ra.assessment_id = fa.assessment_id
                JOIN fraud_detection.payment_transaction t
                    ON t.transaction_id = ra.transaction_id
                JOIN fraud_detection.card c
                    ON c.card_id = t.card_id
                JOIN fraud_detection.account a
                    ON a.account_id = c.account_id
                JOIN fraud_detection.customer cu
                    ON cu.customer_id = a.customer_id
                JOIN fraud_detection.merchant m
                    ON m.merchant_id = t.merchant_id
                LEFT JOIN fraud_detection.analyst an
                    ON an.analyst_id = fa.assigned_analyst_id
                WHERE fa.alert_id = ?
                """;

        return jdbc.query(sql, (rs, rowNum) ->
                new AlertHeader(
                        rs.getLong("alert_id"),
                        rs.getLong("assessment_id"),
                        rs.getLong("transaction_id"),
                        rs.getString("customer_name"),
                        rs.getString("merchant_name"),
                        rs.getBigDecimal("amount"),
                        rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getString("auth_outcome"),
                        rs.getInt("score"),
                        rs.getString("risk_level"),
                        rs.getString("rule_set_version"),
                        rs.getString("status"),
                        rs.getString("assigned_analyst"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("reviewed_at", OffsetDateTime.class),
                        rs.getString("analyst_note")
                ), alertId).stream().findFirst();
    }

    public List<MatchedRule> findMatches(Long assessmentId) {
        return jdbc.query(
                """
                SELECT fr.rule_code, fr.title,
                       rm.observed_value, rm.explanation
                FROM fraud_detection.rule_match rm
                JOIN fraud_detection.fraud_rule fr
                    ON fr.rule_id = rm.rule_id
                WHERE rm.assessment_id = ?
                ORDER BY fr.rule_id
                """,
                (rs, rowNum) -> new MatchedRule(
                        rs.getString("rule_code"),
                        rs.getString("title"),
                        rs.getBigDecimal("observed_value"),
                        rs.getString("explanation")
                ),
                assessmentId
        );
    }
}
