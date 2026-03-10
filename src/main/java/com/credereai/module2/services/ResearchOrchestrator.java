package com.credereai.module2.services;

import com.credereai.module2.engines.*;
import com.credereai.module2.engines.LitigationScanner.LitigationResult;
import com.credereai.module2.models.ResearchData.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ResearchOrchestrator {

    private final NetworkAnalyzer networkAnalyzer;
    private final LitigationScanner litigationScanner;
    private final NewsResearcher newsResearcher;
    private final PromoterAnalyzer promoterAnalyzer;
    private final RiskScorer riskScorer;

    public ResearchReport conductResearch(ResearchInput input) {
        log.info("Starting research for: {}", input.getCompanyName());

                List<RiskAlert> reliabilityAlerts = new ArrayList<>();

        // Step 1: Network Analysis
                log.info("Running network analysis...");
                NetworkGraph networkGraph;
                try {
                        networkGraph = networkAnalyzer.analyze(input);
                } catch (Exception e) {
                        log.warn("Network analysis failed: {}", e.getMessage());
                        networkGraph = NetworkGraph.builder().nodes(List.of()).edges(List.of()).summary("Network analysis unavailable").build();
                        reliabilityAlerts.add(systemAlert("Network analyzer unavailable", e.getMessage()));
                }
        log.info("Network analysis complete: {} nodes, {} edges",
                networkGraph.getNodes() != null ? networkGraph.getNodes().size() : 0,
                networkGraph.getEdges() != null ? networkGraph.getEdges().size() : 0);

        // Step 2: Litigation & Regulatory Scan
        log.info("Running litigation scan...");
                LitigationResult litigationResult;
                try {
                        litigationResult = litigationScanner.scan(input);
                } catch (Exception e) {
                        log.warn("Litigation scan failed: {}", e.getMessage());
                        litigationResult = new LitigationResult(List.of(), List.of());
                        reliabilityAlerts.add(systemAlert("Litigation scanner unavailable", e.getMessage()));
                }
        log.info("Litigation scan complete: {} records, {} regulatory actions",
                litigationResult.records().size(), litigationResult.actions().size());

        // Step 3: News Research
        log.info("Running news research...");
                List<NewsSignal> newsSignals;
                try {
                        newsSignals = newsResearcher.research(input);
                } catch (Exception e) {
                        log.warn("News research failed: {}", e.getMessage());
                        newsSignals = List.of();
                        reliabilityAlerts.add(systemAlert("News researcher unavailable", e.getMessage()));
                }
        log.info("News research complete: {} signals", newsSignals.size());

        // Step 4: Promoter Analysis
        log.info("Running promoter analysis...");
                List<PromoterProfile> promoterProfiles;
                try {
                        promoterProfiles = promoterAnalyzer.analyze(input);
                } catch (Exception e) {
                        log.warn("Promoter analysis failed: {}", e.getMessage());
                        promoterProfiles = List.of();
                        reliabilityAlerts.add(systemAlert("Promoter analyzer unavailable", e.getMessage()));
                }
        log.info("Promoter analysis complete: {} profiles", promoterProfiles.size());

        // Step 5: Risk Scoring
        log.info("Computing risk score...");
        ExternalRiskScore riskScore = riskScorer.computeScore(
                networkGraph, litigationResult.records(), litigationResult.actions(),
                newsSignals, promoterProfiles);

        List<RiskAlert> mergedAlerts = new ArrayList<>(riskScore.getAlerts() != null ? riskScore.getAlerts() : Collections.emptyList());
        mergedAlerts.addAll(reliabilityAlerts);
        riskScore.setAlerts(mergedAlerts);
        if (!reliabilityAlerts.isEmpty()) {
            riskScore.setSummary((riskScore.getSummary() == null ? "" : riskScore.getSummary() + " ")
                    + "System reliability note: one or more research engines were unavailable; manual verification recommended.");
        }

        log.info("Risk score: {} (Grade {})", riskScore.getCompositeScore(), riskScore.getGrade());

        return ResearchReport.builder()
                .input(input)
                .networkGraph(networkGraph)
                .litigationRecords(litigationResult.records())
                .regulatoryActions(litigationResult.actions())
                .newsSignals(newsSignals)
                .promoterProfiles(promoterProfiles)
                .riskScore(riskScore)
                .build();
    }

        private RiskAlert systemAlert(String title, String detail) {
                return RiskAlert.builder()
                                .category(AlertCategory.REGULATORY)
                                .severity(RiskLevel.MEDIUM)
                                .title(title)
                                .description("Reliability fallback activated: " + detail)
                                .source("System Reliability Guard")
                                .impact(0.3)
                                .build();
        }
}
