package com.frauddetection.service;

import com.frauddetection.dto.AuthOutcome;
import com.frauddetection.dto.CreateTransactionRequest;
import com.frauddetection.dto.DemoResponses.ForcedRollbackResult;
import com.frauddetection.dto.DemoResponses.HeldLockResult;
import com.frauddetection.dto.DemoResponses.RecentAttempts;
import com.frauddetection.repository.DemoRepository;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Service
public class DemoService {

    // Large enough to match R1 on any card, so intake always reaches the alert step.
    private static final BigDecimal ROLLBACK_DEMO_AMOUNT = new BigDecimal("7500.00");

    private final DemoTransactionSteps steps;
    private final DemoRepository repository;

    public DemoService(DemoTransactionSteps steps, DemoRepository repository) {
        this.steps = steps;
        this.repository = repository;
    }

    /** Not transactional: counts are read before and after the rolled-back transaction. */
    public ForcedRollbackResult forcedRollback(Long cardId) {
        var request = new CreateTransactionRequest(
                "demo-rollback-" + UUID.randomUUID().toString().substring(0, 8),
                cardId,
                repository.findAnyMerchant(),
                repository.findKnownDevice(cardId).orElseGet(repository::findAnyDevice),
                repository.findAnyLocation(),
                ROLLBACK_DEMO_AMOUNT,
                OffsetDateTime.now(ZoneOffset.UTC).minusSeconds(1),
                AuthOutcome.APPROVED);

        var before = repository.counts();
        String failure;
        try {
            steps.intakeWithFailingAlertInsert(request);
            failure = "No failure occurred";
        } catch (ResponseStatusException e) {
            throw e; // e.g. unknown or inactive card: report it as a normal API error
        } catch (RuntimeException e) {
            failure = describe(e);
        }
        var after = repository.counts();

        return new ForcedRollbackResult(request.requestId(), cardId, failure, before, after,
                repository.requestExists(request.requestId()));
    }

    public HeldLockResult intakeHoldingCardLock(CreateTransactionRequest request, long holdMillis) {
        if (holdMillis < 0 || holdMillis > 2000) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "holdMillis must be between 0 and 2000");
        }
        return steps.intakeHoldingCardLock(request, holdMillis);
    }

    public RecentAttempts recentAttempts(Long cardId) {
        var rule = repository.findVelocityRule();
        return new RecentAttempts(cardId, rule.windowSeconds(), rule.minimumEventCount(),
                repository.countRecentAttempts(cardId, rule.windowSeconds()));
    }

    private static String describe(Throwable e) {
        Throwable cause = NestedExceptionUtils.getMostSpecificCause(e);
        String message = cause.getMessage() == null ? cause.toString() : cause.getMessage();
        message = message.lines().findFirst().orElse(message);
        return message.startsWith("ERROR: ") ? message.substring(7) : message;
    }
}
