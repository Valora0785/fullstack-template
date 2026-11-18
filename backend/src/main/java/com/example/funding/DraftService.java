package com.example.funding;

import org.springframework.stereotype.Service;

@Service
public class DraftService {
    private final DraftRepository draftRepository;

    public DraftService(DraftRepository draftRepository) {
        this.draftRepository = draftRepository;
    }

    public int saveDraft(long projectId, long currentUserId,
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

        if (!draftRepository.isProjectMember(projectId, currentUserId)) {
            throw new IllegalStateException("当前用户未加入该项目");
        }

        return draftRepository.insertDraft(
                projectId, currentUserId, purpose, requestedFen
        );
    }
}