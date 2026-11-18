package com.example.funding;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("db-check")
public class DatabaseReadCheck implements CommandLineRunner {
    private final JdbcTemplate jdbcTemplate;

    public DatabaseReadCheck(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        String password = System.getenv("FUNDING_TEST_USER_PASSWORD");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("请先设置测试用户密码环境变量");
        }

        String name = jdbcTemplate.queryForObject(
                "SELECT name FROM research_project WHERE id = 1",
                String.class
        );
        System.out.println("数据库项目名称：" + name);

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM app_user " +
                        "WHERE id = 1 AND username = 'student_demo'",
                String.class
        );

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        boolean matched = encoder.matches(password, storedHash);
        System.out.println("用户密码匹配：" + matched);
    }
}