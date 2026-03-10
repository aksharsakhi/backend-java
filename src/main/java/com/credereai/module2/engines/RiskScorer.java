package com.credereai.module2.engines;

import com.credereai.module2.models.ResearchData.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class RiskScorer {

    // Category weights for composite score
    private static final Map<String, Double> CATEGORY_WEIGHTS = Map.of(
            "network", 0.25,
            "litigation", 0.20,
            "regulatory", 0.20,
            "news", 0.15,
            "promoter", 0.20
    );

    private static final Map<String, Double> SEVERITY_SCORES = Map.of(
            "LOW", 2.0,
            "MEDIUM", 5.0,
            "HIGH", 8.0,
            "CRITICAL", 10.0
    );

    public ExternalRiskScore computeScore(
            NetworkGraph network,
            List<LitigationRecord> litigations,
            List<RegulatoryAction> regulations,
            List<NewsSignal> newsSignals,
            List<PromoterProfile> promoters) {

        double networkScore = computeNetworkScore(network);
        double litigationScore = computeLitigationScore(litigations);
        double regulatoryScore = computeRegulatoryScore(regulations);
        double newsScore = computeNewsScore(newsSignals);
        double promoterScore = computePromoterScore(promoters);

        double compositeScore =
                networkScore * CATEGORY_WEIGHTS.get("network") +
                litigationScore * CATEGORY_WEIGHTS.get("litigation") +
                regulatoryScore * CATEGORY_WEIGHTS.get("regulatory") +
                newsScore * CATEGORY_WEIGHTS.get("news") +
                promoterScore * CATEGORY_WEIGHTS.get("promoter");

        // Round to 1 decimal
        compositeScore = Math.round(compositeScore * 10.0) / 10.0;

        String grade = computeGrade(compositeScore);
        List<RiskAlert> alerts = generateAlerts(network, litigations, regulations, newsSignals, promoters);
        String summary = generateSummary(compositeScore, grade, alerts.size());

        return ExternalRiskScore.builder()
                .compositeScore(compositeScore)
                .grade(grade)
                .networkScore(Math.round(networkScore * 10.0) / 10.0)
                .litigationScore(Math.round(litigationScore * 10.0) / 10.0)
                .regulatoryScore(Math.round(regulatoryScore * 10.0) / 10.0)
                .newsScore(Math.round(newsScore * 10.0) / 10.0)
                .promoterScore(Math.round(promoterScore * 10.0) / 10.0)
                .alerts(alerts)
                .summary(summary)
                .build();
    }

    private double computeNetworkScore(NetworkGraph network) {
        if (network == null || network.getNodes() == null || network.getNodes().isEmpty()) return 0.0;
        double avgRisk = network.getNodes().stream()
                .filter(n -> n.getRiskScore() != null)
                .mapToDouble(NetworkNode::getRiskScore)
                .average().orElse(0.0);
        return avgRisk * 10.0;
    }

    private double computeLitigationScore(List<LitigationRecord> records) {
        if (records == null || records.isEmpty()) return 0.0;
        double score = 0;
        for (LitigationRecord r : records) {
            score += SEVERITY_SCORES.getOrDefault(
                    r.getRiskLevel() != null ? r.getRiskLevel().name() : "LOW", 2.0);
        }
        return Math.min(score, 30.0); // Cap at 30
    }

    private double computeRegulatoryScore(List<RegulatoryAction> actions) {
        if (actions == null || actions.isEmpty()) return 0.0;
        double score = 0;
        for (RegulatoryAction a : actions) {
            score += SEVERITY_SCORES.getOrDefault(
                    a.getSeverity() != null ? a.getSeverity().name() : "LOW", 2.0);
        }
        return Math.min(score, 30.0);
    }

    private double computeNewsScore(List<NewsSignal> signals) {
        if (signals == null || signals.isEmpty()) return 0.0;
        double score = 0;
        for (NewsSignal s : signals) {
            if ("negative".equalsIgnoreCase(s.getSentiment())) {
                score += 3.0 * (s.getRelevanceScore() != null ? s.getRelevanceScore() : 0.5);
            } else if ("positive".equalsIgnoreCase(s.getSentiment())) {
                score -= 1.0;
            }
        }
        return Math.max(0, Math.min(score, 20.0));
    }

    private double computePromoterScore(List<PromoterProfile> profiles) {
        if (profiles == null || profiles.isEmpty()) return 0.0;
        double avgRisk = profiles.stream()
                .filter(p -> p.getRiskScore() != null)
                .mapToDouble(PromoterProfile::getRiskScore)
                .average().orElse(0.0);
        double flagCount = profiles.stream()
                .filter(p -> p.getFlags() != null)
                .mapToInt(p -> p.getFlags().size())
                .sum();
        return Math.min(avgRisk * 10.0 + flagCount * 2.0, 30.0);
    }

    private String computeGrade(double score) {
        if (score <= 5) return "A";
        if (score <= 10) return "B";
        if (score <= 15) return "C";
        if (score <= 20) return "D";
        if (score <= 25) return "E";
        return "F";
    }

    private List<RiskAlert> generateAlerts(
            NetworkGraph network, List<LitigationRecord> litigations,
            List<RegulatoryAction> regulations, List<NewsSignal> news,
            List<PromoterProfile> promoters) {

        List<RiskAlert> alerts = new ArrayList<>();

        // Network alerts
        if (network != null && network.getNodes() != null) {
            network.getNodes().stream()
                    .filter(n -> n.getRiskScore() != null && n.getRiskScore() > 0.7)
                    .forEach(n -> alerts.add(RiskAlert.builder()
                            .category(AlertCategory.NETWORK)
                            .severity(n.getRiskScore() > 0.9 ? RiskLevel.CRITICAL : RiskLevel.HIGH)
                            .title("High-risk entity: " + n.getName())
                            .description(n.getRelationship())
                            .source("Network Analysis")
                            .impact(n.getRiskScore())
                            .build()));
        }

        // Litigation alerts
        if (litigations != null) {
            litigations.stream()
                    .filter(l -> l.getRiskLevel() == RiskLevel.HIGH || l.getRiskLevel() == RiskLevel.CRITICAL)
                    .forEach(l -> alerts.add(RiskAlert.builder()
                            .category(AlertCategory.LITIGATION)
                            .severity(l.getRiskLevel())
                            .title("Litigation: " + l.getNature())
                            .description(l.getSummary())
                            .source(l.getCourt())
                            .impact(l.getAmount() != null ? Math.min(l.getAmount() / 1e9, 1.0) : 0.5)
                            .build()));
        }

        // Regulatory alerts
        if (regulations != null) {
            regulations.stream()
                    .filter(r -> r.getSeverity() == RiskLevel.HIGH || r.getSeverity() == RiskLevel.CRITICAL)
                    .forEach(r -> alerts.add(RiskAlert.builder()
                            .category(AlertCategory.REGULATORY)
                            .severity(r.getSeverity())
                            .title(r.getAuthority() + " action: " + r.getActionType())
                            .description(r.getDescription())
                            .source(r.getAuthority())
                            .impact(r.getPenalty() != null ? Math.min(r.getPenalty() / 1e9, 1.0) : 0.5)
                            .build()));
        }

        // Promoter alerts
        if (promoters != null) {
            promoters.stream()
                    .filter(p -> p.getFlags() != null && !p.getFlags().isEmpty())
                    .forEach(p -> alerts.add(RiskAlert.builder()
                            .category(AlertCategory.PROMOTER)
                            .severity(p.getRiskScore() != null && p.getRiskScore() > 0.7 ? RiskLevel.HIGH : RiskLevel.MEDIUM)
                            .title("Promoter concern: " + p.getName())
                            .description(String.join("; ", p.getFlags()))
                            .source("Promoter Analysis")
                            .impact(p.getRiskScore())
                            .build()));
        }

        return alerts;
    }

    private String generateSummary(double score, String grade, int alertCount) {
        String riskLevel;
        if (score <= 5) riskLevel = "Low risk";
        else if (score <= 10) riskLevel = "Moderate risk";
        else if (score <= 15) riskLevel = "Elevated risk";
        else if (score <= 20) riskLevel = "High risk";
        else riskLevel = "Very high risk";

        return String.format("Composite risk score: %.1f/30 (Grade %s). %s. %d alert(s) generated.",
                score, grade, riskLevel, alertCount);
    }
}
