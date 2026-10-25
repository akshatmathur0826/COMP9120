package com.frauddetection;

import com.frauddetection.dto.AuthOutcome;
import com.frauddetection.dto.CreateTransactionRequest;
import com.frauddetection.dto.TransactionSubmissionResponse;
import com.frauddetection.dto.UpdateAlertStatusRequest;
import com.frauddetection.dto.DemoResponses.HeldLockResult;
import com.frauddetection.service.AlertReviewService;
import com.frauddetection.service.DemoService;
import com.frauddetection.service.TransactionProcessingService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Service-level transaction tests T1-T8 from test-cases.md.
 *
 * Runs against a dedicated test database (default {@code fraud_test}; override with
 * TEST_DB_NAME). Every test drops and reloads schema.sql + seed.sql, so the database
 * name must end in "_test" - the suite refuses to touch anything else. If the test
 * database is unreachable the suite is skipped rather than failed.
 *
 * Setup once:  createdb fraud_test
 * Run:         sh mvnw test -Dtest=TransactionIntegrityTests
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${TEST_DB_NAME:fraud_test}"
                + "?currentSchema=fraud_detection&prepareThreshold=0",
        "spring.datasource.username=${DB_USER:${user.name}}",
        "spring.datasource.password=${DB_PASSWORD:}"
})
@EnabledIf(value = "testDatabaseAvailable",
        disabledReason = "Test database not reachable; run `createdb fraud_test` to enable these tests")
class TransactionIntegrityTests {

    private static final long ALICE = 1001L, BEN = 1002L, CHLOE = 1003L;

    @Autowired private TransactionProcessingService intake;
    @Autowired private AlertReviewService reviews;
    @Autowired private DemoService demo;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    private int requestSeq;

