package com.frauddetection.service;

import com.frauddetection.dto.AlertDetailResponse;
import com.frauddetection.repository.AlertDetailRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlertDetailService {

    private final AlertDetailRepository repository;

    public AlertDetailService(AlertDetailRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public AlertDetailResponse getAlert(Long alertId) {
        var alert = repository.findHeader(alertId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Alert not found"));

        var matches = repository.findMatches(alert.assessmentId());

        return new AlertDetailResponse(
                alert.alertId(),
                alert.transactionId(),
                alert.customerName(),
                alert.merchantName(),
                alert.amount(),
                alert.occurredAt(),
                alert.authOutcome(),
                alert.score(),
                alert.riskLevel(),
                alert.ruleSetVersion(),
                alert.status(),
                alert.assignedAnalyst(),
                alert.createdAt(),
                alert.reviewedAt(),
                alert.analystNote(),
                matches
        );
    }
}
