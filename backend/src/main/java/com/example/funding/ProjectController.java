package com.example.funding;

import com.example.funding.ProjectRepository.ProjectBudget;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.example.funding.DraftRepository.ApplicationSummary;
import java.util.List;

@RestController
public class ProjectController {
    private final ProjectRepository projectRepository;
    private final DraftRepository draftRepository;

    public ProjectController(
            ProjectRepository projectRepository, DraftRepository draftRepository) {
        this.projectRepository = projectRepository;
        this.draftRepository = draftRepository;
    }

    @GetMapping("/api/projects/{projectId}/budget")
    public ResponseEntity<ProjectBudget> getBudget(
            @PathVariable("projectId") long projectId,
            HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(401).build();
        }

        Long currentUserId = (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        var budget = projectRepository.findBudgetForMember(projectId, currentUserId);
        if (budget.isEmpty()) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(budget.get());
    }
    @GetMapping("/api/projects/{projectId}/applications/pending")
    public ResponseEntity<List<ApplicationSummary>> getPendingApplications(
            @PathVariable("projectId") long projectId,
            HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(401).build();
        }

        Long currentUserId = (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        if (!projectRepository.hasProjectRole(projectId, currentUserId, "TEACHER")) {
            return ResponseEntity.status(403).build();
        }

        var applications = draftRepository.findPendingApplicationsForTeacher(
                projectId, currentUserId
        );
        return ResponseEntity.ok(applications);
    }
}