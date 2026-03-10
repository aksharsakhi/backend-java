package com.credereai.module1.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class PolicyModels {

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskPolicyRules {
        private Double minConfidence;
        private Double minCompletenessScore;
        private Double gstDeviationHigh;
        private Double gstDeviationMedium;
        private Integer approveScore;
        private Integer approveWithConditionsScore;
        private Integer referManualScore;
        private Double baseExposureRatio;
        private Double mediumRiskExposureMultiplier;
        private Double elevatedRiskExposureMultiplier;
        private Double lowSpreadBps;
        private Double mediumSpreadBps;
        private Double elevatedSpreadBps;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskPolicyVersion {
        private String versionId;
        private String ruleId;
        private String description;
        private LocalDateTime createdAt;
        private RiskPolicyRules rules;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RiskPolicyStore {
        private String activeVersionId;
        private List<String> rollbackStack;
        private Map<String, RiskPolicyVersion> versions;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpsertPolicyRequest {
        private String versionId;
        private String ruleId;
        private String description;
        private RiskPolicyRules rules;
        private Boolean activate;
    }
}
