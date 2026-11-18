-- 阶段性手工建表脚本，不是自动迁移。
-- 先创建 research_project 和 app_user；已创建本表时不要重复运行。
-- 仅记录普通成员关系，不代表后端权限检查或老师、财务权限已实现。
USE funding;

CREATE TABLE project_member (
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    PRIMARY KEY (project_id, user_id),
    CONSTRAINT fk_project_member_project
        FOREIGN KEY (project_id) REFERENCES research_project(id),
    CONSTRAINT fk_project_member_user
        FOREIGN KEY (user_id) REFERENCES app_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
