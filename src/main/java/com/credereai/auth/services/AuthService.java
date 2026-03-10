package com.credereai.auth.services;

import com.credereai.auth.models.AuthModels.CurrentUserResponse;
import com.credereai.auth.models.AuthModels.LoginResponse;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AuthService {

    private static final String PASSWORD_SALT = "credere-bank-salt-v1";
    private static final Duration SESSION_TTL = Duration.ofHours(12);

    private final Map<String, UserRecord> users = new ConcurrentHashMap<>();
    private final Map<String, SessionRecord> sessions = new ConcurrentHashMap<>();

    public AuthService() {
        // Demo bank users for hackathon and local deployment.
        registerUser("bank.admin", "Credere@123", "Bank Administrator", "CREDIT_ADMIN");
        registerUser("credit.officer", "Officer@123", "Credit Officer", "UNDERWRITER");
    }

    public Optional<LoginResponse> login(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }

        UserRecord user = users.get(username.trim().toLowerCase());
        if (user == null) {
            return Optional.empty();
        }

        String incomingHash = hashPassword(password);
        if (!incomingHash.equals(user.getPasswordHash())) {
            return Optional.empty();
        }

        LocalDateTime now = LocalDateTime.now();
        String token = UUID.randomUUID().toString();
        SessionRecord session = SessionRecord.builder()
                .token(token)
                .username(user.getUsername())
                .createdAt(now)
                .expiresAt(now.plus(SESSION_TTL))
                .build();
        sessions.put(token, session);

        return Optional.of(LoginResponse.builder()
                .token(token)
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .loginAt(session.getCreatedAt())
                .expiresAt(session.getExpiresAt())
                .build());
    }

    public Optional<CurrentUserResponse> getCurrentUser(String token) {
        SessionRecord session = getValidSession(token).orElse(null);
        if (session == null) {
            return Optional.empty();
        }

        UserRecord user = users.get(session.getUsername());
        if (user == null) {
            sessions.remove(token);
            return Optional.empty();
        }

        return Optional.of(CurrentUserResponse.builder()
                .username(user.getUsername())
                .fullName(user.getFullName())
                .role(user.getRole())
                .sessionCreatedAt(session.getCreatedAt())
                .sessionExpiresAt(session.getExpiresAt())
                .build());
    }

    public Optional<String> resolveUsername(String token) {
        return getValidSession(token).map(SessionRecord::getUsername);
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.remove(token);
        }
    }

    private Optional<SessionRecord> getValidSession(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        SessionRecord session = sessions.get(token);
        if (session == null) {
            return Optional.empty();
        }

        if (LocalDateTime.now().isAfter(session.getExpiresAt())) {
            sessions.remove(token);
            return Optional.empty();
        }

        return Optional.of(session);
    }

    private void registerUser(String username, String password, String fullName, String role) {
        users.put(username.toLowerCase(), UserRecord.builder()
                .username(username.toLowerCase())
                .passwordHash(hashPassword(password))
                .fullName(fullName)
                .role(role)
                .build());
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((PASSWORD_SALT + password).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash password", e);
        }
    }

    @Data
    @Builder
    private static class UserRecord {
        private String username;
        private String passwordHash;
        private String fullName;
        private String role;
    }

    @Data
    @Builder
    private static class SessionRecord {
        private String token;
        private String username;
        private LocalDateTime createdAt;
        private LocalDateTime expiresAt;
    }
}
