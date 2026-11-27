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
import com.example.funding.DraftRepository.OwnApplication;
import com.example.funding.DraftRepository.PendingSettlement;
import com.example.funding.ProjectRepository.ProjectSummary;

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

    @GetMapping("/api/projects/me")
    public ResponseEntity<List<ProjectSummary>> getMyProjects(
            HttpServletRequest request) {
        var session = request.getSession(false);
        Long currentUserId =
                session == null ? null : (Long) session.getAttribute("userId");

        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok(
                projectRepository.findProjectsForMember(currentUserId)
        );
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

        boolean canReadMembers =
                projectRepository.hasProjectRole(projectId, currentUserId, "ADMIN")
                        || projectRepository.hasProjectRole(projectId, currentUserId, "TEACHER")
                        || projectRepository.hasProjectRole(projectId, currentUserId, "FINANCE");

        if (!canReadMembers) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(projectRepository.findProjectMembers(projectId));
    }

    @GetMapping("/api/projects/{projectId}/applications")
    public ResponseEntity<List<ApplicationSummary>> getSubmittedApplications(
            @PathVariable("projectId") long projectId,
            HttpServletRequest request) {
        var session = request.getSession(false);
        Long currentUserId = session == null ? null : (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        boolean canReadApplications =
                projectRepository.hasProjectRole(projectId, currentUserId, "TEACHER")
                        || projectRepository.hasProjectRole(projectId, currentUserId, "FINANCE");
        if (!canReadApplications) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(
                draftRepository.findSubmittedApplicationsByProject(projectId)
        );
    }

    @GetMapping("/api/projects/{projectId}/applications/me")
    public ResponseEntity<List<OwnApplication>> getOwnApplications(
            @PathVariable("projectId") long projectId,
            HttpServletRequest request) {
        var session = request.getSession(false);
        Long currentUserId = session == null ? null : (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        if (!draftRepository.isProjectMember(projectId, currentUserId)) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(
                draftRepository.findOwnApplications(projectId, currentUserId)
        );
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

    @GetMapping("/api/projects/{projectId}/applications/pending-settlement")
    public ResponseEntity<List<PendingSettlement>> getPendingSettlements(
            @PathVariable("projectId") long projectId,
            HttpServletRequest request) {
        var session = request.getSession(false);
        Long currentUserId = session == null ? null : (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        if (!projectRepository.hasProjectRole(projectId, currentUserId, "FINANCE")) {
            return ResponseEntity.status(403).build();
        }

        return ResponseEntity.ok(
                draftRepository.findPendingSettlementsByProject(projectId)
        );
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

    public record AssignProjectRoleRequest(String roleCode) {}

    @PostMapping("/api/projects/{projectId}/members/{memberUserId}/roles")
    public ResponseEntity<Void> assignProjectRole(
            @PathVariable("projectId") long projectId,
            @PathVariable("memberUserId") long memberUserId,
            @RequestBody AssignProjectRoleRequest body,
            HttpServletRequest request) {
        var session = request.getSession(false);
        Long currentUserId = session == null ? null : (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        if (!projectRepository.hasProjectRole(projectId, currentUserId, "ADMIN")) {
            return ResponseEntity.status(403).build();
        }

        String roleCode = body.roleCode();
        if (memberUserId <= 0
                || (!"TEACHER".equals(roleCode) && !"FINANCE".equals(roleCode))) {
            return ResponseEntity.badRequest().build();
        }

        try {
            int insertedRows = projectRepository.assignProjectRole(projectId, memberUserId, roleCode);
            if (insertedRows == 0) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.status(201).build();
        } catch (DuplicateKeyException e) {
            return ResponseEntity.status(409).build();
        }
    }

    public record ApproveApplicationRequest(Long approvedFen) {}
    public record SettleApplicationRequest(Long settledFen) {}

    @PostMapping("/api/projects/{projectId}/applications/{applicationId}/approve")
    public ResponseEntity<Void> approveApplication(
            @PathVariable("projectId") long projectId,
            @PathVariable("applicationId") long applicationId,
            @RequestBody(required = false) ApproveApplicationRequest body,
            HttpServletRequest request) {
        var session = request.getSession(false);
        Long currentUserId = session == null ? null : (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        DraftService.ApprovalResult result;
        if (body != null && body.approvedFen() == null) {
            return ResponseEntity.badRequest().build();
        }
        Long approvedFen = body == null ? null : body.approvedFen();
        try {
            result = draftService.approveApplication(
                    projectId, applicationId, currentUserId, approvedFen
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        return switch (result) {
            case APPROVED -> ResponseEntity.noContent().build();
            case FORBIDDEN -> ResponseEntity.status(403).build();
            case APPLICATION_NOT_FOUND -> ResponseEntity.notFound().build();
        };
    }

    public record RejectApplicationRequest(String reason) {}

    @PostMapping("/api/projects/{projectId}/applications/{applicationId}/reject")
    public ResponseEntity<Void> rejectApplication(
            @PathVariable("projectId") long projectId,
            @PathVariable("applicationId") long applicationId,
            @RequestBody(required = false) RejectApplicationRequest body,
            HttpServletRequest request) {
        var session = request.getSession(false);
        Long currentUserId = session == null ? null : (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }


        DraftService.RejectionResult result;
        try {
            result = draftService.rejectApplication(
                    projectId, applicationId, currentUserId, body == null ? null : body.reason()
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        return switch (result) {
            case REJECTED -> ResponseEntity.noContent().build();
            case FORBIDDEN -> ResponseEntity.status(403).build();
            case APPLICATION_NOT_FOUND -> ResponseEntity.notFound().build();
        };
    }

    public record CancelApplicationRequest(String reason) {}

    @PostMapping("/api/projects/{projectId}/applications/{applicationId}/cancel")
    public ResponseEntity<Void> cancelOwnApplication(
            @PathVariable("projectId") long projectId,
            @PathVariable("applicationId") long applicationId,
            @RequestBody(required = false) CancelApplicationRequest body,
            HttpServletRequest request) {
        var session = request.getSession(false);
        Long currentUserId = session == null ? null : (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        try {
            String reason = body == null ? null : body.reason();
            var result = draftService.cancelOwnApplication(
                    projectId, applicationId, currentUserId, reason
            );

            return switch (result) {
                case CANCELED -> ResponseEntity.noContent().build();
                case FORBIDDEN -> ResponseEntity.status(403).build();
                case APPLICATION_NOT_FOUND -> ResponseEntity.notFound().build();
            };
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/api/projects/{projectId}/applications/{applicationId}/settle")
    public ResponseEntity<Void> settleApplication(
            @PathVariable("projectId") long projectId,
            @PathVariable("applicationId") long applicationId,
            @RequestBody(required = false) SettleApplicationRequest body,
            HttpServletRequest request) {
        var session = request.getSession(false);
        Long currentUserId = session == null ? null : (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        if (body == null || body.settledFen() == null) {
            return ResponseEntity.badRequest().build();
        }

        DraftService.SettlementResult result;
        try {
            result = draftService.settleApplication(
                    projectId, applicationId, currentUserId, body.settledFen()
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        return switch (result) {
            case SETTLED -> ResponseEntity.noContent().build();
            case FORBIDDEN -> ResponseEntity.status(403).build();
            case APPLICATION_NOT_FOUND -> ResponseEntity.notFound().build();
        };
    }

}