package com.credereai.module1.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.util.Map;

@Getter
@Component
public class Module1Config {

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    @Value("${GEMINI_MODEL:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${server.port:8001}")
    private int port;

    private String uploadDir;
    private final int maxFileSizeMb = 200;
        private final int maxTextChars = 180_000;

    // Document categories
    public static final Map<String, String> DOCUMENT_CATEGORIES = Map.of(
            "annual_report", "Annual Report",
            "financial_statement", "Financial Statement",
            "bank_statement", "Bank Statement",
            "gst_filing", "GST Filing",
            "rating_report", "Rating Agency Report"
    );

    // Required financial fields with source docs
    public static final Map<String, String[]> REQUIRED_FINANCIAL_FIELDS = Map.ofEntries(
            Map.entry("revenue", new String[]{"annual_report", "financial_statement", "gst_filing"}),
            Map.entry("profit", new String[]{"annual_report", "financial_statement"}),
            Map.entry("totalDebt", new String[]{"annual_report", "financial_statement"}),
            Map.entry("totalAssets", new String[]{"annual_report", "financial_statement"}),
            Map.entry("totalLiabilities", new String[]{"annual_report", "financial_statement"}),
            Map.entry("cashFlow", new String[]{"annual_report", "financial_statement"}),
            Map.entry("equity", new String[]{"annual_report", "financial_statement"}),
            Map.entry("interestExpense", new String[]{"annual_report", "financial_statement"}),
            Map.entry("ebit", new String[]{"annual_report", "financial_statement"}),
            Map.entry("currentAssets", new String[]{"annual_report", "financial_statement"}),
            Map.entry("currentLiabilities", new String[]{"annual_report", "financial_statement"}),
            Map.entry("gstRevenue", new String[]{"gst_filing"}),
            Map.entry("bankInflow", new String[]{"bank_statement"}),
            Map.entry("bankOutflow", new String[]{"bank_statement"})
    );

    // Ratio benchmarks
    public static final Map<String, Map<String, Double>> RATIO_BENCHMARKS = Map.of(
            "debtToEquity", Map.of("healthy", 1.0, "warning", 2.0, "critical", 3.0),
            "interestCoverage", Map.of("healthy", 3.0, "warning", 1.5, "critical", 1.0),
            "currentRatio", Map.of("healthy", 1.5, "warning", 1.0, "critical", 0.8),
            "profitMargin", Map.of("healthy", 0.10, "warning", 0.05, "critical", 0.02),
            "revenueGrowth", Map.of("healthy", 0.10, "warning", 0.0, "critical", -0.05)
    );

    public static final double GST_BANK_DEVIATION_THRESHOLD = 0.25;
    public static final double CIRCULAR_TRADING_THRESHOLD = 0.50;

    @PostConstruct
    public void init() {
        uploadDir = System.getProperty("user.dir") + "/uploads";
        new File(uploadDir).mkdirs();
    }
}
