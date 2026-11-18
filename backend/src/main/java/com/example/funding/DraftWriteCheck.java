package com.example.funding;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

@Component
@Profile("draft-write-check")
public class DraftWriteCheck implements CommandLineRunner {
    private final DraftService draftService;

    public DraftWriteCheck(DraftService draftService) {
        this.draftService = draftService;
    }

    @Override
    @Transactional
    public void run(String... args) {
        int rows = draftService.saveDraft(
                1L, 1L, "Java草稿回滚检查", 60000L
        );

        System.out.println("新增草稿行数：" + rows);

        TransactionAspectSupport.currentTransactionStatus()
                .setRollbackOnly();

        System.out.println("本次检查已标记回滚");
    }
}