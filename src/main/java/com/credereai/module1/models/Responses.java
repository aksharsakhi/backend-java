package com.credereai.module1.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

public class Responses {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        @Builder.Default
        private T data = null;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ExtractionResponse {
        private String documentId;
        private String filename;
        private String category;
        private int pageCount;
        private int textLength;
        private FinancialData.ExtractedFinancialData extractedData;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TrendAnalysisResponse {
        private List<FinancialData.TrendAnalysis> trends;
        private String summary;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CrossVerificationResponse {
        private List<FinancialData.CrossVerificationAlert> alerts;
        private String summary;
        private int totalAlerts;
        private Map<String, Integer> riskBreakdown;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RatioAnalysisResponse {
        private FinancialData.FinancialRatiosReport ratios;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CompletenessResponse {
        private FinancialData.DataCompletenessReport completeness;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FullAnalysisResponse {
        private Map<String, Object> consolidatedData;
        private FinancialData.DataCompletenessReport completeness;
        private TrendAnalysisResponse trendAnalysis;
        private CrossVerificationResponse crossVerification;
        private RatioAnalysisResponse ratioAnalysis;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DashboardKpi {
        private String key;
        private String label;
        private Double value;
        private String unit;
        private String status;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DashboardTrendSeries {
        private String metric;
        private String trend;
        private Double growthRate;
        private List<FinancialData.TrendPoint> points;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DashboardResponse {
        private String companyName;
        private String financialYear;
        private List<DashboardKpi> kpis;
        private List<DashboardTrendSeries> trendSeries;
        private Map<String, Integer> completenessByPriority;
        private List<String> missingCriticalFields;
        private Map<String, Integer> riskBreakdown;
        private List<String> insights;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UnderwritingResponse {
        private String decision;
        private String riskBand;
        private Double recommendedExposureCr;
        private Double expectedPricingSpreadBps;
        private String policyVersionId;
        private String policyRuleId;
        private List<String> covenants;
        private List<String> rationale;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class StressScenarioResult {
        private String scenario;
        private Double projectedProfitCr;
        private Double projectedDebtToEquity;
        private String riskImpact;
        private String commentary;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class EnterpriseAssessmentResponse {
        private Integer internalRiskScore;
        private String rating;
        private Double probabilityOfDefault1Y;
        private Double expectedLossPct;
        private List<StressScenarioResult> stressScenarios;
        private List<String> controls;
        private List<String> recommendations;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RecommendationEvidence {
        private String key;
        private String label;
        private String value;
        private boolean available;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RecommendationEngineResponse {
        private String status; // READY, WITHHELD
        private String decision;
        private String riskBand;
        private Double recommendedLimitCr;
        private Double pricingSpreadBps;
        private Double indicativeRatePct;
        private Integer internalRiskScore;
        private String internalRating;
        private Double probabilityOfDefault1Y;
        private Double expectedLossPct;
        private Double confidence;
        private Double completenessScore;
        private boolean grounded;
        private List<String> guardrailReasons;
        private List<String> recommendations;
        private List<String> covenants;
        private List<String> rationale;
        private List<RecommendationEvidence> evidence;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReviewWorkflowRequest {
        private String notes;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ReviewWorkflowResponse {
        private String status;
        private String submittedBy;
        private String approvedBy;
        private String notes;
        private String approvedNotes;
        private String submittedAt;
        private String approvedAt;
    }
}
