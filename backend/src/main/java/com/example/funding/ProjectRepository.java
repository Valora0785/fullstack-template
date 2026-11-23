package com.example.funding;

import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProjectRepository {
    private final JdbcTemplate jdbcTemplate;

    public ProjectRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record ProjectBudget(
            long projectId,
            String name,
            long totalBudgetFen,
            long availableFen,
            long frozenFen,
            long usedFen
    ) {}

    public Optional<ProjectBudget> findBudgetForMember(
            long projectId, long currentUserId) {
        String sql = """
                SELECT p.id, p.name, p.total_budget_fen,
                       p.available_fen, p.frozen_fen, p.used_fen
                FROM research_project p
                JOIN project_member m ON m.project_id = p.id
                WHERE m.project_id = ?
                  AND m.user_id = ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new ProjectBudget(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getLong("total_budget_fen"),
                        rs.getLong("available_fen"),
                        rs.getLong("frozen_fen"),
                        rs.getLong("used_fen")
                ),
                projectId,
                currentUserId
        ).stream().findFirst();
    }
    public boolean hasProjectRole(long projectId, long userId, String roleCode) {
        String sql = """
            SELECT COUNT(*)
            FROM project_role_assignment
            WHERE project_id = ?
              AND user_id = ?
              AND role_code = ?
            """;

        Long count = jdbcTemplate.queryForObject(
                sql, Long.class, projectId, userId, roleCode
        );
        return count != null && count > 0;
    }

}