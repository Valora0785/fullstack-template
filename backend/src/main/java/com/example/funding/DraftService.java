package com.example.funding;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DraftService {
    private final DraftRepository draftRepository;
    private final ProjectRepository projectRepository;

    public DraftService(DraftRepository draftRepository, ProjectRepository projectRepository) {
        this.draftRepository = draftRepository;
        this.projectRepository = projectRepository;
    }

    public int saveDraft(long projectId, long currentUserId,
                         String purpose, long requestedFen) {
        validateDraftFields(projectId, currentUserId, purpose, requestedFen);

        if (!draftRepository.isProjectMember(projectId, currentUserId)) {
            throw new ProjectMembershipRequiredException();
        }

        return draftRepository.insertDraft(
                projectId, currentUserId, purpose, requestedFen
        );
    }

    public int updateOwnDraft(long projectId, long draftId, long currentUserId,
                              String purpose, long requestedFen) {
        validateDraftFields(projectId, currentUserId, purpose, requestedFen);

        if (draftId <= 0) {
            throw new IllegalArgumentException("草稿编号必须为正数");
        }

        return draftRepository.updateOwnDraft(
                projectId, draftId, currentUserId, purpose, requestedFen
        );
    }

    public enum SubmitResult {
        SUBMITTED,
        DRAFT_NOT_FOUND,
        INSUFFICIENT_BUDGET,
        NO_ELIGIBLE_APPROVER
    }

    @Transactional
    public SubmitResult submitOwnDraft(long projectId, long draftId, long currentUserId) {
        if (projectId <= 0 || draftId <= 0 || currentUserId <= 0) {
            throw new IllegalArgumentException("项目、草稿和用户编号必须为正数");
        }

        var lockedDraft = draftRepository.findOwnDraftForUpdate(projectId, draftId, currentUserId);
        if (lockedDraft.isEmpty()) {
            return SubmitResult.DRAFT_NOT_FOUND;
        }

        var draft = lockedDraft.get();
        if (draft.purpose().isBlank() || draft.requestedFen() <= 0) {
            throw new IllegalArgumentException("提交时用途不能为空，申请金额必须大于 0");
        }

        if (!projectRepository.hasEligibleApprover(projectId, currentUserId)) {
            return SubmitResult.NO_ELIGIBLE_APPROVER;
        }

        int frozenRows = projectRepository.freezeBudget(projectId, draft.requestedFen());
        if (frozenRows == 0) {
            return SubmitResult.INSUFFICIENT_BUDGET;
        }

        int updatedRows = draftRepository.markOwnDraftPending(projectId, draftId, currentUserId);
        if (updatedRows != 1) {
            throw new IllegalStateException("提交草稿时预期更新一行");
        }

        return SubmitResult.SUBMITTED;
    }

    private void validateDraftFields(long projectId, long currentUserId,
                                     String purpose, long requestedFen) {
        if (projectId <= 0 || currentUserId <= 0) {
            throw new IllegalArgumentException("项目和用户编号必须为正数");
        }

        if (purpose == null) {
            throw new IllegalArgumentException("草稿用途不能为null，可以为空字符串");
        }

        if (purpose.codePointCount(0, purpose.length()) > 200) {
            throw new IllegalArgumentException("用途不能超过200个字符");
        }

        if (requestedFen < 0) {
            throw new IllegalArgumentException("草稿金额不能为负数");
        }
    }

    public enum ApprovalResult {
        APPROVED,
        FORBIDDEN,
        APPLICATION_NOT_FOUND
    }

    public ApprovalResult approveApplication(long projectId, long applicationId, long teacherId) {
        if (projectId <= 0 || applicationId <= 0 || teacherId <= 0) {
            throw new IllegalArgumentException("项目、申请和教师编号必须为正数");
        }

        if (!projectRepository.hasProjectRole(projectId, teacherId, "TEACHER")) {
            return ApprovalResult.FORBIDDEN;
        }

        int updatedRows = draftRepository.approvePendingApplication(
                projectId, applicationId, teacherId
        );
        if (updatedRows == 0) {
            return ApprovalResult.APPLICATION_NOT_FOUND;
        }

        return ApprovalResult.APPROVED;
    }
}