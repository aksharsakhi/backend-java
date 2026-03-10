package com.credereai.module1.utils;

import com.credereai.module1.config.Module1Config;
import com.credereai.module1.models.FinancialData;
import com.credereai.module1.models.FinancialData.RatioHealth;
import com.credereai.module1.models.FinancialData.RatioResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class FinancialUtils {

    public RatioResult calculateDebtToEquity(Double totalDebt, Double equity) {
        if (totalDebt == null || equity == null || equity == 0) {
            return RatioResult.builder().name("Debt-to-Equity Ratio").health(RatioHealth.UNKNOWN)
                    .interpretation("Insufficient data to calculate D/E ratio").build();
        }
        double value = totalDebt / equity;
        Map<String, Double> bench = Module1Config.RATIO_BENCHMARKS.get("debtToEquity");
        RatioHealth health = assessHealth(value, bench, true);
        return RatioResult.builder().name("Debt-to-Equity Ratio").value(value).health(health)
                .interpretation(generateDebtToEquityInterpretation(value, health))
                .benchmark(bench).build();
    }

    public RatioResult calculateInterestCoverage(Double ebit, Double interestExpense) {
        if (ebit == null || interestExpense == null || interestExpense == 0) {
            return RatioResult.builder().name("Interest Coverage Ratio").health(RatioHealth.UNKNOWN)
                    .interpretation("Insufficient data to calculate ICR").build();
        }
        double value = ebit / interestExpense;
        Map<String, Double> bench = Module1Config.RATIO_BENCHMARKS.get("interestCoverage");
        RatioHealth health = assessHealth(value, bench, false);
        return RatioResult.builder().name("Interest Coverage Ratio").value(value).health(health)
                .interpretation(generateInterestCoverageInterpretation(value, health))
                .benchmark(bench).build();
    }

    public RatioResult calculateCurrentRatio(Double currentAssets, Double currentLiabilities) {
        if (currentAssets == null || currentLiabilities == null || currentLiabilities == 0) {
            return RatioResult.builder().name("Current Ratio").health(RatioHealth.UNKNOWN)
                    .interpretation("Insufficient data to calculate current ratio").build();
        }
        double value = currentAssets / currentLiabilities;
        Map<String, Double> bench = Module1Config.RATIO_BENCHMARKS.get("currentRatio");
        RatioHealth health = assessHealth(value, bench, false);
        return RatioResult.builder().name("Current Ratio").value(value).health(health)
                .interpretation(generateCurrentRatioInterpretation(value, health))
                .benchmark(bench).build();
    }

    public RatioResult calculateProfitMargin(Double profit, Double revenue) {
        if (profit == null || revenue == null || revenue == 0) {
            return RatioResult.builder().name("Profit Margin").health(RatioHealth.UNKNOWN)
                    .interpretation("Insufficient data to calculate profit margin").build();
        }
        double value = profit / revenue;
        Map<String, Double> bench = Module1Config.RATIO_BENCHMARKS.get("profitMargin");
        RatioHealth health = assessHealth(value, bench, false);
        return RatioResult.builder().name("Profit Margin").value(value).health(health)
                .interpretation(generateProfitMarginInterpretation(value, health))
                .benchmark(bench).build();
    }

    public RatioResult calculateRevenueGrowth(Double currentRevenue, Double prevRevenue) {
        if (currentRevenue == null || prevRevenue == null || prevRevenue == 0) {
            return RatioResult.builder().name("Revenue Growth").health(RatioHealth.UNKNOWN)
                    .interpretation("Insufficient data to calculate revenue growth").build();
        }
        double value = (currentRevenue - prevRevenue) / Math.abs(prevRevenue);
        Map<String, Double> bench = Module1Config.RATIO_BENCHMARKS.get("revenueGrowth");
        RatioHealth health = assessHealth(value, bench, false);
        return RatioResult.builder().name("Revenue Growth").value(value).health(health)
                .interpretation(generateRevenueGrowthInterpretation(value, health))
                .benchmark(bench).build();
    }

    public FinancialData.FinancialRatiosReport computeAllRatios(FinancialData.ExtractedFinancialData data) {
        RatioResult de = calculateDebtToEquity(data.getTotalDebt(), data.getEquity());
        RatioResult icr = calculateInterestCoverage(data.getEbit(), data.getInterestExpense());
        RatioResult cr = calculateCurrentRatio(data.getCurrentAssets(), data.getCurrentLiabilities());
        RatioResult pm = calculateProfitMargin(data.getProfit(), data.getRevenue());
        RatioResult rg = calculateRevenueGrowth(data.getRevenue(), data.getPrevRevenue());

        String overallHealth = assessOverallHealth(de, icr, cr, pm, rg);
        List<String> recommendations = generateRecommendations(de, icr, cr, pm, rg);

        return FinancialData.FinancialRatiosReport.builder()
                .debtToEquity(de).interestCoverage(icr).currentRatio(cr)
                .profitMargin(pm).revenueGrowth(rg)
                .overallHealth(overallHealth).recommendations(recommendations)
                .build();
    }

    private RatioHealth assessHealth(double value, Map<String, Double> bench, boolean lowerIsBetter) {
        if (bench == null) return RatioHealth.UNKNOWN;
        if (lowerIsBetter) {
            if (value <= bench.get("healthy")) return RatioHealth.HEALTHY;
            if (value <= bench.get("warning")) return RatioHealth.WARNING;
            return RatioHealth.CRITICAL;
        } else {
            if (value >= bench.get("healthy")) return RatioHealth.HEALTHY;
            if (value >= bench.get("warning")) return RatioHealth.WARNING;
            return RatioHealth.CRITICAL;
        }
    }

    private String assessOverallHealth(RatioResult... ratios) {
        int critical = 0, warning = 0, healthy = 0, assessed = 0;
        for (RatioResult r : ratios) {
            if (r.getHealth() == RatioHealth.CRITICAL) { critical++; assessed++; }
            else if (r.getHealth() == RatioHealth.WARNING) { warning++; assessed++; }
            else if (r.getHealth() == RatioHealth.HEALTHY) { healthy++; assessed++; }
        }
        if (assessed == 0) return "UNKNOWN";
        if (critical >= 2) return "CRITICAL";
        if (critical >= 1 || warning >= 2) return "WARNING";
        if (healthy >= 3) return "HEALTHY";
        return "MODERATE";
    }

    private List<String> generateRecommendations(RatioResult... ratios) {
        List<String> recs = new ArrayList<>();
        for (RatioResult r : ratios) {
            if (r.getHealth() == RatioHealth.CRITICAL) {
                recs.add("URGENT: " + r.getName() + " is at critical level (" + formatValue(r.getValue()) + "). Immediate attention required.");
            } else if (r.getHealth() == RatioHealth.WARNING) {
                recs.add("WARNING: " + r.getName() + " needs monitoring (" + formatValue(r.getValue()) + ").");
            }
        }
        if (recs.isEmpty()) recs.add("All financial ratios are within healthy ranges.");
        return recs;
    }

    private String formatValue(Double value) {
        return value != null ? String.format("%.2f", value) : "N/A";
    }

    private String generateDebtToEquityInterpretation(double value, RatioHealth health) {
        return switch (health) {
            case HEALTHY -> String.format("D/E ratio of %.2f indicates conservative leveraging and strong equity base.", value);
            case WARNING -> String.format("D/E ratio of %.2f suggests moderate leverage. Monitor debt levels.", value);
            case CRITICAL -> String.format("D/E ratio of %.2f indicates high leverage risk. Debt restructuring may be needed.", value);
            default -> "Unable to assess.";
        };
    }

    private String generateInterestCoverageInterpretation(double value, RatioHealth health) {
        return switch (health) {
            case HEALTHY -> String.format("ICR of %.2f shows strong ability to service debt obligations.", value);
            case WARNING -> String.format("ICR of %.2f indicates marginal debt service capacity.", value);
            case CRITICAL -> String.format("ICR of %.2f signals potential difficulty meeting interest payments.", value);
            default -> "Unable to assess.";
        };
    }

    private String generateCurrentRatioInterpretation(double value, RatioHealth health) {
        return switch (health) {
            case HEALTHY -> String.format("Current ratio of %.2f indicates good short-term liquidity.", value);
            case WARNING -> String.format("Current ratio of %.2f suggests tight liquidity. Monitor working capital.", value);
            case CRITICAL -> String.format("Current ratio of %.2f indicates potential liquidity crisis.", value);
            default -> "Unable to assess.";
        };
    }

    private String generateProfitMarginInterpretation(double value, RatioHealth health) {
        return switch (health) {
            case HEALTHY -> String.format("Profit margin of %.1f%% indicates healthy profitability.", value * 100);
            case WARNING -> String.format("Profit margin of %.1f%% is thin. Cost optimization may be needed.", value * 100);
            case CRITICAL -> String.format("Profit margin of %.1f%% is critically low. Review pricing and costs.", value * 100);
            default -> "Unable to assess.";
        };
    }

    private String generateRevenueGrowthInterpretation(double value, RatioHealth health) {
        return switch (health) {
            case HEALTHY -> String.format("Revenue growth of %.1f%% shows strong business momentum.", value * 100);
            case WARNING -> String.format("Revenue growth of %.1f%% indicates stagnation.", value * 100);
            case CRITICAL -> String.format("Revenue decline of %.1f%% is concerning. Investigate root causes.", value * 100);
            default -> "Unable to assess.";
        };
    }
}
