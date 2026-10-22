package com.frauddetection.dto;

import java.time.OffsetDateTime;

public record AlertReviewResponse(
        Long reviewId,
        String changedBy,
        OffsetDateTime changedAt,
        String oldStatus,
        String newStatus,
        String note
) {}
