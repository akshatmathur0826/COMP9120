package com.frauddetection.controller;

import com.frauddetection.dto.CreateTransactionRequest;
import com.frauddetection.dto.DemoResponses.ForcedRollbackResult;
import com.frauddetection.dto.DemoResponses.HeldLockResult;
import com.frauddetection.dto.DemoResponses.RecentAttempts;
import com.frauddetection.dto.DemoResponses.TableCounts;
import com.frauddetection.repository.DemoRepository;
import com.frauddetection.service.DemoService;

import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints behind the "Transaction demo" page, used to show transaction boundaries,
 * rollback and card-level locking. Disable with app.demo.enabled=false.
 */
@RestController
@RequestMapping("/api/demo")
@ConditionalOnProperty(name = "app.demo.enabled", havingValue = "true", matchIfMissing = true)
public class DemoController {

    private final DemoService service;
    private final DemoRepository repository;

    public DemoController(DemoService service, DemoRepository repository) {
        this.service = service;
        this.repository = repository;
    }

    @GetMapping("/counts")
    public TableCounts counts() {
        return repository.counts();
    }

    @PostMapping("/forced-rollback")
    public ForcedRollbackResult forcedRollback(@RequestParam("cardId") Long cardId) {
        return service.forcedRollback(cardId);
    }

    @PostMapping("/held-lock-intake")
    public HeldLockResult heldLockIntake(
            @Valid @RequestBody CreateTransactionRequest request,
            @RequestParam(name = "holdMillis", defaultValue = "500") long holdMillis) {
        return service.intakeHoldingCardLock(request, holdMillis);
    }

    @GetMapping("/cards/{id}/recent-attempts")
    public RecentAttempts recentAttempts(@PathVariable("id") Long cardId) {
        return service.recentAttempts(cardId);
    }
}
