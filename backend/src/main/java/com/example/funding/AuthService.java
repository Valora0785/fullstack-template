package com.example.funding;

import java.util.List;
import java.util.OptionalLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final JdbcTemplate jdbcTemplate;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AuthService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public OptionalLong authenticate(String username, String password) {
            if (username == null || username.isBlank()
                    || password == null || password.isBlank()) {
                return OptionalLong.empty();
            }

        String sql = "SELECT id, password_hash FROM app_user WHERE username = ?";

        List<LoginAccount> accounts = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new LoginAccount(
                        rs.getLong("id"),
                        rs.getString("password_hash")
                ),
                username
        );
            if (accounts.isEmpty()) {
                return OptionalLong.empty();
            }

            LoginAccount account = accounts.get(0);
            if (!encoder.matches(password, account.passwordHash())) {
                return OptionalLong.empty();
            }

            return OptionalLong.of(account.id());


    }

    private record LoginAccount(long id, String passwordHash) {}
}