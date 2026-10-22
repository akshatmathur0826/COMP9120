package com.frauddetection.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record AlertDetailResponse(
        Long alertId,
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
        String analystNote,
        List<MatchedRule> matchedRules
) {
    public record MatchedRule(
            String ruleCode,
            String title,
            BigDecimal observedValue,
            String explanation
    ) {}
}
