package com.credereai.module1.services;

import com.credereai.module1.models.PolicyModels.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class RiskPolicyService {

    private final ObjectMapper objectMapper;
    private final String filePath = "data/risk-policy-store.json";

    private RiskPolicyStore store;

    public synchronized RiskPolicyVersion getActivePolicy() {
        ensureLoaded();
        return store.getVersions().get(store.getActiveVersionId());
    }

    public synchronized List<RiskPolicyVersion> listVersions() {
        ensureLoaded();
        return new ArrayList<>(store.getVersions().values());
    }

    public synchronized RiskPolicyVersion upsertPolicy(UpsertPolicyRequest request) {
        ensureLoaded();

        String versionId = safeText(request.getVersionId(), "POL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        String ruleId = safeText(request.getRuleId(), "RULE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        RiskPolicyRules incoming = request.getRules() == null ? defaultRules() : normalizeRules(request.getRules());

        RiskPolicyVersion version = RiskPolicyVersion.builder()
                .versionId(versionId)
                .ruleId(ruleId)
                .description(safeText(request.getDescription(), "Custom policy version"))
                .createdAt(LocalDateTime.now())
                .rules(incoming)
                .build();

        store.getVersions().put(versionId, version);

        if (Boolean.TRUE.equals(request.getActivate())) {
            activate(versionId);
        } else {
            persist();
        }

        return version;
    }

    public synchronized RiskPolicyVersion activate(String versionId) {
        ensureLoaded();
        if (!store.getVersions().containsKey(versionId)) {
            throw new IllegalArgumentException("Policy version not found: " + versionId);
        }

        String current = store.getActiveVersionId();
        if (current != null && !current.equals(versionId)) {
            store.getRollbackStack().add(current);
        }
        store.setActiveVersionId(versionId);
        persist();
        return store.getVersions().get(versionId);
    }

    public synchronized RiskPolicyVersion rollback() {
        ensureLoaded();
        if (store.getRollbackStack().isEmpty()) {
            throw new IllegalArgumentException("No rollback version available.");
        }

        String target = store.getRollbackStack().remove(store.getRollbackStack().size() - 1);
        store.setActiveVersionId(target);
        persist();
        return store.getVersions().get(target);
    }

    private void ensureLoaded() {
        if (store != null) {
            return;
        }

        File file = new File(filePath);
        if (!file.exists()) {
            store = defaultStore();
            persist();
            return;
        }

        try {
            store = objectMapper.readValue(file, RiskPolicyStore.class);
            if (store == null || store.getVersions() == null || store.getVersions().isEmpty()) {
                store = defaultStore();
                persist();
            }
        } catch (Exception e) {
            log.warn("Unable to load risk policy store; creating default policy. {}", e.getMessage());
            store = defaultStore();
            persist();
        }
    }

    private RiskPolicyStore defaultStore() {
        RiskPolicyVersion base = RiskPolicyVersion.builder()
                .versionId("BASE-1")
                .ruleId("UW-RULE-BASE-1")
                .description("Default underwriting policy")
                .createdAt(LocalDateTime.now())
                .rules(defaultRules())
                .build();

        Map<String, RiskPolicyVersion> versions = new LinkedHashMap<>();
        versions.put(base.getVersionId(), base);

        return RiskPolicyStore.builder()
                .activeVersionId(base.getVersionId())
                .rollbackStack(new ArrayList<>())
                .versions(versions)
                .build();
    }

    private RiskPolicyRules defaultRules() {
        return RiskPolicyRules.builder()
                .minConfidence(0.55)
                .minCompletenessScore(50.0)
                .gstDeviationHigh(0.35)
                .gstDeviationMedium(0.20)
                .approveScore(7)
                .approveWithConditionsScore(5)
                .referManualScore(3)
                .baseExposureRatio(0.15)
                .mediumRiskExposureMultiplier(0.75)
                .elevatedRiskExposureMultiplier(0.50)
                .lowSpreadBps(175.0)
                .mediumSpreadBps(275.0)
                .elevatedSpreadBps(400.0)
                .build();
    }

    private RiskPolicyRules normalizeRules(RiskPolicyRules r) {
        RiskPolicyRules d = defaultRules();
        return RiskPolicyRules.builder()
                .minConfidence(r.getMinConfidence() == null ? d.getMinConfidence() : r.getMinConfidence())
                .minCompletenessScore(r.getMinCompletenessScore() == null ? d.getMinCompletenessScore() : r.getMinCompletenessScore())
                .gstDeviationHigh(r.getGstDeviationHigh() == null ? d.getGstDeviationHigh() : r.getGstDeviationHigh())
                .gstDeviationMedium(r.getGstDeviationMedium() == null ? d.getGstDeviationMedium() : r.getGstDeviationMedium())
                .approveScore(r.getApproveScore() == null ? d.getApproveScore() : r.getApproveScore())
                .approveWithConditionsScore(r.getApproveWithConditionsScore() == null ? d.getApproveWithConditionsScore() : r.getApproveWithConditionsScore())
                .referManualScore(r.getReferManualScore() == null ? d.getReferManualScore() : r.getReferManualScore())
                .baseExposureRatio(r.getBaseExposureRatio() == null ? d.getBaseExposureRatio() : r.getBaseExposureRatio())
                .mediumRiskExposureMultiplier(r.getMediumRiskExposureMultiplier() == null ? d.getMediumRiskExposureMultiplier() : r.getMediumRiskExposureMultiplier())
                .elevatedRiskExposureMultiplier(r.getElevatedRiskExposureMultiplier() == null ? d.getElevatedRiskExposureMultiplier() : r.getElevatedRiskExposureMultiplier())
                .lowSpreadBps(r.getLowSpreadBps() == null ? d.getLowSpreadBps() : r.getLowSpreadBps())
                .mediumSpreadBps(r.getMediumSpreadBps() == null ? d.getMediumSpreadBps() : r.getMediumSpreadBps())
                .elevatedSpreadBps(r.getElevatedSpreadBps() == null ? d.getElevatedSpreadBps() : r.getElevatedSpreadBps())
                .build();
    }

    private void persist() {
        try {
            File file = new File(filePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, store);
        } catch (Exception e) {
            log.error("Unable to persist risk policy store", e);
        }
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
