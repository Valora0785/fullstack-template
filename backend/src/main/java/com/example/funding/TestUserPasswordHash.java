package com.example.funding;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class TestUserPasswordHash {
    public static void main(String[] args) {
        String password = System.getenv("FUNDING_TEST_USER_PASSWORD");

        if (password == null || password.isBlank()) {
            throw new IllegalStateException("请先设置测试用户密码环境变量");
        }

        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode(password);

        System.out.println("密码哈希：" + hash);
        System.out.println("匹配检查：" + encoder.matches(password, hash));
    }
}