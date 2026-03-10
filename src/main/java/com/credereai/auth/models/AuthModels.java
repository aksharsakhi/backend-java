package com.credereai.auth.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.Map;

public class AuthModels {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginRequest {
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 80, message = "Username length must be 3-80")
        private String username;

        @NotBlank(message = "Password is required")
        @Size(min = 6, max = 120, message = "Password length must be 6-120")
        private String password;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LoginResponse {
        private String token;
        private String username;
        private String fullName;
        private String role;
        private LocalDateTime loginAt;
        private LocalDateTime expiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CurrentUserResponse {
        private String username;
        private String fullName;
        private String role;
        private LocalDateTime sessionCreatedAt;
        private LocalDateTime sessionExpiresAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SaveHistoryRequest {
        private Map<String, Object> fullAnalysis;
        private Map<String, Object> dashboard;
        private Map<String, Object> underwriting;
        private Integer documentCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnalysisHistoryRecord {
        private String id;
        private String username;
        private String companyName;
        private String decision;
        private String riskBand;
        private Double completenessScore;
        private Double confidence;
        private Integer documentCount;
        private String summary;
        private LocalDateTime createdAt;
        private Map<String, Object> snapshot;
    }
}
