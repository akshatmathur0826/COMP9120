package com.frauddetection.repository;

import com.frauddetection.dto.TransactionResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public class TransactionRepository {

    private final JdbcTemplate jdbcTemplate;

    public TransactionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<TransactionResponse> findRecent(int limit) {
        String sql = """
                SELECT
                    t.transaction_id,
                    t.card_id,
                    m.merchant_name,
                    t.amount,
                    t.occurred_at,
                    t.auth_outcome,
                    r.score,
                    r.risk_level
                FROM fraud_detection.payment_transaction t
                JOIN fraud_detection.merchant m
                    ON m.merchant_id = t.merchant_id
                LEFT JOIN fraud_detection.risk_assessment r
                    ON r.transaction_id = t.transaction_id
                ORDER BY t.occurred_at DESC, t.transaction_id DESC
                LIMIT ?
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) ->
                new TransactionResponse(
                        rs.getLong("transaction_id"),
                        rs.getLong("card_id"),
                        rs.getString("merchant_name"),
                        rs.getBigDecimal("amount"),
                        rs.getObject("occurred_at", OffsetDateTime.class),
                        rs.getString("auth_outcome"),
                        rs.getObject("score", Integer.class),
                        rs.getString("risk_level")
                ), limit);
    }
}
