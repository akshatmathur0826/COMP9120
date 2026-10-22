package com.frauddetection.dto;

import java.util.List;

public record ReferenceDataResponse(
        List<CardOption> cards,
        List<MerchantOption> merchants,
        List<DeviceOption> devices,
        List<LocationOption> locations,
        List<AnalystOption> analysts,
        List<RuleOption> rules
) {
    public record CardOption(
            Long cardId,
            String cardToken,
            String cardStatus,
            String accountNumber,
            String accountStatus,
            String customerName,
            List<Long> knownDeviceIds
    ) {}

    public record MerchantOption(
            Long merchantId,
            String merchantName,
            String category
    ) {}

    public record DeviceOption(
            Long deviceId,
            String deviceFingerprint
    ) {}

    public record LocationOption(
            Long locationId,
            String city,
            String countryCode
    ) {}

    public record AnalystOption(
            Long analystId,
            String displayName
    ) {}

    public record RuleOption(
            Long ruleId,
            String ruleCode,
            String title,
            String description,
            int severityWeight,
            boolean active,
            String ruleSetVersion
    ) {}
}
