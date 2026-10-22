package com.frauddetection.service;

import com.frauddetection.dto.CreateTransactionRequest;
import com.frauddetection.dto.TransactionSubmissionResponse;
import com.frauddetection.repository.TransactionProcessingRepository;
import com.frauddetection.repository.TransactionProcessingRepository.RuleConfig;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
public class TransactionProcessingService {

    private final TransactionProcessingRepository repository;

    public TransactionProcessingService(
            TransactionProcessingRepository repository) {
        this.repository = repository;
    }

    private record RuleHit(
            Long ruleId,
            String ruleCode,
            int weight,
            BigDecimal observedValue,
            String explanation
    ) {}

    @Transactional
    public TransactionSubmissionResponse process(
            CreateTransactionRequest request) {

        var card = repository.lockCard(request.cardId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Card not found"));

        if (!"ACTIVE".equals(card.cardStatus())
                || !"ACTIVE".equals(card.accountStatus())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Both card and account must be ACTIVE");
        }

        if (repository.findTransactionIdByRequestId(request.requestId())
                .isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "requestId has already been submitted");
        }

        List<RuleConfig> rules = repository.findActiveRules();

        if (rules.isEmpty()) {
            throw new IllegalStateException(
                    "No active fraud rules are configured");
        }

        var versions = rules.stream()
                .map(RuleConfig::version)
                .distinct()
                .toList();

        if (versions.size() != 1) {
            throw new IllegalStateException(
                    "Active rules must use one rule-set version");
        }

        Long transactionId = repository.insertTransaction(request);

        List<RuleHit> hits = new ArrayList<>();

        for (RuleConfig rule : rules) {
            RuleHit hit = evaluate(rule, request, transactionId);
            if (hit != null) {
                hits.add(hit);
            }
        }

        int score = hits.stream()
                .mapToInt(RuleHit::weight)
                .sum();

        String riskLevel = score >= 60
                ? "HIGH"
                : score >= 30 ? "MEDIUM" : "LOW";

        Long assessmentId = repository.insertAssessment(
                transactionId, score, riskLevel, versions.getFirst());

        for (RuleHit hit : hits) {
            repository.insertRuleMatch(
                    assessmentId,
                    hit.ruleId(),
                    hit.observedValue(),
                    hit.explanation()
            );
        }

        Long alertId = score >= 30
                ? repository.insertAlert(assessmentId)
                : null;

        return new TransactionSubmissionResponse(
                transactionId,
                assessmentId,
                score,
                riskLevel,
                hits.stream().map(RuleHit::ruleCode).toList(),
                alertId
        );
    }

    private RuleHit evaluate(
            RuleConfig rule,
            CreateTransactionRequest request,
            Long transactionId) {

        return switch (rule.ruleCode()) {
            case "R1" -> {
                BigDecimal threshold = required(
                        rule.minimumAmount(), "R1 minimum_amount");

                yield request.amount().compareTo(threshold) >= 0
                        ? hit(rule, request.amount(),
                            "Amount " + request.amount()
                            + " is at least " + threshold)
                        : null;
            }

            case "R2" -> {
                int minimumCount = required(
                        rule.minimumHistoryCount(),
                        "R2 minimum_history_count");

                BigDecimal multiplier = required(
                        rule.historicalMultiplier(),
                        "R2 historical_multiplier");

                BigDecimal minimumAmount = required(
                        rule.minimumAmount(), "R2 minimum_amount");

                var history = repository.findApprovedHistory(
                        request.cardId(),
                        request.occurredAt(),
                        transactionId
                );

                if (history.count() < minimumCount
                        || history.average() == null) {
                    yield null;
                }

                BigDecimal threshold = history.average()
                        .multiply(multiplier)
                        .max(minimumAmount);

                yield request.amount().compareTo(threshold) >= 0
                        ? hit(rule, request.amount(),
                            "Amount " + request.amount()
                            + " meets threshold " + threshold
                            + "; prior approved average "
                            + history.average()
                            + " across " + history.count() + " attempts")
                        : null;
            }

            case "R3", "R5" -> {
                int window = required(
                        rule.windowSeconds(),
                        rule.ruleCode() + " event_window_seconds");

                int minimumCount = required(
                        rule.minimumEventCount(),
                        rule.ruleCode() + " minimum_event_count");

                boolean declinedOnly = "R5".equals(rule.ruleCode());

                long count = repository.countAttempts(
                        request.cardId(),
                        request.occurredAt(),
                        transactionId,
                        window,
                        declinedOnly
                );

                yield count >= minimumCount
                        ? hit(rule, BigDecimal.valueOf(count),
                            count + (declinedOnly
                                ? " declined attempts"
                                : " attempts")
                            + " in the preceding " + window
                            + " seconds, including the current attempt "
                            + "when applicable")
                        : null;
            }

            case "R4" -> {
                BigDecimal threshold = required(
                        rule.minimumAmount(), "R4 minimum_amount");

                boolean knownDevice = repository.hasEarlierDeviceUse(
                        request.cardId(),
                        request.deviceId(),
                        request.occurredAt(),
                        transactionId
                );

                yield !knownDevice
                        && request.amount().compareTo(threshold) >= 0
                        ? hit(rule, request.amount(),
                            "No earlier use of this device by this card; "
                            + "amount " + request.amount()
                            + " is at least " + threshold)
                        : null;
            }

            default -> throw new IllegalStateException(
                    "Unsupported active rule: " + rule.ruleCode());
        };
    }

    private RuleHit hit(
            RuleConfig rule,
            BigDecimal observedValue,
            String explanation) {

        return new RuleHit(
                rule.ruleId(),
                rule.ruleCode(),
                rule.weight(),
                observedValue,
                explanation
        );
    }

    private <T> T required(T value, String name) {
        if (value == null) {
            throw new IllegalStateException(
                    "Missing rule configuration: " + name);
        }
        return value;
    }
}
