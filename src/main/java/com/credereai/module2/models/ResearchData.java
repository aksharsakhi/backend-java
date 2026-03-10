package com.credereai.module2.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

public class ResearchData {

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum AlertCategory {
        NETWORK, LITIGATION, REGULATORY, NEWS, PROMOTER
    }

    public enum EntityType {
        COMPANY, INDIVIDUAL, GOVERNMENT, OTHER
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ResearchInput {
        @NotBlank(message = "Company name is required")
        @Size(max = 120, message = "Company name max length is 120")
        private String companyName;

        @Size(max = 30, message = "CIN max length is 30")
        private String cin;

        @Size(max = 80, message = "Industry max length is 80")
        private String industry;

        @Size(max = 120, message = "Location max length is 120")
        private String location;
        private List<String> promoterNames;
        private List<String> directors;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NetworkNode {
        private String id;
        private String name;
        private String type; // company, individual, etc.
        private String relationship;
        private Double riskScore;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NetworkEdge {
        private String source;
        private String target;
        private String relationship;
        private Double weight;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NetworkGraph {
        private List<NetworkNode> nodes;
        private List<NetworkEdge> edges;
        private String summary;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RiskAlert {
        private AlertCategory category;
        private RiskLevel severity;
        private String title;
        private String description;
        private String source;
        private Double impact;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LitigationRecord {
        private String caseNumber;
        private String court;
        private String parties;
        private String status;
        private String nature;
        private String filingDate;
        private Double amount;
        private RiskLevel riskLevel;
        private String summary;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RegulatoryAction {
        private String authority;
        private String actionType;
        private String date;
        private String description;
        private Double penalty;
        private RiskLevel severity;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NewsSignal {
        private String title;
        private String source;
        private String date;
        private String sentiment; // positive, negative, neutral
        private Double relevanceScore;
        private String summary;
        private String url;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PromoterProfile {
        private String name;
        private String designation;
        private Double holdingPercent;
        private List<String> otherCompanies;
        private List<String> flags;
        private String backgroundSummary;
        private Double riskScore;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ExternalRiskScore {
        private Double compositeScore;
        private String grade; // A-F
        private Double networkScore;
        private Double litigationScore;
        private Double regulatoryScore;
        private Double newsScore;
        private Double promoterScore;
        private List<RiskAlert> alerts;
        private String summary;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ResearchReport {
        private ResearchInput input;
        private NetworkGraph networkGraph;
        private List<LitigationRecord> litigationRecords;
        private List<RegulatoryAction> regulatoryActions;
        private List<NewsSignal> newsSignals;
        private List<PromoterProfile> promoterProfiles;
        private ExternalRiskScore riskScore;
    }
}
