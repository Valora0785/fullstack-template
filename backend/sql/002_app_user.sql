-- 阶段性手工建表脚本，不是自动迁移。
-- 在 funding 数据库中执行一次；已创建 app_user 时不要重复运行。
-- 仅建立用户表，不初始化账号，不代表登录或权限功能已实现。
-- password_hash 用于后续保存安全密码哈希，不应写入明文密码。
USE funding;

CREATE TABLE app_user (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    CONSTRAINT uk_app_user_username UNIQUE (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
