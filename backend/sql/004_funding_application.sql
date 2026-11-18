-- 阶段性手工脚本，不是自动迁移；先创建research_project和app_user。
-- 当前数据库已有本表，不要重复运行。合并版本尚未整体重放验证。
-- 尚缺原因与办理记录；权限、状态流转、历史保护、资金联动及事务待后端实现。
-- TRIM只覆盖普通空格。
USE funding;

CREATE TABLE funding_application (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    applicant_id BIGINT NOT NULL,
    purpose VARCHAR(200) NOT NULL,
    requested_fen BIGINT NOT NULL,
    approved_fen BIGINT NULL,
    settled_fen BIGINT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    CONSTRAINT fk_application_project
        FOREIGN KEY (project_id) REFERENCES research_project(id),
    CONSTRAINT fk_application_applicant
        FOREIGN KEY (applicant_id) REFERENCES app_user(id),
    CONSTRAINT chk_application_status CHECK (
        status IN ('DRAFT', 'PENDING_L1', 'APPROVED', 'REJECTED', 'CANCELED', 'SETTLED')
    ),
    CONSTRAINT chk_application_approved_amount CHECK (
        approved_fen IS NULL OR (approved_fen > 0 AND approved_fen <= requested_fen)
    ),
    CONSTRAINT chk_application_settled_amount CHECK (
        settled_fen IS NULL OR (
            approved_fen IS NOT NULL
            AND settled_fen >= 0 AND settled_fen <= approved_fen
        )
    ),
    CONSTRAINT chk_application_settlement_state CHECK (
        (status = 'SETTLED' AND settled_fen IS NOT NULL)
        OR (status <> 'SETTLED' AND settled_fen IS NULL)
    ),
    CONSTRAINT chk_application_approval_state CHECK (
        (status IN ('DRAFT', 'PENDING_L1', 'REJECTED') AND approved_fen IS NULL)
        OR (status IN ('APPROVED', 'SETTLED') AND approved_fen IS NOT NULL)
        OR status = 'CANCELED'
    ),
    CONSTRAINT chk_application_requested_amount CHECK (requested_fen >= 0),
    CONSTRAINT chk_application_submission_fields CHECK (
        status = 'DRAFT'
        OR (requested_fen > 0 AND CHAR_LENGTH(TRIM(purpose)) > 0)
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
