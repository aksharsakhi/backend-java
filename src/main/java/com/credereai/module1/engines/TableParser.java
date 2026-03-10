package com.credereai.module1.engines;

import com.credereai.module1.models.FinancialData.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class TableParser {

    /**
     * Parse extracted tables and generate trend data points from multi-year tables.
     */
    public List<TrendAnalysis> parseTrends(List<FinancialTable> tables, ExtractedFinancialData data) {
        List<TrendAnalysis> trends = new ArrayList<>();

        // Generate trends from extracted data (current + previous year)
        addTrendIfAvailable(trends, "Revenue", data.getRevenue(), data.getPrevRevenue(), data.getFinancialYear());
        addTrendIfAvailable(trends, "Net Profit", data.getProfit(), data.getPrevProfit(), data.getFinancialYear());
        addTrendIfAvailable(trends, "Total Debt", data.getTotalDebt(), data.getPrevTotalDebt(), data.getFinancialYear());

        // Try to extract more trends from tables
        if (tables != null) {
            for (FinancialTable table : tables) {
                List<TrendAnalysis> tableTrends = extractTrendsFromTable(table);
                trends.addAll(tableTrends);
            }
        }

        return trends;
    }

    private void addTrendIfAvailable(List<TrendAnalysis> trends, String metric,
                                     Double currentValue, Double prevValue, String currentYear) {
        if (currentValue == null) return;

        List<TrendPoint> points = new ArrayList<>();
        String prevYear = derivePreviousYear(currentYear);

        if (prevValue != null) {
            points.add(TrendPoint.builder().year(prevYear).value(prevValue).build());
        }
        points.add(TrendPoint.builder().year(currentYear != null ? currentYear : "Current").value(currentValue).build());

        Double growthRate = null;
        String trend = "stable";
        boolean lowerIsBetter = metric != null && metric.toLowerCase().contains("debt");
        if (prevValue != null && prevValue != 0) {
            growthRate = (currentValue - prevValue) / Math.abs(prevValue);
            if (lowerIsBetter) {
                if (growthRate > 0.05) trend = "declining";
                else if (growthRate < -0.05) trend = "improving";
            } else {
                if (growthRate > 0.05) trend = "improving";
                else if (growthRate < -0.05) trend = "declining";
            }
        }

        String analysis = String.format("%s: Current value %.2f", metric, currentValue);
        if (prevValue != null) {
            analysis += String.format(", Previous year %.2f", prevValue);
            if (growthRate != null) {
                analysis += String.format(" (%.1f%% %s)", Math.abs(growthRate * 100),
                        growthRate >= 0 ? "increase" : "decrease");
            }
        }

        trends.add(TrendAnalysis.builder()
                .metric(metric)
                .dataPoints(points)
                .growthRate(growthRate)
                .trend(trend)
                .analysis(analysis)
                .build());
    }

    private List<TrendAnalysis> extractTrendsFromTable(FinancialTable table) {
        List<TrendAnalysis> trends = new ArrayList<>();
        if (table.getHeaders() == null || table.getRows() == null || table.getRows().isEmpty()) return trends;

        // Check if headers contain year patterns
        List<String> yearHeaders = new ArrayList<>();
        List<Integer> yearIndices = new ArrayList<>();
        for (int i = 0; i < table.getHeaders().size(); i++) {
            String header = table.getHeaders().get(i);
            if (header.matches(".*\\d{4}.*")) {
                yearHeaders.add(header.trim());
                yearIndices.add(i);
            }
        }

        if (yearHeaders.size() < 2) return trends;

        // Extract trend for each row (metric)
        for (List<String> row : table.getRows()) {
            if (row.isEmpty()) continue;
            String metric = row.get(0).trim();
            if (metric.isBlank() || metric.matches("^[-\\s]+$")) continue;

            List<TrendPoint> points = new ArrayList<>();
            for (int i = 0; i < yearIndices.size() && yearIndices.get(i) < row.size(); i++) {
                String valueStr = row.get(yearIndices.get(i));
                Double value = parseNumber(valueStr);
                if (value != null) {
                    points.add(TrendPoint.builder().year(yearHeaders.get(i)).value(value).build());
                }
            }

            if (points.size() >= 2) {
                double first = points.get(0).getValue();
                double last = points.get(points.size() - 1).getValue();
                Double growthRate = first != 0 ? (last - first) / Math.abs(first) : null;
                String trend = "stable";
                if (growthRate != null) {
                    if (growthRate > 0.05) trend = "improving";
                    else if (growthRate < -0.05) trend = "declining";
                }

                trends.add(TrendAnalysis.builder()
                        .metric(metric)
                        .dataPoints(points)
                        .growthRate(growthRate)
                        .trend(trend)
                        .analysis(String.format("%s trend over %d periods: %s", metric, points.size(), trend))
                        .build());
            }
        }
        return trends;
    }

    private String derivePreviousYear(String currentYear) {
        if (currentYear == null) return "Previous";
        // Handle "2023-24" format
        if (currentYear.matches("\\d{4}-\\d{2}")) {
            int startYear = Integer.parseInt(currentYear.substring(0, 4));
            return (startYear - 1) + "-" + String.format("%02d", startYear % 100);
        }
        // Handle "2024" format
        if (currentYear.matches("\\d{4}")) {
            return String.valueOf(Integer.parseInt(currentYear) - 1);
        }
        return "Previous";
    }

    private Double parseNumber(String text) {
        if (text == null) return null;
        String cleaned = text.replaceAll("[₹$,\\s]", "").trim();
        boolean negative = cleaned.startsWith("(") && cleaned.endsWith(")");
        if (negative) cleaned = cleaned.substring(1, cleaned.length() - 1);
        try {
            double value = Double.parseDouble(cleaned);
            return negative ? -value : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
