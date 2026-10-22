package com.frauddetection.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record DashboardResponse(
        long transactionCount,
        long declinedCount,
        BigDecimal totalAmount,
        Map<String, Long> riskLevelCounts,
        Map<String, Long> alertStatusCounts,
        List<RulePerformance> rulePerformance,
        List<AnalystWorkload> analystWorkload,
        List<MerchantRisk> highRiskMerchants
) {
    public record RulePerformance(
            String ruleCode,
            String title,
            int severityWeight,
            long matchCount,
            long linkedAlertCount
    ) {}

    public record AnalystWorkload(
            Long analystId,
            String displayName,
            long activeAlerts,
            long resolvedAlerts,
            long dismissedAlerts
    ) {}

    public record MerchantRisk(
            Long merchantId,
            String merchantName,
            String category,
            long highRiskCount,
            BigDecimal highRiskAmount
    ) {}
}
