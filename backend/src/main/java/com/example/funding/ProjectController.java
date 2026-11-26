package com.example.funding;

import com.example.funding.ProjectRepository.ProjectBudget;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import com.example.funding.DraftRepository.ApplicationSummary;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import com.example.funding.ProjectRepository.ProjectMember;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
public class ProjectController {
    private final ProjectRepository projectRepository;
    private final DraftRepository draftRepository;
    private final DraftService draftService;

    public ProjectController(
            ProjectRepository projectRepository,
            DraftRepository draftRepository,
            DraftService draftService) {
        this.projectRepository = projectRepository;
        this.draftRepository = draftRepository;
        this.draftService = draftService;
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

    @GetMapping("/api/projects/{projectId}/members")
    public ResponseEntity<List<ProjectMember>> getProjectMembers(
            @PathVariable("projectId") long projectId,
            HttpServletRequest request) {
        var session = request.getSession(false);
        Long currentUserId = session == null ? null : (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        if (!projectRepository.hasProjectRole(projectId, currentUserId, "ADMIN")) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(projectRepository.findProjectMembers(projectId));
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

    public record AddProjectMemberRequest(Long userId) {}

    @PostMapping("/api/projects/{projectId}/members")
    public ResponseEntity<Void> addProjectMember(
            @PathVariable("projectId") long projectId,
            @RequestBody AddProjectMemberRequest body,
            HttpServletRequest request) {
        var session = request.getSession(false);
        Long currentUserId = session == null ? null : (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        if (!projectRepository.hasProjectRole(projectId, currentUserId, "ADMIN")) {
            return ResponseEntity.status(403).build();
        }

        Long memberUserId = body.userId();
        if (memberUserId == null || memberUserId <= 0) {
            return ResponseEntity.badRequest().build();
        }

        try {
            int insertedRows = projectRepository.addProjectMember(projectId, memberUserId);
            if (insertedRows == 0) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(201).build();
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(409).build();
        }
    }

    @PostMapping("/api/projects/{projectId}/applications/{applicationId}/approve")
    public ResponseEntity<Void> approveApplication(
            @PathVariable("projectId") long projectId,
            @PathVariable("applicationId") long applicationId,
            HttpServletRequest request) {
        var session = request.getSession(false);
        Long currentUserId = session == null ? null : (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        DraftService.ApprovalResult result;
        try {
            result = draftService.approveApplication(projectId, applicationId, currentUserId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        return switch (result) {
            case APPROVED -> ResponseEntity.noContent().build();
            case FORBIDDEN -> ResponseEntity.status(403).build();
            case APPLICATION_NOT_FOUND -> ResponseEntity.notFound().build();
        };
    }
}