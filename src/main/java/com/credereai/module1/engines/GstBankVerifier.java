package com.credereai.module1.engines;

import com.credereai.module1.config.Module1Config;
import com.credereai.module1.models.FinancialData.*;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class GstBankVerifier {

    public List<CrossVerificationAlert> verify(ExtractedFinancialData data) {
        List<CrossVerificationAlert> alerts = new ArrayList<>();

        // GST Revenue vs Financial Statement Revenue
        if (data.getGstRevenue() != null && data.getRevenue() != null && data.getRevenue() != 0) {
            double deviation = Math.abs(data.getGstRevenue() - data.getRevenue()) / Math.abs(data.getRevenue());
            if (deviation > Module1Config.GST_BANK_DEVIATION_THRESHOLD) {
                alerts.add(CrossVerificationAlert.builder()
                        .field("Revenue")
                        .source1("GST Filing")
                        .value1(data.getGstRevenue())
                        .source2("Financial Statement")
                        .value2(data.getRevenue())
                        .deviationPercent(deviation * 100)
                        .riskLevel(deviation > 0.5 ? RiskLevel.HIGH : RiskLevel.MEDIUM)
                        .description(String.format(
                                "GST revenue (%.2f Cr) deviates from reported revenue (%.2f Cr) by %.1f%%. Possible under-reporting of revenue in financial statements.",
                                data.getGstRevenue(), data.getRevenue(), deviation * 100))
                        .build());
            }
        }

        // Bank Inflow vs Revenue
        if (data.getBankInflow() != null && data.getRevenue() != null && data.getRevenue() != 0) {
            double deviation = Math.abs(data.getBankInflow() - data.getRevenue()) / Math.abs(data.getRevenue());
            if (deviation > Module1Config.GST_BANK_DEVIATION_THRESHOLD) {
                alerts.add(CrossVerificationAlert.builder()
                        .field("Revenue vs Bank Inflow")
                        .source1("Bank Statement")
                        .value1(data.getBankInflow())
                        .source2("Financial Statement")
                        .value2(data.getRevenue())
                        .deviationPercent(deviation * 100)
                        .riskLevel(deviation > 0.5 ? RiskLevel.HIGH : RiskLevel.MEDIUM)
                        .description(String.format(
                                "Bank inflow (%.2f Cr) deviates from reported revenue (%.2f Cr) by %.1f%%. Investigate cash flow sources.",
                                data.getBankInflow(), data.getRevenue(), deviation * 100))
                        .build());
            }
        }

        // Circular trading detection: high bank outflow relative to inflow
        if (data.getBankInflow() != null && data.getBankOutflow() != null && data.getBankInflow() != 0) {
            double outflowRatio = data.getBankOutflow() / data.getBankInflow();
            if (outflowRatio > Module1Config.CIRCULAR_TRADING_THRESHOLD) {
                RiskLevel risk = outflowRatio > 0.9 ? RiskLevel.HIGH : RiskLevel.MEDIUM;
                alerts.add(CrossVerificationAlert.builder()
                        .field("Circular Trading Indicator")
                        .source1("Bank Outflow")
                        .value1(data.getBankOutflow())
                        .source2("Bank Inflow")
                        .value2(data.getBankInflow())
                        .deviationPercent(outflowRatio * 100)
                        .riskLevel(risk)
                        .description(String.format(
                                "Bank outflow (%.2f Cr) is %.1f%% of inflow (%.2f Cr). High outflow ratio may indicate circular trading or fund diversion.",
                                data.getBankOutflow(), outflowRatio * 100, data.getBankInflow()))
                        .build());
            }
        }

        // GST Revenue vs Bank Inflow
        if (data.getGstRevenue() != null && data.getBankInflow() != null && data.getBankInflow() != 0) {
            double deviation = Math.abs(data.getGstRevenue() - data.getBankInflow()) / Math.abs(data.getBankInflow());
            if (deviation > Module1Config.GST_BANK_DEVIATION_THRESHOLD) {
                alerts.add(CrossVerificationAlert.builder()
                        .field("GST Revenue vs Bank Inflow")
                        .source1("GST Filing")
                        .value1(data.getGstRevenue())
                        .source2("Bank Statement")
                        .value2(data.getBankInflow())
                        .deviationPercent(deviation * 100)
                        .riskLevel(deviation > 0.5 ? RiskLevel.HIGH : RiskLevel.MEDIUM)
                        .description(String.format(
                                "GST revenue (%.2f Cr) deviates from bank inflow (%.2f Cr) by %.1f%%. Possible discrepancy in reported vs actual revenue.",
                                data.getGstRevenue(), data.getBankInflow(), deviation * 100))
                        .build());
            }
        }

        return alerts;
    }

    public String generateSummary(List<CrossVerificationAlert> alerts) {
        if (alerts.isEmpty()) {
            return "No significant deviations found between GST, bank, and financial statement data.";
        }
        long high = alerts.stream().filter(a -> a.getRiskLevel() == RiskLevel.HIGH).count();
        long medium = alerts.stream().filter(a -> a.getRiskLevel() == RiskLevel.MEDIUM).count();
        return String.format(
                "Cross-verification found %d alert(s): %d high-risk, %d medium-risk. Review flagged discrepancies between data sources.",
                alerts.size(), high, medium);
    }
}
