package com.frauddetection.repository;

import com.frauddetection.dto.ReferenceDataResponse.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.util.Arrays;
import java.util.List;

@Repository
public class ReferenceDataRepository {

    private final JdbcTemplate jdbc;

    public ReferenceDataRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // known_device_ids lets the simulator flag a card/device pair as "new" (rule R4).
    public List<CardOption> findCards() {
        return jdbc.query(
                """
                SELECT c.card_id, c.card_token, c.status AS card_status,
                       a.account_number, a.status AS account_status,
                       cu.full_name AS customer_name,
                       COALESCE(
                           ARRAY_AGG(DISTINCT t.device_id ORDER BY t.device_id)
                               FILTER (WHERE t.device_id IS NOT NULL),
                           '{}'::BIGINT[]
                       ) AS known_device_ids
                FROM fraud_detection.card c
                JOIN fraud_detection.account a
                    ON a.account_id = c.account_id
                JOIN fraud_detection.customer cu
                    ON cu.customer_id = a.customer_id
                LEFT JOIN fraud_detection.payment_transaction t
                    ON t.card_id = c.card_id
                GROUP BY c.card_id, c.card_token, c.status,
                         a.account_number, a.status, cu.full_name
                ORDER BY c.card_id
                """,
                (rs, rowNum) -> new CardOption(
                        rs.getLong("card_id"),
                        rs.getString("card_token"),
                        rs.getString("card_status"),
                        rs.getString("account_number"),
                        rs.getString("account_status"),
                        rs.getString("customer_name"),
                        toLongList(rs.getArray("known_device_ids"))
                )
        );
    }

    public List<MerchantOption> findMerchants() {
        return jdbc.query(
                """
                SELECT merchant_id, merchant_name, category
                FROM fraud_detection.merchant
                ORDER BY merchant_name
                """,
                (rs, rowNum) -> new MerchantOption(
                        rs.getLong("merchant_id"),
                        rs.getString("merchant_name"),
                        rs.getString("category")
                )
        );
    }

    public List<DeviceOption> findDevices() {
        return jdbc.query(
                """
                SELECT device_id, device_fingerprint
                FROM fraud_detection.device
                ORDER BY device_id
                """,
                (rs, rowNum) -> new DeviceOption(
                        rs.getLong("device_id"),
                        rs.getString("device_fingerprint")
                )
        );
    }

    public List<LocationOption> findLocations() {
        return jdbc.query(
                """
                SELECT location_id, city, country_code
                FROM fraud_detection.location
                ORDER BY country_code, city
                """,
                (rs, rowNum) -> new LocationOption(
                        rs.getLong("location_id"),
                        rs.getString("city"),
                        rs.getString("country_code")
                )
        );
    }

    public List<AnalystOption> findActiveAnalysts() {
        return jdbc.query(
                """
                SELECT analyst_id, display_name
                FROM fraud_detection.analyst
                WHERE active = TRUE
                ORDER BY display_name
                """,
                (rs, rowNum) -> new AnalystOption(
                        rs.getLong("analyst_id"),
                        rs.getString("display_name")
                )
        );
    }

    public List<RuleOption> findRules() {
        return jdbc.query(
                """
                SELECT rule_id, rule_code, title, description,
                       severity_weight, active, rule_set_version
                FROM fraud_detection.fraud_rule
                ORDER BY rule_id
                """,
                (rs, rowNum) -> new RuleOption(
                        rs.getLong("rule_id"),
                        rs.getString("rule_code"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getInt("severity_weight"),
                        rs.getBoolean("active"),
                        rs.getString("rule_set_version")
                )
        );
    }

    private static List<Long> toLongList(Array array) throws java.sql.SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values)
                .map(value -> ((Number) value).longValue())
                .toList();
    }
}
