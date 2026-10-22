package com.frauddetection.repository;

import com.frauddetection.dto.DashboardResponse.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class DashboardRepository {

    private final JdbcTemplate jdbc;

    public DashboardRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record TransactionTotals(
            long transactionCount,
            long declinedCount,
            BigDecimal totalAmount
    ) {}

    public TransactionTotals findTransactionTotals() {
        return jdbc.queryForObject(
                """
                SELECT COUNT(*) AS transaction_count,
                       COUNT(*) FILTER (WHERE auth_outcome = 'DECLINED') AS declined_count,
                       COALESCE(SUM(amount), 0) AS total_amount
                FROM fraud_detection.payment_transaction
                """,
                (rs, rowNum) -> new TransactionTotals(
                        rs.getLong("transaction_count"),
                        rs.getLong("declined_count"),
                        rs.getBigDecimal("total_amount")
                )
        );
    }

    public Map<String, Long> countByRiskLevel() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("LOW", 0L);
        counts.put("MEDIUM", 0L);
        counts.put("HIGH", 0L);

        jdbc.query(
                """
                SELECT risk_level, COUNT(*) AS level_count
                FROM fraud_detection.risk_assessment
                GROUP BY risk_level
                """,
                rs -> {
                    counts.put(rs.getString("risk_level"), rs.getLong("level_count"));
                }
        );
        return counts;
    }

    public Map<String, Long> countByAlertStatus() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("OPEN", 0L);
        counts.put("INVESTIGATING", 0L);
        counts.put("RESOLVED", 0L);
        counts.put("DISMISSED", 0L);

        jdbc.query(
                """
                SELECT status, COUNT(*) AS status_count
                FROM fraud_detection.fraud_alert
                GROUP BY status
                """,
                rs -> {
                    counts.put(rs.getString("status"), rs.getLong("status_count"));
                }
        );
        return counts;
    }

    // Query Q4 in queries.sql: left joins keep active rules with zero matches.
    public List<RulePerformance> findRulePerformance() {
        return jdbc.query(
                """
                SELECT fr.rule_code, fr.title, fr.severity_weight,
                       COUNT(rm.assessment_id) AS match_count,
                       COUNT(DISTINCT fa.alert_id) AS linked_alert_count
                FROM fraud_detection.fraud_rule fr
                LEFT JOIN fraud_detection.rule_match rm
                    ON rm.rule_id = fr.rule_id
                LEFT JOIN fraud_detection.risk_assessment ra
                    ON ra.assessment_id = rm.assessment_id
                LEFT JOIN fraud_detection.fraud_alert fa
                    ON fa.assessment_id = ra.assessment_id
                WHERE fr.active = TRUE
                GROUP BY fr.rule_id, fr.rule_code, fr.title, fr.severity_weight
                ORDER BY fr.rule_id
                """,
                (rs, rowNum) -> new RulePerformance(
                        rs.getString("rule_code"),
                        rs.getString("title"),
                        rs.getInt("severity_weight"),
                        rs.getLong("match_count"),
                        rs.getLong("linked_alert_count")
                )
        );
    }

    // Query Q7 in queries.sql.
    public List<AnalystWorkload> findAnalystWorkload() {
        return jdbc.query(
                """
                SELECT an.analyst_id, an.display_name,
                       COUNT(fa.alert_id) FILTER (WHERE fa.status IN ('OPEN', 'INVESTIGATING')) AS active_alerts,
                       COUNT(fa.alert_id) FILTER (WHERE fa.status = 'RESOLVED') AS resolved_alerts,
                       COUNT(fa.alert_id) FILTER (WHERE fa.status = 'DISMISSED') AS dismissed_alerts
                FROM fraud_detection.analyst an
                LEFT JOIN fraud_detection.fraud_alert fa
                    ON fa.assigned_analyst_id = an.analyst_id
                WHERE an.active = TRUE
                GROUP BY an.analyst_id, an.display_name
                ORDER BY active_alerts DESC, an.display_name
                """,
                (rs, rowNum) -> new AnalystWorkload(
                        rs.getLong("analyst_id"),
                        rs.getString("display_name"),
                        rs.getLong("active_alerts"),
                        rs.getLong("resolved_alerts"),
                        rs.getLong("dismissed_alerts")
                )
        );
    }

    // Query Q2 in queries.sql, without the date window.
    public List<MerchantRisk> findHighRiskMerchants() {
        return jdbc.query(
                """
                SELECT m.merchant_id, m.merchant_name, m.category,
                       COUNT(*) AS high_risk_count,
                       ROUND(SUM(t.amount), 2) AS high_risk_amount
                FROM fraud_detection.merchant m
                JOIN fraud_detection.payment_transaction t
                    ON t.merchant_id = m.merchant_id
                JOIN fraud_detection.risk_assessment ra
                    ON ra.transaction_id = t.transaction_id
                WHERE ra.risk_level = 'HIGH'
                GROUP BY m.merchant_id, m.merchant_name, m.category
                ORDER BY high_risk_count DESC, high_risk_amount DESC
                LIMIT 5
                """,
                (rs, rowNum) -> new MerchantRisk(
                        rs.getLong("merchant_id"),
                        rs.getString("merchant_name"),
                        rs.getString("category"),
                        rs.getLong("high_risk_count"),
                        rs.getBigDecimal("high_risk_amount")
                )
        );
    }
}
