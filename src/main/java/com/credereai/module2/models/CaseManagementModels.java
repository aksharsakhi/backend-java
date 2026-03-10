package com.credereai.module2.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public class CaseManagementModels {

    public enum CaseState {
        NEW,
        UNDER_REVIEW,
        ESCALATED,
        CLOSED
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CaseFile {
        private String caseId;
        private String companyName;
        private CaseState state;
        private String priority;
        private String createdBy;
        private String lastDecision;
        private String summary;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
        private List<String> alertTitles;
        private List<ActionItem> actions;
        private List<EvidenceItem> evidence;
        private Integer auditCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActionItem {
        private String actionId;
        private String title;
        private String owner;
        private Integer slaHours;
        private LocalDateTime dueAt;
        private String status;
        private String notes;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceItem {
        private String evidenceId;
        private String type;
        private String source;
        private String reference;
        private String description;
        private String createdBy;
        private LocalDateTime createdAt;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditRecord {
        private String auditId;
        private String caseId;
        private LocalDateTime at;
        private String actor;
        private String eventType;
        private Map<String, Object> details;
        private String previousHash;
        private String hash;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateCaseRequest {
        private String companyName;
        private String priority;
        private String summary;
        private List<String> alertTitles;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TransitionStateRequest {
        private String targetState;
        private String reason;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AssignActionRequest {
        private String title;
        private String owner;
        private Integer slaHours;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateActionRequest {
        private String owner;
        private String status;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AddEvidenceRequest {
        private String type;
        private String source;
        private String reference;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecordDecisionRequest {
        private String decision;
        private String notes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditVerificationResponse {
        private String caseId;
        private boolean valid;
        private Integer recordsChecked;
        private String failureAtAuditId;
        private String message;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OverdueActionView {
        private String caseId;
        private String companyName;
        private String actionId;
        private String title;
        private String owner;
        private LocalDateTime dueAt;
        private long overdueHours;
        private String status;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EscalationSweepResponse {
        private Integer overdueActions;
        private Integer escalatedCases;
        private List<String> escalatedCaseIds;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DecisionPackResponse {
        private CaseFile caseFile;
        private List<ActionItem> actions;
        private List<EvidenceItem> evidence;
        private List<AuditRecord> auditTrail;
        private AuditVerificationResponse auditVerification;
        private String generatedAt;
    }
}
