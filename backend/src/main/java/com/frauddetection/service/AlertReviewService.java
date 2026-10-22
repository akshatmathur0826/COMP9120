package com.frauddetection.service;

import com.frauddetection.dto.AlertDetailResponse;
import com.frauddetection.dto.AlertReviewResponse;
import com.frauddetection.dto.UpdateAlertStatusRequest;
import com.frauddetection.repository.AlertReviewRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class AlertReviewService {

    // FR5 lifecycle: OPEN -> INVESTIGATING -> RESOLVED / DISMISSED.
    // An OPEN alert may also be dismissed directly (test case T8).
    private static final Map<String, Set<String>> ALLOWED_TRANSITIONS = Map.of(
            "OPEN", Set.of("INVESTIGATING", "DISMISSED"),
            "INVESTIGATING", Set.of("RESOLVED", "DISMISSED"),
            "RESOLVED", Set.of(),
            "DISMISSED", Set.of()
    );

    private static final Set<String> TERMINAL = Set.of("RESOLVED", "DISMISSED");

    private final AlertReviewRepository repository;
    private final AlertDetailService alertDetailService;

    public AlertReviewService(
            AlertReviewRepository repository,
            AlertDetailService alertDetailService) {
        this.repository = repository;
        this.alertDetailService = alertDetailService;
    }

    @Transactional
    public AlertDetailResponse updateStatus(
            Long alertId, UpdateAlertStatusRequest request) {

        String currentStatus = repository.lockAlertStatus(alertId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Alert not found"));

        String newStatus = request.newStatus();

        if (!ALLOWED_TRANSITIONS.getOrDefault(currentStatus, Set.of())
                .contains(newStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot change alert from " + currentStatus
                    + " to " + newStatus);
        }

        if (!repository.isActiveAnalyst(request.analystId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "analystId must reference an active analyst");
        }

        String note = request.note() == null || request.note().isBlank()
                ? null
                : request.note().trim();

        boolean terminal = TERMINAL.contains(newStatus);

        if (terminal && note == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A note is required to mark an alert " + newStatus);
        }

        repository.updateAlertStatus(
                alertId, newStatus, terminal, request.analystId(), note);
        repository.insertReview(
                alertId, request.analystId(), currentStatus, newStatus, note);

        return alertDetailService.getAlert(alertId);
    }

    @Transactional(readOnly = true)
    public List<AlertReviewResponse> getReviews(Long alertId) {
        if (!repository.alertExists(alertId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, "Alert not found");
        }
        return repository.findReviews(alertId);
    }
}
