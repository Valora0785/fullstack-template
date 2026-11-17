package com.example.funding;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

public class PasswordHashPractice {
    public static void main(String[] args) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

        // 公开练习字符串，不作为实际项目账号的密码
        String demoPassword = "learning-only-123";
        String hash = encoder.encode(demoPassword);

        System.out.println("哈希：" + hash);
        System.out.println("正确密码匹配：" + encoder.matches(demoPassword, hash));
        System.out.println("错误密码匹配：" + encoder.matches("wrong-password", hash));
    }
}