    static boolean testDatabaseAvailable() {
        String url = "jdbc:postgresql://" + env("DB_HOST", "localhost") + ":" + env("DB_PORT", "5432")
                + "/" + env("TEST_DB_NAME", "fraud_test") + "?connectTimeout=3";
        try (Connection ignored = DriverManager.getConnection(
                url, env("DB_USER", System.getProperty("user.name")), env("DB_PASSWORD", ""))) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static String env(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }

    @BeforeEach
    void resetDatabase() throws Exception {
        String database = jdbc.queryForObject("SELECT current_database()", String.class);
        if (database == null || !database.endsWith("_test")) {
            throw new IllegalStateException(
                    "Refusing to reset database '" + database + "': name must end in _test");
        }
        jdbc.execute("DROP SCHEMA IF EXISTS fraud_detection CASCADE");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(projectFile("schema.sql")));
            ScriptUtils.executeSqlScript(connection, new FileSystemResource(projectFile("seed.sql")));
        }
    }

    // T1
    @Test
    @DisplayName("T1 normal transaction commits one LOW assessment, no matches, no alert")
    void normalTransaction() {
        var result = submit(ALICE, "42.50", 1, AuthOutcome.APPROVED, minutesAgo(30));

        assertThat(result.score()).isZero();
        assertThat(result.riskLevel()).isEqualTo("LOW");
        assertThat(result.matchedRules()).isEmpty();
        assertThat(result.alertId()).isNull();
        assertThat(count("risk_assessment WHERE transaction_id = ?", result.transactionId())).isEqualTo(1);
        assertThat(count("rule_match WHERE assessment_id = ?", result.assessmentId())).isZero();
        assertThat(count("fraud_alert WHERE assessment_id = ?", result.assessmentId())).isZero();
    }

    // T2 + insufficient history for R2
    @Test
    @DisplayName("T2 R1 fires at exactly AUD 5,000.00 (not 4,999.99); R2 needs 5 earlier approvals")
    void largeAmountBoundary() {
        var below = submit(CHLOE, "4999.99", 3, AuthOutcome.APPROVED, minutesAgo(60));
        assertThat(below.matchedRules()).isEmpty();

        // Chloe has only 3 earlier approved transactions, so R2 cannot fire.
        var atThreshold = submit(CHLOE, "5000.00", 3, AuthOutcome.APPROVED, minutesAgo(30));
        assertThat(atThreshold.matchedRules()).containsExactly("R1");
        assertThat(atThreshold.score()).isEqualTo(40);
        assertThat(atThreshold.riskLevel()).isEqualTo("MEDIUM");
        assertThat(atThreshold.alertId()).isNotNull();
    }

    // T3
    @Test
    @DisplayName("T3 AUD 6,200 on an unseen device for Alice matches R1 + R2 + R4 = 100 HIGH")
    void newDeviceLargeAmount() {
        var result = submit(ALICE, "6200.00", 3, AuthOutcome.APPROVED, minutesAgo(30));

        assertThat(result.matchedRules()).containsExactlyInAnyOrder("R1", "R2", "R4");
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.riskLevel()).isEqualTo("HIGH");
        assertThat(result.alertId()).isNotNull();
        assertThat(count("rule_match WHERE assessment_id = ?", result.assessmentId())).isEqualTo(3);
    }

    @Test
    @DisplayName("R4 fires at exactly AUD 1,000.00 on an unseen device (not 999.99)")
    void newDeviceBoundary() {
        assertThat(submit(CHLOE, "999.99", 2, AuthOutcome.APPROVED, minutesAgo(60)).matchedRules()).isEmpty();
        assertThat(submit(CHLOE, "1000.00", 1, AuthOutcome.APPROVED, minutesAgo(30)).matchedRules())
                .containsExactly("R4");
    }

    // T4
    @Test
    @DisplayName("T4 velocity: attempts 1-4 in 10 minutes do not match R3; the 5th does")
    void velocityBoundary() {
        OffsetDateTime start = minutesAgo(30);
        for (int i = 0; i < 4; i++) {
            assertThat(submit(BEN, "15.00", 2, AuthOutcome.APPROVED, start.plusMinutes(i)).matchedRules())
                    .as("attempt %d", i + 1).isEmpty();
        }
        var fifth = submit(BEN, "15.00", 2, AuthOutcome.APPROVED, start.plusMinutes(4));
        assertThat(fifth.matchedRules()).containsExactly("R3");
        assertThat(fifth.alertId()).isNotNull();
    }

    @Test
    @DisplayName("R3 window is inclusive at 10:00 and excludes the first attempt at 10:01")
    void velocityWindowEdge() {
        OffsetDateTime start = minutesAgo(120);
        for (int i = 0; i < 4; i++) submit(BEN, "15.00", 2, AuthOutcome.APPROVED, start.plusMinutes(i));
        assertThat(submit(BEN, "15.00", 2, AuthOutcome.APPROVED, start.plusMinutes(10)).matchedRules())
                .containsExactly("R3");

        OffsetDateTime later = minutesAgo(60);
        for (int i = 0; i < 4; i++) submit(BEN, "15.00", 2, AuthOutcome.APPROVED, later.plusMinutes(i));
        assertThat(submit(BEN, "15.00", 2, AuthOutcome.APPROVED, later.plusMinutes(10).plusSeconds(1))
                .matchedRules()).isEmpty();
    }

    @Test
    @DisplayName("R5: the third decline within five minutes matches; score 25 stays below the alert threshold")
    void repeatedDeclines() {
        OffsetDateTime start = minutesAgo(30);
        submit(BEN, "20.00", 2, AuthOutcome.DECLINED, start);
        submit(BEN, "20.00", 2, AuthOutcome.DECLINED, start.plusMinutes(1));
        var third = submit(BEN, "20.00", 2, AuthOutcome.DECLINED, start.plusMinutes(2));

        assertThat(third.matchedRules()).containsExactly("R5");
        assertThat(third.score()).isEqualTo(25);
        assertThat(third.alertId()).isNull();
    }

    // T5
    @Test
    @DisplayName("T5 a failure while inserting the alert rolls back transaction, assessment and matches")
    void forcedAlertFailureRollsBackEverything() {
        jdbc.execute("""
                CREATE FUNCTION fraud_detection.fail_alert_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced alert insert failure'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER force_alert_failure BEFORE INSERT ON fraud_detection.fraud_alert
                FOR EACH ROW EXECUTE FUNCTION fraud_detection.fail_alert_insert()
                """);
        List<Integer> before = tableCounts();
        String requestId = nextRequestId();

        assertThatThrownBy(() -> intake.process(request(requestId, ALICE, "7000.00", 1,
                AuthOutcome.APPROVED, minutesAgo(30))))
                .hasMessageContaining("forced alert insert failure");

        assertThat(tableCounts()).as("transaction/assessment/match/alert counts").isEqualTo(before);
        assertThat(count("payment_transaction WHERE request_id = ?", requestId)).isZero();

        // Once the fault is removed the same request succeeds, proving nothing was left behind.
        jdbc.execute("DROP TRIGGER force_alert_failure ON fraud_detection.fraud_alert");
        var retry = intake.process(request(requestId, ALICE, "7000.00", 1, AuthOutcome.APPROVED, minutesAgo(30)));
        assertThat(retry.alertId()).isNotNull();
    }

    // T6
    @Test
    @DisplayName("T6 a duplicate requestId is rejected and creates no second transaction")
    void duplicateRequestId() {
        String requestId = nextRequestId();
        intake.process(request(requestId, ALICE, "10.00", 1, AuthOutcome.APPROVED, minutesAgo(30)));
        int before = count("payment_transaction");

        assertThatThrownBy(() -> intake.process(request(requestId, ALICE, "10.00", 1,
                AuthOutcome.APPROVED, minutesAgo(29))))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("already been submitted");
        assertThat(count("payment_transaction")).isEqualTo(before);
    }

    @Test
    @DisplayName("Intake rejects a blocked card before writing anything")
    void blockedCardRejected() {
        jdbc.update("UPDATE fraud_detection.card SET status = 'BLOCKED' WHERE card_id = ?", CHLOE);
        int before = count("payment_transaction");

        assertThatThrownBy(() -> submit(CHLOE, "10.00", 3, AuthOutcome.APPROVED, minutesAgo(5)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ACTIVE");
        assertThat(count("payment_transaction")).isEqualTo(before);
    }

    // T7
    @Test
    @DisplayName("T7 six simultaneous same-card attempts are serialised: exactly attempts 5 and 6 match R3")
    void concurrentSameCardIntakeIsSerialised() throws Exception {
        // Widen the race window: each intake pauses after inserting its row, before it
        // reads history. With the card lock the six run one after another and each sees
        // the earlier commits; without it they overlap and none sees the others.
        jdbc.execute("""
                CREATE FUNCTION fraud_detection.slow_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN PERFORM pg_sleep(0.5); RETURN NULL; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER slow_transaction_insert AFTER INSERT ON fraud_detection.payment_transaction
                FOR EACH ROW EXECUTE FUNCTION fraud_detection.slow_insert()
                """);

        OffsetDateTime sameInstant = minutesAgo(5);
        int attempts = 6;
        warmConnectionPool(attempts);
        CountDownLatch startGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<TransactionSubmissionResponse>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                var req = request(nextRequestId(), ALICE, "12.00", 1, AuthOutcome.APPROVED, sameInstant);
                Callable<TransactionSubmissionResponse> task = () -> {
                    startGun.await();
                    return intake.process(req);
                };
                futures.add(pool.submit(task));
            }
            startGun.countDown();

            List<List<String>> matches = new ArrayList<>();
            for (var future : futures) matches.add(future.get().matchedRules());
            long r3Matches = matches.stream().filter(m -> m.contains("R3")).count();
            // Without the SELECT ... FOR UPDATE card lock, concurrent attempts could read
            // history before the others commit and all miss the five-attempt threshold.
            assertThat(r3Matches).as("R3 matches across attempts %s", matches).isEqualTo(2);
        } finally {
            pool.shutdownNow();
        }
    }

    // T8
    @Test
    @DisplayName("T8 review: only allowed transitions, note required to close, every change audited")
    void alertReviewTransitions() {
        long alert = 9001L; // seeded OPEN alert

        assertThatThrownBy(() -> review(alert, "RESOLVED", 1L, "too early"))
                .hasMessageContaining("Cannot change alert from OPEN to RESOLVED");

        var investigating = review(alert, "INVESTIGATING", 2L, null);
        assertThat(investigating.status()).isEqualTo("INVESTIGATING");
        assertThat(investigating.reviewedAt()).isNull();

        int reviewsBefore = count("alert_review WHERE alert_id = ?", alert);
        assertThatThrownBy(() -> review(alert, "RESOLVED", 2L, "   "))
                .hasMessageContaining("note is required");
        assertThat(count("alert_review WHERE alert_id = ?", alert)).isEqualTo(reviewsBefore);

        var resolved = review(alert, "RESOLVED", 2L, "Customer confirmed the purchase.");
        assertThat(resolved.status()).isEqualTo("RESOLVED");
        assertThat(resolved.reviewedAt()).isNotNull();
        assertThat(resolved.analystNote()).isEqualTo("Customer confirmed the purchase.");

        assertThatThrownBy(() -> review(alert, "DISMISSED", 2L, "late"))
                .hasMessageContaining("Cannot change alert from RESOLVED");
        assertThat(reviews.getReviews(alert))
                .extracting(r -> r.oldStatus() + "->" + r.newStatus())
                .containsExactly("OPEN->INVESTIGATING", "INVESTIGATING->RESOLVED");
    }

    @Test
    @DisplayName("T8 a failure writing the audit row rolls back the alert status change")
    void reviewAuditFailureRollsBackStatus() {
        jdbc.execute("""
                CREATE FUNCTION fraud_detection.fail_review_insert() RETURNS trigger
                LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'forced review insert failure'; END $$
                """);
        jdbc.execute("""
                CREATE TRIGGER force_review_failure BEFORE INSERT ON fraud_detection.alert_review
                FOR EACH ROW EXECUTE FUNCTION fraud_detection.fail_review_insert()
                """);

        assertThatThrownBy(() -> review(9001L, "INVESTIGATING", 1L, null))
                .hasMessageContaining("forced review insert failure");
        assertThat(jdbc.queryForObject(
                "SELECT status FROM fraud_detection.fraud_alert WHERE alert_id = 9001", String.class))
                .isEqualTo("OPEN");
    }

    @Test
    @DisplayName("Database constraints reject invalid rows even if the service is bypassed")
    void databaseConstraints() {
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO fraud_detection.payment_transaction
                    (request_id, card_id, merchant_id, device_id, location_id, amount, occurred_at, auth_outcome)
                VALUES ('bad-amount', 1001, 1, 1, 1, 0, now(), 'APPROVED')"""))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_transaction_amount");
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO fraud_detection.payment_transaction
                    (request_id, card_id, merchant_id, device_id, location_id, amount, occurred_at, auth_outcome)
                VALUES ('bad-card', 999999, 1, 1, 1, 10, now(), 'APPROVED')"""))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                UPDATE fraud_detection.fraud_alert
                SET status = 'RESOLVED', reviewed_at = NULL, analyst_note = NULL
                WHERE alert_id = 9001"""))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_alert_closure_evidence");
        assertThatThrownBy(() -> jdbc.update(
                "UPDATE fraud_detection.risk_assessment SET score = 10, risk_level = 'HIGH' WHERE assessment_id = 50007"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_assessment_level");
    }

    // Demo page (/demo) ----------------------------------------------------------

    @Test
    @DisplayName("Demo: forced alert failure rolls back every row and leaves no trigger behind")
    void demoForcedRollback() {
        var result = demo.forcedRollback(ALICE);

        assertThat(result.failureMessage()).startsWith("Forced failure at step 7 (alert insert)");
        assertThat(result.failureMessage()).contains("rule match(es)");
        assertThat(result.after()).isEqualTo(result.before());
        assertThat(result.requestStored()).isFalse();
        // The fault trigger was created inside the rolled-back transaction, so it is gone.
        assertThat(count("payment_transaction")).isEqualTo((int) result.before().transactions());
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM pg_trigger WHERE tgname = 'demo_force_alert_failure'", Integer.class))
                .isZero();
        assertThat(submit(ALICE, "7500.00", 1, AuthOutcome.APPROVED, minutesAgo(1)).alertId())
                .as("normal intake still raises alerts afterwards").isNotNull();
    }

    @Test
    @DisplayName("Demo: simultaneous attempts holding the card lock queue one behind another")
    void demoHeldLockQueuing() throws Exception {
        OffsetDateTime sameInstant = minutesAgo(5);
        int attempts = 4;
        warmConnectionPool(attempts);
        CountDownLatch startGun = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<HeldLockResult>> futures = new ArrayList<>();
            for (int i = 0; i < attempts; i++) {
                var req = request(nextRequestId(), BEN, "12.00", 2, AuthOutcome.APPROVED, sameInstant);
                futures.add(pool.submit(() -> {
                    startGun.await();
                    return demo.intakeHoldingCardLock(req, 300);
                }));
            }
            startGun.countDown();
            List<Long> waits = new ArrayList<>();
            for (var future : futures) waits.add(future.get().lockWaitMillis());
            waits.sort(Long::compare);
            // Each attempt holds the lock for 300 ms, so the k-th waits roughly k x 300 ms.
            assertThat(waits.get(0)).isLessThan(250);
            assertThat(waits.get(attempts - 1)).isGreaterThanOrEqualTo(800);
        } finally {
            pool.shutdownNow();
        }
    }

    // --- helpers ---------------------------------------------------------------

    private TransactionSubmissionResponse submit(
            long cardId, String amount, long deviceId, AuthOutcome outcome, OffsetDateTime at) {
        return intake.process(request(nextRequestId(), cardId, amount, deviceId, outcome, at));
    }

    private CreateTransactionRequest request(
            String requestId, long cardId, String amount, long deviceId,
            AuthOutcome outcome, OffsetDateTime at) {
        return new CreateTransactionRequest(
                requestId, cardId, 1L, deviceId, 1L, new BigDecimal(amount), at, outcome);
    }

    private com.frauddetection.dto.AlertDetailResponse review(
            long alertId, String status, long analystId, String note) {
        return reviews.updateStatus(alertId, new UpdateAlertStatusRequest(status, analystId, note));
    }

    private String nextRequestId() {
        return "it-" + (++requestSeq);
    }

    // Open the connections up front so all intake threads start together instead of
    // being staggered while the pool creates connections.
    private void warmConnectionPool(int size) throws Exception {
        List<Connection> held = new ArrayList<>();
        try {
            for (int i = 0; i < size; i++) held.add(dataSource.getConnection());
        } finally {
            for (Connection c : held) c.close();
        }
    }

    private static OffsetDateTime minutesAgo(long minutes) {
        return OffsetDateTime.now(ZoneOffset.UTC).withNano(0).minusMinutes(minutes);
    }

    private int count(String fromWhere, Object... args) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM fraud_detection." + fromWhere, Integer.class, args);
        return n == null ? 0 : n;
    }

    private List<Integer> tableCounts() {
        return List.of(count("payment_transaction"), count("risk_assessment"),
                count("rule_match"), count("fraud_alert"));
    }

    private static Path projectFile(String name) {
        for (Path candidate : List.of(Path.of("..", name), Path.of(name))) {
            if (Files.exists(candidate)) return candidate;
        }
        throw new IllegalStateException("Cannot find " + name + " in the project root");
    }
}
