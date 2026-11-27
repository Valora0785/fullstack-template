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

    @Transactional
    public ApprovalResult approveApplication(long projectId, long applicationId, long teacherId) {
        return approveApplication(projectId, applicationId, teacherId, null);
    }

    @Transactional
    public ApprovalResult approveApplication(
            long projectId, long applicationId, long teacherId, Long approvedFen) {
        if (projectId <= 0 || applicationId <= 0 || teacherId <= 0) {
            throw new IllegalArgumentException("项目、申请和教师编号必须为正数");
        }

        if (!projectRepository.hasProjectRole(projectId, teacherId, "TEACHER")) {
            return ApprovalResult.FORBIDDEN;
        }

        var requestedAmount = draftRepository.findPendingRequestedFenForUpdate(
                projectId, applicationId, teacherId
        );
        if (requestedAmount.isEmpty()) {
            return ApprovalResult.APPLICATION_NOT_FOUND;
        }

        long requestedFen = requestedAmount.get();
        long effectiveApprovedFen = approvedFen == null ? requestedFen : approvedFen;
        if (effectiveApprovedFen <= 0 || effectiveApprovedFen > requestedFen) {
            throw new IllegalArgumentException("批准金额必须大于 0 且不能超过申请金额");
        }

        int updatedRows = draftRepository.approvePendingApplication(
                projectId, applicationId, teacherId, effectiveApprovedFen
        );
        if (updatedRows == 0) {
            return ApprovalResult.APPLICATION_NOT_FOUND;
        }

        long releaseFen = requestedFen - effectiveApprovedFen;
        if (releaseFen > 0 && projectRepository.releaseFrozenBudget(projectId, releaseFen) != 1) {
            throw new IllegalStateException("退回冻结预算失败");
        }

        return ApprovalResult.APPROVED;
    }

    public enum RejectionResult {
        REJECTED,
        FORBIDDEN,
        APPLICATION_NOT_FOUND
    }

    @Transactional
    public RejectionResult rejectApplication(
            long projectId, long applicationId, long teacherId, String reason) {
        if (projectId <= 0 || applicationId <= 0 || teacherId <= 0) {
            throw new IllegalArgumentException("项目、申请和教师编号必须为正数");
        }

        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("拒绝原因不能为空");
        }

        String normalizedReason = reason.strip();
        if (normalizedReason.codePointCount(0, normalizedReason.length()) > 200) {
            throw new IllegalArgumentException("拒绝原因不能超过 200 字符");
        }

        if (!projectRepository.hasProjectRole(projectId, teacherId, "TEACHER")) {
            return RejectionResult.FORBIDDEN;
        }

        var requestedAmount = draftRepository.findPendingRequestedFenForUpdate(
                projectId, applicationId, teacherId
        );
        if (requestedAmount.isEmpty()) {
            return RejectionResult.APPLICATION_NOT_FOUND;
        }

        int updatedRows = draftRepository.rejectPendingApplication(
                projectId, applicationId, teacherId, normalizedReason
        );
        if (updatedRows == 0) {
            return RejectionResult.APPLICATION_NOT_FOUND;
        }

        if (projectRepository.releaseFrozenBudget(projectId, requestedAmount.get()) != 1) {
            throw new IllegalStateException("退回冻结预算失败");
        }

        return RejectionResult.REJECTED;
    }

    public enum SettlementResult {
        SETTLED,
        FORBIDDEN,
        APPLICATION_NOT_FOUND
    }

    @Transactional
    public SettlementResult settleApplication(
            long projectId, long applicationId, long financeUserId, long settledFen) {
        if (projectId <= 0 || applicationId <= 0 || financeUserId <= 0 || settledFen < 0) {
            throw new IllegalArgumentException("编号必须为正数，结算金额不能为负数");
        }

        if (!projectRepository.hasProjectRole(projectId, financeUserId, "FINANCE")) {
            return SettlementResult.FORBIDDEN;
        }

        var approvedAmount = draftRepository.findApprovedFenForUpdate(projectId, applicationId);
        if (approvedAmount.isEmpty()) {
            return SettlementResult.APPLICATION_NOT_FOUND;
        }

        long approvedFen = approvedAmount.get();
        if (settledFen > approvedFen) {
            throw new IllegalArgumentException("结算金额不能超过批准金额");
        }

        int updatedRows = draftRepository.settleApprovedApplication(
                projectId, applicationId, financeUserId, settledFen
        );
        if (updatedRows == 0) {
            return SettlementResult.APPLICATION_NOT_FOUND;
        }


        if (projectRepository.settleFrozenBudget(projectId, approvedFen, settledFen) != 1) {
            throw new IllegalStateException("结算预算失败");
        }

        return SettlementResult.SETTLED;
    }

}