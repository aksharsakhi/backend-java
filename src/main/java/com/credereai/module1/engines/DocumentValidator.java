package com.credereai.module1.engines;

import com.credereai.module1.config.Module1Config;
import com.credereai.module1.models.FinancialData;
import com.credereai.module1.models.FinancialData.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DocumentValidator {

    private static final Map<String, String> FIELD_PRIORITIES = Map.ofEntries(
            Map.entry("revenue", "CRITICAL"),
            Map.entry("profit", "CRITICAL"),
            Map.entry("totalDebt", "CRITICAL"),
            Map.entry("totalAssets", "HIGH"),
            Map.entry("totalLiabilities", "HIGH"),
            Map.entry("cashFlow", "HIGH"),
            Map.entry("equity", "HIGH"),
            Map.entry("interestExpense", "MEDIUM"),
            Map.entry("ebit", "MEDIUM"),
            Map.entry("currentAssets", "MEDIUM"),
            Map.entry("currentLiabilities", "MEDIUM"),
            Map.entry("gstRevenue", "LOW"),
            Map.entry("bankInflow", "LOW"),
            Map.entry("bankOutflow", "LOW")
    );

    private static final Map<String, String> FIELD_SUGGESTIONS = Map.ofEntries(
            Map.entry("revenue", "Upload Annual Report or Financial Statement containing P&L data"),
            Map.entry("profit", "Upload Annual Report or Financial Statement containing P&L data"),
            Map.entry("totalDebt", "Upload Annual Report or Financial Statement with Balance Sheet"),
            Map.entry("totalAssets", "Upload Annual Report or Financial Statement with Balance Sheet"),
            Map.entry("totalLiabilities", "Upload Annual Report or Financial Statement with Balance Sheet"),
            Map.entry("cashFlow", "Upload Annual Report or Financial Statement with Cash Flow Statement"),
            Map.entry("equity", "Upload Annual Report or Financial Statement with Balance Sheet"),
            Map.entry("interestExpense", "Upload Financial Statement with detailed P&L"),
            Map.entry("ebit", "Upload Financial Statement with detailed P&L"),
            Map.entry("currentAssets", "Upload Annual Report or Financial Statement with Balance Sheet"),
            Map.entry("currentLiabilities", "Upload Annual Report or Financial Statement with Balance Sheet"),
            Map.entry("gstRevenue", "Upload GST filings (GSTR-1 / GSTR-3B)"),
            Map.entry("bankInflow", "Upload Bank statements"),
            Map.entry("bankOutflow", "Upload Bank statements")
    );

    public DataCompletenessReport checkCompleteness(ExtractedFinancialData data) {
        List<FieldCompleteness> fields = new ArrayList<>();
        int present = 0;
        int total = FIELD_PRIORITIES.size();

        Map<String, Double> valueMap = getFieldValueMap(data);

        for (Map.Entry<String, String> entry : FIELD_PRIORITIES.entrySet()) {
            String field = entry.getKey();
            String priority = entry.getValue();
            Double value = valueMap.get(field);
            boolean isPresent = value != null;
            if (isPresent) present++;

            String[] sourceDocs = Module1Config.REQUIRED_FINANCIAL_FIELDS.getOrDefault(field, new String[]{});

            fields.add(FieldCompleteness.builder()
                    .field(field)
                    .present(isPresent)
                    .priority(priority)
                    .sourceDocs(List.of(sourceDocs))
                    .suggestion(isPresent ? null : FIELD_SUGGESTIONS.get(field))
                    .build());
        }

        double score = total > 0 ? (double) present / total * 100.0 : 0.0;

        List<String> overallSuggestions = generateOverallSuggestions(fields);

        return DataCompletenessReport.builder()
                .completenessScore(score)
                .totalFields(total)
                .presentFields(present)
                .fields(fields)
                .overallSuggestions(overallSuggestions)
                .build();
    }

    private Map<String, Double> getFieldValueMap(ExtractedFinancialData data) {
        Map<String, Double> map = new LinkedHashMap<>();
        map.put("revenue", data.getRevenue());
        map.put("profit", data.getProfit());
        map.put("totalDebt", data.getTotalDebt());
        map.put("totalAssets", data.getTotalAssets());
        map.put("totalLiabilities", data.getTotalLiabilities());
        map.put("cashFlow", data.getCashFlow());
        map.put("equity", data.getEquity());
        map.put("interestExpense", data.getInterestExpense());
        map.put("ebit", data.getEbit());
        map.put("currentAssets", data.getCurrentAssets());
        map.put("currentLiabilities", data.getCurrentLiabilities());
        map.put("gstRevenue", data.getGstRevenue());
        map.put("bankInflow", data.getBankInflow());
        map.put("bankOutflow", data.getBankOutflow());
        return map;
    }

    private List<String> generateOverallSuggestions(List<FieldCompleteness> fields) {
        List<String> suggestions = new ArrayList<>();
        long missingCritical = fields.stream()
                .filter(f -> !f.isPresent() && "CRITICAL".equals(f.getPriority())).count();
        long missingHigh = fields.stream()
                .filter(f -> !f.isPresent() && "HIGH".equals(f.getPriority())).count();

        if (missingCritical > 0) {
            suggestions.add("URGENT: " + missingCritical + " critical fields are missing. Upload Annual Report or Financial Statements.");
        }
        if (missingHigh > 0) {
            suggestions.add("Upload additional financial documents to fill " + missingHigh + " high-priority fields.");
        }

        boolean hasGst = fields.stream().anyMatch(f -> "gstRevenue".equals(f.getField()) && f.isPresent());
        boolean hasBank = fields.stream().anyMatch(f -> "bankInflow".equals(f.getField()) && f.isPresent());
        if (!hasGst || !hasBank) {
            suggestions.add("Upload GST filings and Bank statements to enable cross-verification analysis.");
        }

        if (suggestions.isEmpty()) {
            suggestions.add("All required financial fields are available. Data completeness is excellent.");
        }

        return suggestions;
    }
}
