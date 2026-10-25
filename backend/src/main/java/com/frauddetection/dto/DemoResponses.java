package com.frauddetection.dto;

import java.time.OffsetDateTime;

/** Response shapes for the transaction-demonstration endpoints (/api/demo). */
public final class DemoResponses {

    private DemoResponses() {}

    public record TableCounts(
            long transactions,
            long assessments,
            long ruleMatches,
            long alerts
    ) {}

    public record ForcedRollbackResult(
            String requestId,
            Long cardId,
            String failureMessage,
            TableCounts before,
            TableCounts after,
            boolean requestStored
    ) {}

    public record HeldLockResult(
            TransactionSubmissionResponse submission,
            long lockWaitMillis,
            long totalMillis,
            OffsetDateTime lockAcquiredAt
    ) {}

    public record RecentAttempts(
            Long cardId,
            int windowSeconds,
            int minimumEventCount,
            long attempts
    ) {}
}
