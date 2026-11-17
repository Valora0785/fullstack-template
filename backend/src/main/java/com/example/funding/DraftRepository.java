package com.example.funding;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

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
}