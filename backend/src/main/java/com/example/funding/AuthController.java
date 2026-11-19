package com.example.funding;

import org.springframework.web.bind.annotation.RestController;
import java.util.OptionalLong;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.CacheControl;

@RestController
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }
    public record LoginRequest(String username, String password) {}
    private OptionalLong authenticateLogin(LoginRequest loginRequest) {
        return authService.authenticate(loginRequest.username(), loginRequest.password());
    }
    @PostMapping("/api/auth/login")
    public ResponseEntity<Void> login(
            @RequestBody LoginRequest loginRequest,
            HttpServletRequest request) {
        OptionalLong userId = authenticateLogin(loginRequest);

        if (userId.isEmpty()) {
            return ResponseEntity.status(401).build();
        }

        var session = request.getSession();
        request.changeSessionId();
        session.setAttribute("userId", userId.getAsLong());
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/api/auth/me")
    public ResponseEntity<Map<String, Long>> me(HttpServletRequest request) {
        var session = request.getSession(false);
        if (session == null) {
            return ResponseEntity.status(401).build();
        }

        Long currentUserId = (Long) session.getAttribute("userId");
        if (currentUserId == null) {
            return ResponseEntity.status(401).build();
        }

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of("userId", currentUserId));
    }
    @PostMapping("/api/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        var session =request.getSession(false);
        if (session != null){
            session.invalidate();
        }
        return ResponseEntity.noContent().build();
    }


}