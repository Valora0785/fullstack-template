package com.example.funding;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
public class DraftController {
    private final DraftService draftService;

    public DraftController(DraftService draftService) {
        this.draftService = draftService;
    }

    public record DraftRequest(String purpose, Long requestedFen) {}

    @PostMapping("/api/projects/{projectId}/drafts")
    public ResponseEntity<Void> createDraft(
            @PathVariable("projectId") long projectId,
            HttpServletRequest request,
            @RequestBody DraftRequest draft) {
        var session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(401).build();
        }

        Long currentUserId = (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        if (draft.requestedFen() == null) {
            return ResponseEntity.badRequest().build();
        }

        int insertedRows;
        try {
            insertedRows = draftService.saveDraft(
                    projectId, currentUserId, draft.purpose(), draft.requestedFen()
            );
        } catch (ProjectMembershipRequiredException e) {
            return ResponseEntity.status(403).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        if (insertedRows != 1) {
            throw new IllegalStateException("创建草稿时预期写入一行");
        }

        return ResponseEntity.status(201).build();
    }
    @PutMapping("/api/projects/{projectId}/drafts/{draftId}")
    public ResponseEntity<Void> updateDraft(
            @PathVariable("projectId") long projectId,
            @PathVariable("draftId") long draftId,
            HttpServletRequest request,
            @RequestBody DraftRequest draft) {
        var session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(401).build();
        }

        Long currentUserId = (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        if (draft.requestedFen() == null) {
            return ResponseEntity.badRequest().build();
        }

        int updatedRows;
        try {
            updatedRows = draftService.updateOwnDraft(
                    projectId, draftId, currentUserId, draft.purpose(), draft.requestedFen()
            );
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        if (updatedRows == 0) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.noContent().build();
    }

    public record SubmitError(String code) {}

    @PostMapping("/api/projects/{projectId}/drafts/{draftId}/submit")
    public ResponseEntity<SubmitError> submitDraft(
            @PathVariable("projectId") long projectId,
            @PathVariable("draftId") long draftId,
            HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(401).build();
        }

        Long currentUserId = (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        DraftService.SubmitResult result;
        try {
            result = draftService.submitOwnDraft(projectId, draftId, currentUserId);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        return switch (result) {
            case SUBMITTED -> ResponseEntity.noContent().build();
            case DRAFT_NOT_FOUND -> ResponseEntity.notFound().build();
            case INSUFFICIENT_BUDGET -> ResponseEntity.status(409)
                    .body(new SubmitError("INSUFFICIENT_BUDGET"));
            case NO_ELIGIBLE_APPROVER -> ResponseEntity.status(409)
                    .body(new SubmitError("NO_ELIGIBLE_APPROVER"));
        };
    }
}