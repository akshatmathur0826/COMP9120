package com.frauddetection.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record TransactionResponse(
        Long transactionId,
        Long cardId,
        String merchantName,
        BigDecimal amount,
        OffsetDateTime occurredAt,
        String authOutcome,
        Integer score,
        String riskLevel
) {}
