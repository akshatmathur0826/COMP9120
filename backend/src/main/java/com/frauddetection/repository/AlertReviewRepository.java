package com.frauddetection.repository;

import com.frauddetection.dto.AlertReviewResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class AlertReviewRepository {

    private final JdbcTemplate jdbc;

    public AlertReviewRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // Row lock serialises concurrent reviews of the same alert.
    public Optional<String> lockAlertStatus(Long alertId) {
        return jdbc.query(
                """
                SELECT status
                FROM fraud_detection.fraud_alert
                WHERE alert_id = ?
                FOR UPDATE
                """,
                (rs, rowNum) -> rs.getString("status"),
                alertId
        ).stream().findFirst();
    }

    public boolean alertExists(Long alertId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM fraud_detection.fraud_alert
                    WHERE alert_id = ?
                )
                """,
                Boolean.class,
                alertId
        ));
    }

    public boolean isActiveAnalyst(Long analystId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM fraud_detection.analyst
                    WHERE analyst_id = ?
                      AND active = TRUE
                )
                """,
                Boolean.class,
                analystId
        ));
    }

    public void updateAlertStatus(
            Long alertId, String newStatus,
            boolean terminal, Long analystId, String note) {

        jdbc.update(
                """
                UPDATE fraud_detection.fraud_alert
                SET status = ?,
                    reviewed_at = CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END,
                    analyst_note = COALESCE(?, analyst_note),
                    assigned_analyst_id = COALESCE(assigned_analyst_id, ?)
                WHERE alert_id = ?
                """,
                newStatus, terminal, note, analystId, alertId
        );
    }

    public void insertReview(
            Long alertId, Long analystId,
            String oldStatus, String newStatus, String note) {

        jdbc.update(
                """
                INSERT INTO fraud_detection.alert_review
                    (alert_id, changed_by_analyst_id, old_status, new_status, note)
                VALUES (?, ?, ?, ?, ?)
                """,
                alertId, analystId, oldStatus, newStatus, note
        );
    }

    public List<AlertReviewResponse> findReviews(Long alertId) {
        return jdbc.query(
                """
                SELECT ar.review_id, an.display_name AS changed_by,
                       ar.changed_at, ar.old_status, ar.new_status, ar.note
                FROM fraud_detection.alert_review ar
                JOIN fraud_detection.analyst an
                    ON an.analyst_id = ar.changed_by_analyst_id
                WHERE ar.alert_id = ?
                ORDER BY ar.changed_at, ar.review_id
                """,
                (rs, rowNum) -> new AlertReviewResponse(
                        rs.getLong("review_id"),
                        rs.getString("changed_by"),
                        rs.getObject("changed_at", OffsetDateTime.class),
                        rs.getString("old_status"),
                        rs.getString("new_status"),
                        rs.getString("note")
                ),
                alertId
        );
    }
}
