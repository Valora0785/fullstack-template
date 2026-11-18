-- 阶段性手工建表脚本，不是自动迁移。
-- 用于尚未创建 research_project 的 funding 数据库；按顺序执行一次。
-- 当前学习数据库已经执行过这些建表和初始化语句，不要重复运行。
-- 仅包含项目预算表；用户、项目成员和申请等关系尚未实现。
USE funding;

CREATE TABLE research_project (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL,
    total_budget_fen BIGINT NOT NULL,
    available_fen BIGINT NOT NULL,
    frozen_fen BIGINT NOT NULL DEFAULT 0,
    used_fen BIGINT NOT NULL DEFAULT 0,

    CONSTRAINT chk_project_nonnegative CHECK (
        total_budget_fen >= 0
        AND available_fen >= 0
        AND frozen_fen >= 0
        AND used_fen >= 0
    ),
    CONSTRAINT chk_project_balance CHECK (
        total_budget_fen = available_fen + frozen_fen + used_fen
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 学习用初始项目：1000元 = 100000分。
INSERT INTO research_project (
    name, total_budget_fen, available_fen
)
VALUES ('传感器研究项目', 100000, 100000);

SELECT id, name, total_budget_fen,
       available_fen, frozen_fen, used_fen
FROM research_project
WHERE id = LAST_INSERT_ID();
