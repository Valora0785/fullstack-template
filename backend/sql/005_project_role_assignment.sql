USE funding;

CREATE TABLE project_role_assignment (
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role_code VARCHAR(20) NOT NULL,
    PRIMARY KEY (project_id, user_id, role_code),
    CONSTRAINT fk_role_assignment_project
        FOREIGN KEY (project_id) REFERENCES research_project(id),
    CONSTRAINT fk_role_assignment_user
        FOREIGN KEY (user_id) REFERENCES app_user(id),
    CONSTRAINT chk_role_assignment_code
        CHECK (role_code IN ('TEACHER', 'FINANCE', 'ADMIN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;