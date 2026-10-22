package com.frauddetection.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record CreateTransactionRequest(

        @NotBlank(message = "requestId is required")
        @Size(max = 80, message = "requestId must not exceed 80 characters")
        String requestId,

        @NotNull(message = "cardId is required")
        @Positive(message = "cardId must be positive")
        Long cardId,

        @NotNull(message = "merchantId is required")
        @Positive(message = "merchantId must be positive")
        Long merchantId,

        @NotNull(message = "deviceId is required")
        @Positive(message = "deviceId must be positive")
        Long deviceId,

        @NotNull(message = "locationId is required")
        @Positive(message = "locationId must be positive")
        Long locationId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.00", inclusive = false,
                message = "amount must be greater than zero")
        @Digits(integer = 10, fraction = 2,
                message = "amount allows 10 integer digits and 2 decimal places")
        BigDecimal amount,

        @NotNull(message = "occurredAt is required")
        @PastOrPresent(message = "occurredAt must not be in the future")
        OffsetDateTime occurredAt,

        @NotNull(message = "authOutcome is required")
        AuthOutcome authOutcome
) {}
