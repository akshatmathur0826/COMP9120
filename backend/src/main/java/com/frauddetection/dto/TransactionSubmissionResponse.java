package com.frauddetection.dto;

import java.util.List;

public record TransactionSubmissionResponse(
        Long transactionId,
        Long assessmentId,
        int score,
        String riskLevel,
        List<String> matchedRules,
        Long alertId
) {}
