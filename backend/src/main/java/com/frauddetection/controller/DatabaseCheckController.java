package com.frauddetection.controller;

import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class DatabaseCheckController {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseCheckController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/db-check")
    public Map<String, Object> checkDatabase() {
        Long customerCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fraud_detection.customer",
                Long.class
        );

        Long transactionCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM fraud_detection.payment_transaction",
                Long.class
        );

        return Map.of(
                "status", "connected",
                "customers", customerCount,
                "transactions", transactionCount
        );
    }
}
