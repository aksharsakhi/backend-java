package com.credereai.module1.engines;

import com.credereai.module1.models.FinancialData.*;
import com.credereai.module1.utils.FinancialUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
public class DebtCashflowAnalyzer {

    private final FinancialUtils financialUtils;

    public FinancialRatiosReport analyzeRatios(ExtractedFinancialData data) {
        return financialUtils.computeAllRatios(data);
    }

    public String generateAnalysisSummary(FinancialRatiosReport report) {
        List<String> parts = new ArrayList<>();
        parts.add("Financial Ratio Analysis Summary:");
        parts.add(String.format("Overall Health: %s", report.getOverallHealth()));

        if (report.getDebtToEquity() != null && report.getDebtToEquity().getValue() != null) {
            parts.add(String.format("- D/E Ratio: %.2f (%s)", report.getDebtToEquity().getValue(),
                    report.getDebtToEquity().getHealth()));
        }
        if (report.getInterestCoverage() != null && report.getInterestCoverage().getValue() != null) {
            parts.add(String.format("- ICR: %.2f (%s)", report.getInterestCoverage().getValue(),
                    report.getInterestCoverage().getHealth()));
        }
        if (report.getCurrentRatio() != null && report.getCurrentRatio().getValue() != null) {
            parts.add(String.format("- Current Ratio: %.2f (%s)", report.getCurrentRatio().getValue(),
                    report.getCurrentRatio().getHealth()));
        }
        if (report.getProfitMargin() != null && report.getProfitMargin().getValue() != null) {
            parts.add(String.format("- Profit Margin: %.1f%% (%s)", report.getProfitMargin().getValue() * 100,
                    report.getProfitMargin().getHealth()));
        }
        if (report.getRevenueGrowth() != null && report.getRevenueGrowth().getValue() != null) {
            parts.add(String.format("- Revenue Growth: %.1f%% (%s)", report.getRevenueGrowth().getValue() * 100,
                    report.getRevenueGrowth().getHealth()));
        }

        return String.join("\n", parts);
    }
}
