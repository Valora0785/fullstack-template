package com.example.funding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

@Repository
public class DraftRepository {
    private final JdbcTemplate jdbcTemplate;

    public DraftRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public int insertDraft(long projectId, long applicantId,
                           String purpose, long requestedFen) {
        return jdbcTemplate.update(
                "INSERT INTO funding_application " +
                        "(project_id, applicant_id, purpose, requested_fen) " +
                        "VALUES (?, ?, ?, ?)",
                projectId, applicantId, purpose, requestedFen
        );
    }
    public boolean isProjectMember(long projectId, long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM project_member " +
                        "WHERE project_id = ? AND user_id = ?",
                Long.class,
                projectId, userId
        );

        return count != null && count > 0;
    }
    public int updateOwnDraft(long projectId, long draftId, long currentUserId,
                              String purpose, long requestedFen) {
        String sql = """
            UPDATE funding_application a
            JOIN project_member m
              ON m.project_id = a.project_id
             AND m.user_id = a.applicant_id
            SET a.purpose = ?,
                a.requested_fen = ?
            WHERE a.id = ?
              AND a.project_id = ?
              AND a.applicant_id = ?
              AND a.status = 'DRAFT'
            """;

        return jdbcTemplate.update(
                sql,
                purpose,
                requestedFen,
                draftId,
                projectId,
                currentUserId
        );
    }

    public Optional<ApplicationSummary> findOwnDraftForUpdate(
            long projectId, long draftId, long currentUserId) {
        String sql = """
            SELECT a.id, a.applicant_id, a.purpose, a.requested_fen, a.status
            FROM funding_application a
            JOIN project_member m
              ON m.project_id = a.project_id
             AND m.user_id = a.applicant_id
            WHERE a.project_id = ?
              AND a.id = ?
              AND a.applicant_id = ?
              AND a.status = 'DRAFT'
            FOR UPDATE
            """;

        return jdbcTemplate.query(
                sql, this::mapApplicationSummary, projectId, draftId, currentUserId
        ).stream().findFirst();
    }

    public int markOwnDraftPending(long projectId, long draftId, long currentUserId) {
        String sql = """
            UPDATE funding_application a
            JOIN project_member m
              ON m.project_id = a.project_id
             AND m.user_id = a.applicant_id
            SET a.status = 'PENDING_L1'
            WHERE a.project_id = ?
              AND a.id = ?
              AND a.applicant_id = ?
              AND a.status = 'DRAFT'
            """;

        return jdbcTemplate.update(sql, projectId, draftId, currentUserId);
    }

    public int approvePendingApplication(long projectId, long applicationId, long teacherId) {
        return approvePendingApplication(projectId, applicationId, teacherId, null);
    }

    public Optional<Long> findPendingRequestedFenForUpdate(
            long projectId, long applicationId, long teacherId) {
        String sql = """
            SELECT a.requested_fen
            FROM funding_application a
            WHERE a.project_id = ?
              AND a.id = ?
              AND a.status = 'PENDING_L1'
              AND a.applicant_id <> ?
            FOR UPDATE
            """;

        return jdbcTemplate.query(
                sql, (rs, rowNum) -> rs.getLong("requested_fen"),
                projectId, applicationId, teacherId
        ).stream().findFirst();
    }

    public Optional<Long> findApprovedFenForUpdate(long projectId, long applicationId) {
        String sql = """
        SELECT a.approved_fen
        FROM funding_application a
        WHERE a.project_id = ?
          AND a.id = ?
          AND a.status = 'APPROVED'
        FOR UPDATE
        """;

        return jdbcTemplate.query(
                sql, (rs, rowNum) -> rs.getLong("approved_fen"),
                projectId, applicationId
        ).stream().findFirst();
    }

    public Optional<Long> findOwnCancelableFrozenFenForUpdate(
            long projectId, long applicationId, long applicantId) {
        String sql = """
        SELECT CASE a.status
                   WHEN 'PENDING_L1' THEN a.requested_fen
                   WHEN 'APPROVED' THEN a.approved_fen
               END AS remaining_frozen_fen
        FROM funding_application a
        WHERE a.project_id = ?
          AND a.id = ?
          AND a.applicant_id = ?
          AND a.status IN ('PENDING_L1', 'APPROVED')
        FOR UPDATE
        """;

        return jdbcTemplate.query(
                sql, (rs, rowNum) -> rs.getLong("remaining_frozen_fen"),
                projectId, applicationId, applicantId
        ).stream().findFirst();
    }

    public int cancelOwnApplication(
            long projectId, long applicationId, long applicantId, String reason) {
        String sql = """
        UPDATE funding_application a
        JOIN project_member m
          ON m.project_id = a.project_id
         AND m.user_id = a.applicant_id
        SET a.status = 'CANCELED',
            a.cancellation_reason = ?
        WHERE a.project_id = ?
          AND a.id = ?
          AND a.applicant_id = ?
          AND a.status IN ('PENDING_L1', 'APPROVED')
        """;

        return jdbcTemplate.update(sql, reason, projectId, applicationId, applicantId);
    }

    public int settleApprovedApplication(
            long projectId, long applicationId, long financeUserId, long settledFen) {
        String sql = """
        UPDATE funding_application a
        JOIN project_role_assignment r
          ON r.project_id = a.project_id
         AND r.user_id = ?
         AND r.role_code = 'FINANCE'
        SET a.status = 'SETTLED',
            a.settled_fen = ?
        WHERE a.project_id = ?
          AND a.id = ?
          AND a.status = 'APPROVED'
        """;

        return jdbcTemplate.update(sql, financeUserId, settledFen, projectId, applicationId);
    }

    public int approvePendingApplication(
            long projectId, long applicationId, long teacherId, Long approvedFen) {
        String sql = """
            UPDATE funding_application a
            JOIN project_role_assignment r
              ON r.project_id = a.project_id
             AND r.user_id = ?
             AND r.role_code = 'TEACHER'
            SET a.status = 'APPROVED',
                a.approved_fen = COALESCE(?, a.requested_fen)
            WHERE a.project_id = ?
              AND a.id = ?
              AND a.status = 'PENDING_L1'
              AND a.applicant_id <> ?
              AND COALESCE(?, a.requested_fen) BETWEEN 1 AND a.requested_fen
            """;

        return jdbcTemplate.update(
                sql, teacherId, approvedFen, projectId, applicationId, teacherId, approvedFen
        );
    }

    public int rejectPendingApplication(
            long projectId, long applicationId, long teacherId, String reason) {
        String sql = """
        UPDATE funding_application a
        JOIN project_role_assignment r
          ON r.project_id = a.project_id
         AND r.user_id = ?
         AND r.role_code = 'TEACHER'
        SET a.status = 'REJECTED',
            a.rejection_reason = ?
        WHERE a.project_id = ?
          AND a.id = ?
          AND a.status = 'PENDING_L1'
          AND a.applicant_id <> ?
        """;

        return jdbcTemplate.update(
                sql, teacherId, reason, projectId, applicationId, teacherId
        );
    }

    public record ApplicationSummary(
            long applicationId, long applicantId, String purpose,
            long requestedFen, String status
    ) {}

    public List<ApplicationSummary> findSubmittedApplicationsByProject(long projectId) {
        String sql = """
                SELECT id, applicant_id, purpose, requested_fen, status
                FROM funding_application
                WHERE project_id = ?
                  AND status <> 'DRAFT'
                ORDER BY id
                """;

        return jdbcTemplate.query(sql, this::mapApplicationSummary, projectId);
    }
    public List<ApplicationSummary> findPendingApplicationsForTeacher(
            long projectId, long currentUserId) {
        String sql = """
            SELECT id, applicant_id, purpose, requested_fen, status
            FROM funding_application
            WHERE project_id = ?
              AND status = 'PENDING_L1'
              AND applicant_id <> ?
            ORDER BY id
            """;

        return jdbcTemplate.query(
                sql, this::mapApplicationSummary, projectId, currentUserId
        );
    }

    private ApplicationSummary mapApplicationSummary(ResultSet rs, int rowNum)
            throws SQLException {
        return new ApplicationSummary(
                rs.getLong("id"),
                rs.getLong("applicant_id"),
                rs.getString("purpose"),
                rs.getLong("requested_fen"),
                rs.getString("status")
        );
    }
}