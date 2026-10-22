package com.frauddetection.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record AlertResponse(
        Long alertId,
        Long transactionId,
        String customerName,
        String merchantName,
        BigDecimal amount,
        int score,
        String riskLevel,
        String status,
        String assignedAnalyst,
        OffsetDateTime createdAt
) {}
