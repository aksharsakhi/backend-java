package com.credereai.auth.services;

import com.credereai.auth.models.AuthModels.AnalysisHistoryRecord;
import com.credereai.auth.models.AuthModels.SaveHistoryRequest;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class HistoryService {

    private final ObjectMapper objectMapper;
    private final Map<String, List<AnalysisHistoryRecord>> historyByUser = new ConcurrentHashMap<>();
    private final String storagePath = "data/analysis-history.json";

    public synchronized AnalysisHistoryRecord save(String username, SaveHistoryRequest request) {
        loadIfEmpty();

        Map<String, Object> fullAnalysis = safeMap(request.getFullAnalysis());
        Map<String, Object> dashboard = safeMap(request.getDashboard());
        Map<String, Object> underwriting = safeMap(request.getUnderwriting());

        Map<String, Object> consolidated = safeMap(fullAnalysis.get("consolidatedData"));
        Map<String, Object> completeness = safeMap(fullAnalysis.get("completeness"));

        String companyName = asString(dashboard.get("companyName"));
        if (companyName == null || companyName.isBlank()) {
            companyName = asString(consolidated.get("companyName"));
        }

        AnalysisHistoryRecord record = AnalysisHistoryRecord.builder()
                .id(UUID.randomUUID().toString())
                .username(username)
                .companyName(companyName == null || companyName.isBlank() ? "Unknown Company" : companyName)
                .decision(asString(underwriting.get("decision")))
                .riskBand(asString(underwriting.get("riskBand")))
                .completenessScore(asDouble(completeness.get("completenessScore")))
                .confidence(asDouble(consolidated.get("confidence")))
                .documentCount(request.getDocumentCount() == null ? 0 : request.getDocumentCount())
                .summary(buildSummary(dashboard, underwriting))
                .createdAt(LocalDateTime.now())
                .snapshot(buildSnapshot(fullAnalysis, dashboard, underwriting))
                .build();

        List<AnalysisHistoryRecord> entries = historyByUser.computeIfAbsent(username, k -> new ArrayList<>());
        entries.add(record);
        entries.sort(Comparator.comparing(AnalysisHistoryRecord::getCreatedAt).reversed());

        persist();
        return record;
    }

    public synchronized List<AnalysisHistoryRecord> list(String username, int limit) {
        loadIfEmpty();
        List<AnalysisHistoryRecord> entries = historyByUser.getOrDefault(username, List.of());
        if (limit <= 0) {
            return new ArrayList<>(entries);
        }
        return new ArrayList<>(entries.stream().limit(limit).toList());
    }

    public synchronized Optional<AnalysisHistoryRecord> getById(String username, String id) {
        loadIfEmpty();
        return historyByUser.getOrDefault(username, List.of()).stream()
                .filter(item -> item.getId().equals(id))
                .findFirst();
    }

    private String buildSummary(Map<String, Object> dashboard, Map<String, Object> underwriting) {
        Object rationaleObj = underwriting.get("rationale");
        if (rationaleObj instanceof List<?> rationale && !rationale.isEmpty()) {
            Object first = rationale.get(0);
            if (first != null) {
                return first.toString();
            }
        }

        Object insightsObj = dashboard.get("insights");
        if (insightsObj instanceof List<?> insights && !insights.isEmpty()) {
            Object first = insights.get(0);
            if (first != null) {
                return first.toString();
            }
        }

        return "Automated analysis completed.";
    }

    private Map<String, Object> buildSnapshot(Map<String, Object> fullAnalysis, Map<String, Object> dashboard, Map<String, Object> underwriting) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("fullAnalysis", fullAnalysis);
        out.put("dashboard", dashboard);
        out.put("underwriting", underwriting);
        return out;
    }

    private Map<String, Object> safeMap(Object value) {
        if (value instanceof Map<?, ?> mapValue) {
            Map<String, Object> out = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : mapValue.entrySet()) {
                if (entry.getKey() != null) {
                    out.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return out;
        }
        return new LinkedHashMap<>();
    }

    private String asString(Object value) {
        return value == null ? null : value.toString();
    }

    private Double asDouble(Object value) {
        if (value == null) return null;
        if (value instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(value.toString());
        } catch (Exception ignored) {
            return null;
        }
    }

    private void loadIfEmpty() {
        if (!historyByUser.isEmpty()) {
            return;
        }

        File file = new File(storagePath);
        if (!file.exists()) {
            return;
        }

        try {
            TypeReference<Map<String, List<AnalysisHistoryRecord>>> type = new TypeReference<>() {};
            Map<String, List<AnalysisHistoryRecord>> loaded = objectMapper.readValue(file, type);
            historyByUser.putAll(loaded);
        } catch (Exception e) {
            log.warn("Unable to load history file: {}", e.getMessage());
        }
    }

    private void persist() {
        File file = new File(storagePath);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, historyByUser);
        } catch (Exception e) {
            log.error("Unable to persist history file", e);
        }
    }
}
