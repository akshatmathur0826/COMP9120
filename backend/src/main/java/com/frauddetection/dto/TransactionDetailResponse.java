package com.frauddetection.dto;

import com.frauddetection.dto.AlertDetailResponse.MatchedRule;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record TransactionDetailResponse(
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
        String alertStatus,
        List<MatchedRule> matchedRules
) {}
