package com.frauddetection.service;

import com.frauddetection.dto.TransactionDetailResponse;
import com.frauddetection.repository.AlertDetailRepository;
import com.frauddetection.repository.TransactionDetailRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class TransactionDetailService {

    private final TransactionDetailRepository repository;
    private final AlertDetailRepository alertDetailRepository;

    public TransactionDetailService(
            TransactionDetailRepository repository,
            AlertDetailRepository alertDetailRepository) {
        this.repository = repository;
        this.alertDetailRepository = alertDetailRepository;
    }

    @Transactional(readOnly = true)
    public TransactionDetailResponse getTransaction(Long transactionId) {
        var t = repository.findHeader(transactionId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Transaction not found"));

        var matches = t.assessmentId() == null
                ? List.<com.frauddetection.dto.AlertDetailResponse.MatchedRule>of()
                : alertDetailRepository.findMatches(t.assessmentId());

        return new TransactionDetailResponse(
                t.transactionId(),
                t.requestId(),
                t.cardId(),
                t.cardToken(),
                t.accountNumber(),
                t.customerName(),
                t.merchantName(),
                t.merchantCategory(),
                t.deviceFingerprint(),
                t.city(),
                t.countryCode(),
                t.amount(),
                t.occurredAt(),
                t.authOutcome(),
                t.assessmentId(),
                t.score(),
                t.riskLevel(),
                t.ruleSetVersion(),
                t.alertId(),
                t.alertStatus(),
                matches
        );
    }
}
