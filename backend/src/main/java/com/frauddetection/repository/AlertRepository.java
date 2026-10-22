package com.frauddetection.repository;

import com.frauddetection.dto.AlertResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class AlertRepository {

    private final JdbcTemplate jdbc;

    public AlertRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<AlertResponse> findRecent() {
        String sql = """
                SELECT
                    fa.alert_id,
                    t.transaction_id,
                    cu.full_name AS customer_name,
                    m.merchant_name,
                    t.amount,
                    ra.score,
                    ra.risk_level,
                    fa.status,
                    an.display_name AS assigned_analyst,
                    fa.created_at
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
                ORDER BY fa.created_at DESC, fa.alert_id DESC
                LIMIT 100
                """;

        return jdbc.query(sql, (rs, rowNum) ->
                new AlertResponse(
                        rs.getLong("alert_id"),
                        rs.getLong("transaction_id"),
                        rs.getString("customer_name"),
                        rs.getString("merchant_name"),
                        rs.getBigDecimal("amount"),
                        rs.getInt("score"),
                        rs.getString("risk_level"),
                        rs.getString("status"),
                        rs.getString("assigned_analyst"),
                        rs.getObject("created_at", OffsetDateTime.class)
                )
        );
    }
}
