package com.frauddetection.dto;

import jakarta.validation.constraints.*;

public record UpdateAlertStatusRequest(

        @NotBlank(message = "newStatus is required")
        @Pattern(regexp = "INVESTIGATING|RESOLVED|DISMISSED",
                message = "newStatus must be INVESTIGATING, RESOLVED or DISMISSED")
        String newStatus,

        @NotNull(message = "analystId is required")
        @Positive(message = "analystId must be positive")
        Long analystId,

        @Size(max = 2000, message = "note must not exceed 2000 characters")
        String note
) {}
