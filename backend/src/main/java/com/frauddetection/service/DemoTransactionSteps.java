package com.frauddetection.service;

import com.frauddetection.dto.CreateTransactionRequest;
import com.frauddetection.dto.DemoResponses.HeldLockResult;
import com.frauddetection.repository.DemoRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Transactional wrappers used only by the demo page. Each method opens one database
 * transaction and runs the real TransactionProcessingService.process inside it
 * (it joins the same transaction), so the demo exercises production intake code.
 */
@Service
public class DemoTransactionSteps {

    private final TransactionProcessingService intake;
    private final DemoRepository repository;

    public DemoTransactionSteps(TransactionProcessingService intake, DemoRepository repository) {
        this.intake = intake;
        this.repository = repository;
    }

    @Transactional
    public void intakeWithFailingAlertInsert(CreateTransactionRequest request) {
        repository.installAlertInsertFault();
        intake.process(request);
        // Only reached if no alert was raised; still roll back so nothing is kept.
        throw new IllegalStateException(
                "Demo transaction finished without reaching the alert step; rolled back anyway");
    }

    @Transactional
    public HeldLockResult intakeHoldingCardLock(CreateTransactionRequest request, long holdMillis) {
        long start = System.nanoTime();
        repository.lockCard(request.cardId());          // the same row lock intake takes
        long locked = System.nanoTime();
        OffsetDateTime lockedAt = OffsetDateTime.now(ZoneOffset.UTC);
        if (holdMillis > 0) {
            repository.sleep(holdMillis);                // keep the lock so queuing is visible
        }
        var submission = intake.process(request);       // re-locking our own row is a no-op
        long done = System.nanoTime();
        return new HeldLockResult(
                submission,
                (locked - start) / 1_000_000,
                (done - start) / 1_000_000,
                lockedAt);
    }
}
