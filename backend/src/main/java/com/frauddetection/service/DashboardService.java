package com.frauddetection.service;

import com.frauddetection.dto.DashboardResponse;
import com.frauddetection.repository.DashboardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {

    private final DashboardRepository repository;

    public DashboardService(DashboardRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        var totals = repository.findTransactionTotals();

        return new DashboardResponse(
                totals.transactionCount(),
                totals.declinedCount(),
                totals.totalAmount(),
                repository.countByRiskLevel(),
                repository.countByAlertStatus(),
                repository.findRulePerformance(),
                repository.findAnalystWorkload(),
                repository.findHighRiskMerchants()
        );
    }
}
