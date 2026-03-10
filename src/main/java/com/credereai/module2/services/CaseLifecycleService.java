package com.credereai.module2.services;

import com.credereai.module2.models.CaseManagementModels.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class CaseLifecycleService {

    private final ObjectMapper objectMapper;

    private final Map<String, CaseFile> cases = new ConcurrentHashMap<>();
    private final Map<String, List<AuditRecord>> auditByCase = new ConcurrentHashMap<>();

    private final String casesFilePath = "data/module2-cases.json";
    private final String auditFilePath = "data/module2-audit-log.jsonl";

    public synchronized CaseFile createCase(CreateCaseRequest request, String actor) {
        loadIfNeeded();

        String caseId = "CASE-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        LocalDateTime now = LocalDateTime.now();

        CaseFile caseFile = CaseFile.builder()
                .caseId(caseId)
                .companyName(safeText(request.getCompanyName(), "Unknown Company"))
                .state(CaseState.NEW)
                .priority(safeText(request.getPriority(), "MEDIUM"))
                .createdBy(safeText(actor, "system"))
                .lastDecision(null)
                .summary(safeText(request.getSummary(), "Case initiated."))
                .createdAt(now)
                .updatedAt(now)
                .alertTitles(request.getAlertTitles() == null ? new ArrayList<>() : new ArrayList<>(request.getAlertTitles()))
                .actions(new ArrayList<>())
                .evidence(new ArrayList<>())
                .auditCount(0)
                .build();

        cases.put(caseId, caseFile);
        appendAudit(caseId, safeText(actor, "system"), "CASE_CREATED", Map.of(
                "state", CaseState.NEW.name(),
                "priority", caseFile.getPriority(),
                "companyName", caseFile.getCompanyName()));
        persistCases();
        return cloneCase(caseFile);
    }

    public synchronized List<CaseFile> listCases() {
        loadIfNeeded();
        return cases.values().stream()
                .sorted(Comparator.comparing(CaseFile::getUpdatedAt).reversed())
                .map(this::cloneCase)
                .toList();
    }

    public synchronized Optional<CaseFile> getCase(String caseId) {
        loadIfNeeded();
        CaseFile caseFile = cases.get(caseId);
        return caseFile == null ? Optional.empty() : Optional.of(cloneCase(caseFile));
    }

    public synchronized CaseFile transitionState(String caseId, CaseState targetState, String reason, String actor) {
        loadIfNeeded();
        CaseFile caseFile = requireCase(caseId);
        enforceTransition(caseFile.getState(), targetState);

        CaseState from = caseFile.getState();
        caseFile.setState(targetState);
        caseFile.setUpdatedAt(LocalDateTime.now());

        appendAudit(caseId, safeText(actor, "system"), "STATE_TRANSITION", Map.of(
                "from", from.name(),
                "to", targetState.name(),
                "reason", safeText(reason, "not provided")));
        persistCases();
        return cloneCase(caseFile);
    }

    public synchronized ActionItem assignAction(String caseId, AssignActionRequest request, String actor) {
        loadIfNeeded();
        CaseFile caseFile = requireCase(caseId);

        int slaHours = request.getSlaHours() == null ? 48 : Math.max(1, request.getSlaHours());
        LocalDateTime now = LocalDateTime.now();
        ActionItem action = ActionItem.builder()
                .actionId("ACT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .title(safeText(request.getTitle(), "Follow-up action"))
                .owner(safeText(request.getOwner(), "unassigned"))
                .slaHours(slaHours)
                .dueAt(now.plusHours(slaHours))
                .status("OPEN")
                .notes(request.getNotes())
                .createdAt(now)
                .updatedAt(now)
                .build();

        caseFile.getActions().add(action);
        caseFile.setUpdatedAt(now);

        appendAudit(caseId, safeText(actor, "system"), "ACTION_ASSIGNED", Map.of(
                "actionId", action.getActionId(),
                "owner", action.getOwner(),
                "slaHours", action.getSlaHours(),
                "title", action.getTitle()));
        persistCases();
        return action;
    }

    public synchronized ActionItem updateAction(String caseId, String actionId, UpdateActionRequest request, String actor) {
        loadIfNeeded();
        CaseFile caseFile = requireCase(caseId);

        ActionItem action = caseFile.getActions().stream()
                .filter(a -> a.getActionId().equals(actionId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Action not found for this case."));

        if (request.getOwner() != null && !request.getOwner().isBlank()) {
            action.setOwner(request.getOwner().trim());
        }
        if (request.getStatus() != null && !request.getStatus().isBlank()) {
            action.setStatus(request.getStatus().trim().toUpperCase());
        }
        if (request.getNotes() != null && !request.getNotes().isBlank()) {
            action.setNotes(request.getNotes().trim());
        }
        action.setUpdatedAt(LocalDateTime.now());

        caseFile.setUpdatedAt(LocalDateTime.now());

        appendAudit(caseId, safeText(actor, "system"), "ACTION_UPDATED", Map.of(
                "actionId", actionId,
                "status", action.getStatus(),
                "owner", action.getOwner()));
        persistCases();
        return action;
    }

    public synchronized EvidenceItem addEvidence(String caseId, AddEvidenceRequest request, String actor) {
        loadIfNeeded();
        CaseFile caseFile = requireCase(caseId);

        EvidenceItem evidence = EvidenceItem.builder()
                .evidenceId("EVD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .type(safeText(request.getType(), "GENERIC"))
                .source(safeText(request.getSource(), "manual"))
                .reference(request.getReference())
                .description(safeText(request.getDescription(), "Evidence added"))
                .createdBy(safeText(actor, "system"))
                .createdAt(LocalDateTime.now())
                .build();

        caseFile.getEvidence().add(evidence);
        caseFile.setUpdatedAt(LocalDateTime.now());

        appendAudit(caseId, safeText(actor, "system"), "EVIDENCE_ADDED", Map.of(
                "evidenceId", evidence.getEvidenceId(),
                "type", evidence.getType(),
                "source", evidence.getSource()));
        persistCases();
        return evidence;
    }

    public synchronized CaseFile recordDecision(String caseId, String decision, String notes, String actor) {
        loadIfNeeded();
        CaseFile caseFile = requireCase(caseId);

        caseFile.setLastDecision(safeText(decision, "REFERRED"));
        caseFile.setUpdatedAt(LocalDateTime.now());

        appendAudit(caseId, safeText(actor, "system"), "DECISION_RECORDED", Map.of(
                "decision", caseFile.getLastDecision(),
                "notes", safeText(notes, "not provided")));

        if (!CaseState.CLOSED.equals(caseFile.getState())) {
            CaseState from = caseFile.getState();
            caseFile.setState(CaseState.CLOSED);
            appendAudit(caseId, safeText(actor, "system"), "STATE_TRANSITION", Map.of(
                    "from", from.name(),
                    "to", CaseState.CLOSED.name(),
                    "reason", "Decision recorded and case closed"));
        }

        persistCases();
        return cloneCase(caseFile);
    }

    public synchronized List<AuditRecord> getAuditTrail(String caseId) {
        loadIfNeeded();
        requireCase(caseId);
        return new ArrayList<>(auditByCase.getOrDefault(caseId, List.of()));
    }

    public synchronized AuditVerificationResponse verifyAuditTrail(String caseId) {
        loadIfNeeded();
        requireCase(caseId);

        List<AuditRecord> audits = auditByCase.getOrDefault(caseId, List.of());
        String prevHash = "GENESIS";

        for (AuditRecord record : audits) {
            String payload = record.getCaseId() + "|" + record.getAt() + "|" + record.getActor()
                    + "|" + record.getEventType() + "|" + record.getDetails();
            String expectedHash = sha256(prevHash + "|" + payload);

            if (!prevHash.equals(record.getPreviousHash()) || !expectedHash.equals(record.getHash())) {
                return AuditVerificationResponse.builder()
                        .caseId(caseId)
                        .valid(false)
                        .recordsChecked(audits.size())
                        .failureAtAuditId(record.getAuditId())
                        .message("Audit chain validation failed")
                        .build();
            }
            prevHash = record.getHash();
        }

        return AuditVerificationResponse.builder()
                .caseId(caseId)
                .valid(true)
                .recordsChecked(audits.size())
                .failureAtAuditId(null)
                .message("Audit chain valid")
                .build();
    }

    public synchronized List<OverdueActionView> listOverdueActions() {
        loadIfNeeded();
        LocalDateTime now = LocalDateTime.now();
        List<OverdueActionView> overdue = new ArrayList<>();

        for (CaseFile caseFile : cases.values()) {
            for (ActionItem action : caseFile.getActions() == null ? List.<ActionItem>of() : caseFile.getActions()) {
                if (action.getDueAt() == null) continue;
                String status = action.getStatus() == null ? "OPEN" : action.getStatus().toUpperCase();
                boolean stillOpen = !"CLOSED".equals(status) && !"DONE".equals(status);
                if (stillOpen && action.getDueAt().isBefore(now)) {
                    long overdueHours = Math.max(1, ChronoUnit.HOURS.between(action.getDueAt(), now));
                    overdue.add(OverdueActionView.builder()
                            .caseId(caseFile.getCaseId())
                            .companyName(caseFile.getCompanyName())
                            .actionId(action.getActionId())
                            .title(action.getTitle())
                            .owner(action.getOwner())
                            .dueAt(action.getDueAt())
                            .overdueHours(overdueHours)
                            .status(status)
                            .build());
                }
            }
        }

        overdue.sort(Comparator.comparingLong(OverdueActionView::getOverdueHours).reversed());
        return overdue;
    }

    public synchronized EscalationSweepResponse runEscalationSweep(String actor) {
        loadIfNeeded();
        List<OverdueActionView> overdue = listOverdueActions();
        List<String> escalated = new ArrayList<>();

        for (OverdueActionView item : overdue) {
            CaseFile caseFile = cases.get(item.getCaseId());
            if (caseFile == null) continue;
            if (caseFile.getState() == CaseState.NEW || caseFile.getState() == CaseState.CLOSED) {
                continue;
            }
            if (caseFile.getState() != CaseState.ESCALATED) {
                CaseState from = caseFile.getState();
                caseFile.setState(CaseState.ESCALATED);
                caseFile.setUpdatedAt(LocalDateTime.now());
                appendAudit(caseFile.getCaseId(), safeText(actor, "system"), "AUTO_ESCALATED", Map.of(
                        "from", from.name(),
                        "to", CaseState.ESCALATED.name(),
                        "reason", "Overdue SLA action detected"));
                escalated.add(caseFile.getCaseId());
            }
        }

        persistCases();
        return EscalationSweepResponse.builder()
                .overdueActions(overdue.size())
                .escalatedCases(escalated.size())
                .escalatedCaseIds(escalated)
                .build();
    }

    public synchronized DecisionPackResponse getDecisionPack(String caseId) {
        loadIfNeeded();
        CaseFile caseFile = requireCase(caseId);

        return DecisionPackResponse.builder()
                .caseFile(cloneCase(caseFile))
                .actions(caseFile.getActions() == null ? List.of() : new ArrayList<>(caseFile.getActions()))
                .evidence(caseFile.getEvidence() == null ? List.of() : new ArrayList<>(caseFile.getEvidence()))
                .auditTrail(getAuditTrail(caseId))
                .auditVerification(verifyAuditTrail(caseId))
                .generatedAt(LocalDateTime.now().toString())
                .build();
    }

    private void enforceTransition(CaseState from, CaseState to) {
        if (from == to) {
            return;
        }

        boolean valid = switch (from) {
            case NEW -> to == CaseState.UNDER_REVIEW;
            case UNDER_REVIEW -> to == CaseState.ESCALATED || to == CaseState.CLOSED;
            case ESCALATED -> to == CaseState.UNDER_REVIEW || to == CaseState.CLOSED;
            case CLOSED -> false;
        };

        if (!valid) {
            throw new IllegalArgumentException("Invalid state transition: " + from + " -> " + to);
        }
    }

    private CaseFile requireCase(String caseId) {
        CaseFile caseFile = cases.get(caseId);
        if (caseFile == null) {
            throw new IllegalArgumentException("Case not found: " + caseId);
        }
        return caseFile;
    }

    private void appendAudit(String caseId, String actor, String eventType, Map<String, Object> details) {
        List<AuditRecord> caseAudits = auditByCase.computeIfAbsent(caseId, k -> new ArrayList<>());
        String prevHash = caseAudits.isEmpty() ? "GENESIS" : caseAudits.get(caseAudits.size() - 1).getHash();
        LocalDateTime now = LocalDateTime.now();
        String payload = caseId + "|" + now + "|" + actor + "|" + eventType + "|" + details;
        String hash = sha256(prevHash + "|" + payload);

        AuditRecord record = AuditRecord.builder()
                .auditId("AUD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase())
                .caseId(caseId)
                .at(now)
                .actor(actor)
                .eventType(eventType)
                .details(new LinkedHashMap<>(details))
                .previousHash(prevHash)
                .hash(hash)
                .build();

        caseAudits.add(record);

        CaseFile caseFile = cases.get(caseId);
        if (caseFile != null) {
            caseFile.setAuditCount(caseAudits.size());
        }

        appendAuditLine(record);
    }

    private void appendAuditLine(AuditRecord record) {
        try {
            File file = new File(auditFilePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            String line = objectMapper.writeValueAsString(record) + System.lineSeparator();
            try (FileWriter fw = new FileWriter(file, StandardCharsets.UTF_8, true)) {
                fw.write(line);
            }
        } catch (Exception e) {
            log.error("Failed to append immutable audit log", e);
        }
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to hash audit payload", e);
        }
    }

    private void loadIfNeeded() {
        if (!cases.isEmpty()) {
            return;
        }

        File casesFile = new File(casesFilePath);
        if (!casesFile.exists()) {
            return;
        }

        try {
            TypeReference<Map<String, CaseFile>> type = new TypeReference<>() {};
            Map<String, CaseFile> loaded = objectMapper.readValue(casesFile, type);
            cases.putAll(loaded);
            for (CaseFile caseFile : loaded.values()) {
                auditByCase.putIfAbsent(caseFile.getCaseId(), new ArrayList<>());
            }
        } catch (Exception e) {
            log.warn("Unable to load persisted cases: {}", e.getMessage());
        }
    }

    private void persistCases() {
        try {
            File file = new File(casesFilePath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(file, cases);
        } catch (Exception e) {
            log.error("Failed to persist cases", e);
        }
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }

    private CaseFile cloneCase(CaseFile caseFile) {
        return CaseFile.builder()
                .caseId(caseFile.getCaseId())
                .companyName(caseFile.getCompanyName())
                .state(caseFile.getState())
                .priority(caseFile.getPriority())
                .createdBy(caseFile.getCreatedBy())
                .lastDecision(caseFile.getLastDecision())
                .summary(caseFile.getSummary())
                .createdAt(caseFile.getCreatedAt())
                .updatedAt(caseFile.getUpdatedAt())
                .alertTitles(caseFile.getAlertTitles() == null ? List.of() : new ArrayList<>(caseFile.getAlertTitles()))
                .actions(caseFile.getActions() == null ? List.of() : new ArrayList<>(caseFile.getActions()))
                .evidence(caseFile.getEvidence() == null ? List.of() : new ArrayList<>(caseFile.getEvidence()))
                .auditCount(caseFile.getAuditCount())
                .build();
    }
}
