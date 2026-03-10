package com.credereai.module1.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class FinancialData {

    public enum DocumentCategory {
        ANNUAL_REPORT("annual_report"),
        FINANCIAL_STATEMENT("financial_statement"),
        BANK_STATEMENT("bank_statement"),
        GST_FILING("gst_filing"),
        RATING_REPORT("rating_report");

        private final String value;
        DocumentCategory(String value) { this.value = value; }
        public String getValue() { return value; }

        public static DocumentCategory fromString(String text) {
            for (DocumentCategory c : DocumentCategory.values()) {
                if (c.value.equalsIgnoreCase(text)) return c;
            }
            return ANNUAL_REPORT;
        }
    }

    public enum RiskLevel {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum RatioHealth {
        HEALTHY, WARNING, CRITICAL, UNKNOWN
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExtractedFinancialData {
        private String companyName;
        private String financialYear;
        private String documentType;
        private String sourceDocument;

        // Core financials
        private Double revenue;
        private Double profit;
        private Double totalDebt;
        private Double totalAssets;
        private Double totalLiabilities;
        private Double cashFlow;
        private Double equity;
        private Double interestExpense;
        private Double ebit;
        private Double currentAssets;
        private Double currentLiabilities;

        // GST and Bank fields
        private Double gstRevenue;
        private Double bankInflow;
        private Double bankOutflow;

        // Previous year fields
        @JsonProperty("prev_revenue")
        private Double prevRevenue;
        @JsonProperty("prev_profit")
        private Double prevProfit;
        @JsonProperty("prev_total_debt")
        private Double prevTotalDebt;

        // Corporate info
        private String cin;
        private String registeredAddress;
        private String auditor;
        private List<String> directors;
        private List<String> promoters;
        private Double promoterHolding;

        // Metadata
        private Double confidence;
        private List<String> warnings;
        private List<FinancialTable> extractedTables;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class FinancialTable {
        private String title;
        private List<String> headers;
        private List<List<String>> rows;
        private String unit;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TrendAnalysis {
        private String metric;
        private List<TrendPoint> dataPoints;
        private Double growthRate;
        private String trend; // "improving", "declining", "stable"
        private String analysis;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class TrendPoint {
        private String year;
        private Double value;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class CrossVerificationAlert {
        private String field;
        private String source1;
        private Double value1;
        private String source2;
        private Double value2;
        private Double deviationPercent;
        private RiskLevel riskLevel;
        private String description;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class RatioResult {
        private String name;
        private Double value;
        private RatioHealth health;
        private String interpretation;
        private Map<String, Double> benchmark;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FinancialRatiosReport {
        private RatioResult debtToEquity;
        private RatioResult interestCoverage;
        private RatioResult currentRatio;
        private RatioResult profitMargin;
        private RatioResult revenueGrowth;
        private String overallHealth;
        private List<String> recommendations;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class FieldCompleteness {
        private String field;
        private boolean present;
        private String priority; // CRITICAL, HIGH, MEDIUM, LOW
        private List<String> sourceDocs;
        private String suggestion;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class DataCompletenessReport {
        private double completenessScore;
        private int totalFields;
        private int presentFields;
        private List<FieldCompleteness> fields;
        private List<String> overallSuggestions;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class UploadedDocument {
        private String id;
        private String filename;
        private String category;
        private long fileSize;
        private int pageCount;
        private int textLength;
        private LocalDateTime uploadedAt;
        private ExtractedFinancialData extractedData;
    }
}
