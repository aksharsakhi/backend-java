package com.credereai.module2.controller;

import com.credereai.auth.services.AuthService;
import com.credereai.module1.models.FinancialData.UploadedDocument;
import com.credereai.module1.services.PdfProcessorService;
import com.credereai.module2.models.CaseManagementModels.*;
import com.credereai.module2.models.ResearchData.*;
import com.credereai.module2.models.Responses.*;
import com.credereai.module2.services.CaseLifecycleService;
import com.credereai.module2.services.ResearchOrchestrator;
import com.credereai.module2.services.ResearchJobService;
import com.credereai.module2.services.ResearchJobService.ResearchJobStatus;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/module2")
@RequiredArgsConstructor
public class Module2Controller {

    private final ResearchOrchestrator researchOrchestrator;
    private final ResearchJobService researchJobService;
    private final CaseLifecycleService caseLifecycleService;
    private final AuthService authService;
    private final PdfProcessorService pdfProcessorService;

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "healthy", "module", "Module 2 - External Research Agent");
    }

    @PostMapping("/research")
    public ResponseEntity<ApiResponse<ResearchResponse>> research(@Valid @RequestBody ResearchInput input) {
        try {
            ResearchInput sanitized = sanitizeInput(input);
            if (sanitized.getCompanyName() == null || sanitized.getCompanyName().isBlank()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.<ResearchResponse>builder()
                                .success(false).message("Company name is required").build());
            }

            log.info("Research request for: {}", sanitized.getCompanyName());
            ResearchReport report = researchOrchestrator.conductResearch(sanitized);

            return ResponseEntity.ok(ApiResponse.<ResearchResponse>builder()
                    .success(true)
                    .message("Research completed successfully")
                    .data(ResearchResponse.builder().report(report).build())
                    .build());
        } catch (Exception e) {
            log.error("Research failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    ApiResponse.<ResearchResponse>builder()
                            .success(false).message("Research failed: " + e.getMessage()).build());
        }
    }

    @PostMapping("/research/async")
    public ApiResponse<ResearchJobStatus> researchAsync(@Valid @RequestBody ResearchInput input) {
        ResearchInput sanitized = sanitizeInput(input);
        ResearchJobStatus job = researchJobService.submit(sanitized);
        return ApiResponse.<ResearchJobStatus>builder()
                .success(true)
                .message("Research job submitted")
                .data(job)
                .build();
    }

    @GetMapping("/research/async/{jobId}")
    public ResponseEntity<ApiResponse<ResearchJobStatus>> getAsyncResearchStatus(@PathVariable String jobId) {
        ResearchJobStatus status = researchJobService.get(jobId);
        if (status == null) {
            return ResponseEntity.badRequest().body(ApiResponse.<ResearchJobStatus>builder()
                    .success(false)
                    .message("Job not found")
                    .build());
        }

        return ResponseEntity.ok(ApiResponse.<ResearchJobStatus>builder()
                .success(true)
                .message("Research job status fetched")
                .data(status)
                .build());
    }

    @GetMapping("/module1-data")
    public ResponseEntity<ApiResponse<Module1DataResponse>> getModule1Data() {
        try {
            // Read Module 1 state in-process to avoid fragile localhost/port coupling.
            List<UploadedDocument> docs = pdfProcessorService.getDocuments();

            String companyName = null;
            String cin = null;
            String industry = null;
            List<String> promoterNames = new java.util.ArrayList<>();
            List<String> directors = new java.util.ArrayList<>();

            for (UploadedDocument doc : docs) {
                if (doc == null || doc.getExtractedData() == null) continue;

                var extracted = doc.getExtractedData();
                if (companyName == null && extracted.getCompanyName() != null && !extracted.getCompanyName().isBlank()) {
                    companyName = extracted.getCompanyName();
                }
                if (cin == null && extracted.getCin() != null && !extracted.getCin().isBlank()) {
                    cin = extracted.getCin();
                }
                if (industry == null && extracted.getDocumentType() != null && !extracted.getDocumentType().isBlank()) {
                    industry = extracted.getDocumentType();
                }

                if (extracted.getPromoters() != null) {
                    extracted.getPromoters().stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .forEach(name -> {
                                if (!promoterNames.contains(name)) promoterNames.add(name);
                            });
                }

                if (extracted.getDirectors() != null) {
                    extracted.getDirectors().stream()
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .forEach(name -> {
                                if (!directors.contains(name)) directors.add(name);
                            });
                }
            }

            boolean noDocuments = docs.isEmpty();
            boolean noUsableContext = (companyName == null || companyName.isBlank())
                    && promoterNames.isEmpty()
                    && directors.isEmpty();

            if (noDocuments || noUsableContext) {
                return ResponseEntity.ok(ApiResponse.<Module1DataResponse>builder()
                        .success(false)
                        .message("Module 1 has no usable extracted profile yet. Upload and process financial documents first.")
                        .build());
            }

            return ResponseEntity.ok(ApiResponse.<Module1DataResponse>builder()
                    .success(true)
                    .message("Module 1 data retrieved")
                    .data(Module1DataResponse.builder()
                            .companyName(companyName)
                            .cin(cin)
                            .industry(industry)
                            .promoterNames(promoterNames)
                            .directors(directors)
                            .build())
                    .build());
        } catch (Exception e) {
            log.warn("Could not fetch Module 1 data: {}", e.getMessage());
            return ResponseEntity.ok(ApiResponse.<Module1DataResponse>builder()
                    .success(false)
                    .message("Module 1 data not available: " + e.getMessage())
                    .build());
        }
    }

        @PostMapping("/research/from-module1")
        public ResponseEntity<ApiResponse<ResearchResponse>> researchFromModule1() {
        try {
            ResponseEntity<ApiResponse<Module1DataResponse>> m1 = getModule1Data();
            Module1DataResponse seed = m1.getBody() != null ? m1.getBody().getData() : null;

            if (seed == null || seed.getCompanyName() == null || seed.getCompanyName().isBlank()) {
            return ResponseEntity.badRequest().body(
                ApiResponse.<ResearchResponse>builder()
                    .success(false)
                    .message("Module 1 does not have enough extracted data. Upload/process documents first.")
                    .build());
            }

            ResearchInput input = ResearchInput.builder()
                .companyName(seed.getCompanyName())
                .cin(seed.getCin())
                .industry(seed.getIndustry())
                .promoterNames(seed.getPromoterNames())
                .directors(seed.getDirectors())
                .build();

            input = sanitizeInput(input);

            ResearchReport report = researchOrchestrator.conductResearch(input);
            return ResponseEntity.ok(ApiResponse.<ResearchResponse>builder()
                .success(true)
                .message("Research completed from Module 1 pipeline")
                .data(ResearchResponse.builder().report(report).build())
                .build());
        } catch (Exception e) {
            log.error("Research from Module 1 failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                ApiResponse.<ResearchResponse>builder()
                    .success(false).message("Research failed: " + e.getMessage()).build());
        }
        }

    @PostMapping("/reset")
    public ApiResponse<Void> reset() {
        return ApiResponse.<Void>builder().success(true).message("Module 2 state reset").build();
    }

    @PostMapping("/cases")
    public ApiResponse<CaseFile> createCase(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @RequestBody CreateCaseRequest request) {
        String actor = authService.resolveUsername(token).orElse("system");
        CaseFile created = caseLifecycleService.createCase(request, actor);
        return ApiResponse.<CaseFile>builder()
                .success(true)
                .message("Case created")
                .data(created)
                .build();
    }

    @PostMapping("/cases/from-research")
    public ResponseEntity<ApiResponse<CaseFile>> createCaseFromResearch(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @Valid @RequestBody ResearchInput input) {
        ResearchInput sanitized = sanitizeInput(input);
        if (sanitized.getCompanyName() == null || sanitized.getCompanyName().isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.<CaseFile>builder()
                    .success(false)
                    .message("Company name is required")
                    .build());
        }

        ResearchReport report = researchOrchestrator.conductResearch(sanitized);
        String actor = authService.resolveUsername(token).orElse("system");

        List<String> alerts = report.getRiskScore() != null && report.getRiskScore().getAlerts() != null
                ? report.getRiskScore().getAlerts().stream().map(RiskAlert::getTitle).limit(20).toList()
                : List.of();

        String summary = report.getRiskScore() != null ? report.getRiskScore().getSummary() : "Research case created";

        CreateCaseRequest request = CreateCaseRequest.builder()
                .companyName(sanitized.getCompanyName())
                .priority(derivePriority(report))
                .summary(summary)
                .alertTitles(alerts)
                .build();

        CaseFile created = caseLifecycleService.createCase(request, actor);

        return ResponseEntity.ok(ApiResponse.<CaseFile>builder()
                .success(true)
                .message("Case created from research")
                .data(created)
                .build());
    }

    @GetMapping("/cases")
    public ApiResponse<List<CaseFile>> listCases() {
        return ApiResponse.<List<CaseFile>>builder()
                .success(true)
                .message("Case list fetched")
                .data(caseLifecycleService.listCases())
                .build();
    }

    @GetMapping("/cases/{caseId}")
    public ResponseEntity<ApiResponse<CaseFile>> getCase(@PathVariable String caseId) {
        return caseLifecycleService.getCase(caseId)
                .map(c -> ResponseEntity.ok(ApiResponse.<CaseFile>builder()
                        .success(true)
                        .message("Case fetched")
                        .data(c)
                        .build()))
                .orElseGet(() -> ResponseEntity.badRequest().body(ApiResponse.<CaseFile>builder()
                        .success(false)
                        .message("Case not found")
                        .build()));
    }

    @PostMapping("/cases/{caseId}/state")
    public ResponseEntity<ApiResponse<CaseFile>> transitionCaseState(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable String caseId,
            @RequestBody TransitionStateRequest request) {
        try {
            CaseState target = CaseState.valueOf(request.getTargetState().trim().toUpperCase(Locale.ROOT));
            String actor = authService.resolveUsername(token).orElse("system");
            CaseFile updated = caseLifecycleService.transitionState(caseId, target, request.getReason(), actor);

            return ResponseEntity.ok(ApiResponse.<CaseFile>builder()
                    .success(true)
                    .message("Case state updated")
                    .data(updated)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<CaseFile>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    @PostMapping("/cases/{caseId}/actions")
    public ResponseEntity<ApiResponse<ActionItem>> assignAction(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable String caseId,
            @RequestBody AssignActionRequest request) {
        try {
            String actor = authService.resolveUsername(token).orElse("system");
            ActionItem action = caseLifecycleService.assignAction(caseId, request, actor);
            return ResponseEntity.ok(ApiResponse.<ActionItem>builder()
                    .success(true)
                    .message("Action assigned")
                    .data(action)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<ActionItem>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    @PatchMapping("/cases/{caseId}/actions/{actionId}")
    public ResponseEntity<ApiResponse<ActionItem>> updateAction(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable String caseId,
            @PathVariable String actionId,
            @RequestBody UpdateActionRequest request) {
        try {
            String actor = authService.resolveUsername(token).orElse("system");
            ActionItem action = caseLifecycleService.updateAction(caseId, actionId, request, actor);
            return ResponseEntity.ok(ApiResponse.<ActionItem>builder()
                    .success(true)
                    .message("Action updated")
                    .data(action)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<ActionItem>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    @PostMapping("/cases/{caseId}/evidence")
    public ResponseEntity<ApiResponse<EvidenceItem>> addEvidence(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable String caseId,
            @RequestBody AddEvidenceRequest request) {
        try {
            String actor = authService.resolveUsername(token).orElse("system");
            EvidenceItem evidence = caseLifecycleService.addEvidence(caseId, request, actor);
            return ResponseEntity.ok(ApiResponse.<EvidenceItem>builder()
                    .success(true)
                    .message("Evidence added")
                    .data(evidence)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<EvidenceItem>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    @PostMapping("/cases/{caseId}/decision")
    public ResponseEntity<ApiResponse<CaseFile>> recordDecision(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable String caseId,
            @RequestBody RecordDecisionRequest request) {
        try {
            String actor = authService.resolveUsername(token).orElse("system");
            CaseFile updated = caseLifecycleService.recordDecision(caseId, request.getDecision(), request.getNotes(), actor);
            return ResponseEntity.ok(ApiResponse.<CaseFile>builder()
                    .success(true)
                    .message("Decision recorded")
                    .data(updated)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<CaseFile>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    @GetMapping("/cases/{caseId}/audit")
    public ResponseEntity<ApiResponse<List<AuditRecord>>> getAuditTrail(@PathVariable String caseId) {
        try {
            List<AuditRecord> trail = caseLifecycleService.getAuditTrail(caseId);
            return ResponseEntity.ok(ApiResponse.<List<AuditRecord>>builder()
                    .success(true)
                    .message("Audit trail fetched")
                    .data(trail)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<List<AuditRecord>>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    @GetMapping("/cases/{caseId}/audit/verify")
    public ResponseEntity<ApiResponse<AuditVerificationResponse>> verifyAuditTrail(@PathVariable String caseId) {
        try {
            AuditVerificationResponse verification = caseLifecycleService.verifyAuditTrail(caseId);
            return ResponseEntity.ok(ApiResponse.<AuditVerificationResponse>builder()
                    .success(true)
                    .message("Audit verification completed")
                    .data(verification)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<AuditVerificationResponse>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    @GetMapping("/cases/actions/overdue")
    public ApiResponse<List<OverdueActionView>> listOverdueActions() {
        return ApiResponse.<List<OverdueActionView>>builder()
                .success(true)
                .message("Overdue actions fetched")
                .data(caseLifecycleService.listOverdueActions())
                .build();
    }

    @PostMapping("/cases/escalation/sweep")
    public ApiResponse<EscalationSweepResponse> runEscalationSweep(
            @RequestHeader(value = "X-Auth-Token", required = false) String token) {
        String actor = authService.resolveUsername(token).orElse("system");
        EscalationSweepResponse result = caseLifecycleService.runEscalationSweep(actor);
        return ApiResponse.<EscalationSweepResponse>builder()
                .success(true)
                .message("Escalation sweep completed")
                .data(result)
                .build();
    }

    @GetMapping("/cases/{caseId}/decision-pack")
    public ResponseEntity<ApiResponse<DecisionPackResponse>> getDecisionPack(@PathVariable String caseId) {
        try {
            DecisionPackResponse pack = caseLifecycleService.getDecisionPack(caseId);
            return ResponseEntity.ok(ApiResponse.<DecisionPackResponse>builder()
                    .success(true)
                    .message("Decision pack generated")
                    .data(pack)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<DecisionPackResponse>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    private String derivePriority(ResearchReport report) {
        if (report == null || report.getRiskScore() == null) {
            return "MEDIUM";
        }

        double score = report.getRiskScore().getCompositeScore() == null ? 0.0 : report.getRiskScore().getCompositeScore();
        long severeAlerts = report.getRiskScore().getAlerts() == null ? 0
                : report.getRiskScore().getAlerts().stream()
                .filter(a -> a.getSeverity() == RiskLevel.HIGH || a.getSeverity() == RiskLevel.CRITICAL)
                .count();

        if (score >= 22 || severeAlerts >= 4) return "CRITICAL";
        if (score >= 14 || severeAlerts >= 2) return "HIGH";
        if (score >= 8) return "MEDIUM";
        return "LOW";
    }

    private ResearchInput sanitizeInput(ResearchInput input) {
        if (input == null) return ResearchInput.builder().build();

        String company = cleanText(input.getCompanyName(), 120);
        String cin = cleanCin(input.getCin());
        String industry = cleanText(input.getIndustry(), 80);
        String location = cleanText(input.getLocation(), 120);

        List<String> promoters = cleanNameList(input.getPromoterNames(), 20, 80);
        List<String> directors = cleanNameList(input.getDirectors(), 20, 80);

        return ResearchInput.builder()
                .companyName(company)
                .cin(cin)
                .industry(industry)
                .location(location)
                .promoterNames(promoters)
                .directors(directors)
                .build();
    }

    private String cleanText(String value, int maxLen) {
        if (value == null) return null;
        String cleaned = value.replaceAll("[\\p{Cntrl}]", " ").trim().replaceAll("\\s+", " ");
        if (cleaned.isBlank()) return null;
        return cleaned.length() > maxLen ? cleaned.substring(0, maxLen) : cleaned;
    }

    private String cleanCin(String value) {
        String cleaned = cleanText(value, 30);
        if (cleaned == null) return null;
        cleaned = cleaned.toUpperCase(Locale.ROOT);
        return cleaned.matches("[LU][0-9A-Z]{10,25}") ? cleaned : null;
    }

    private List<String> cleanNameList(List<String> names, int maxItems, int maxLen) {
        if (names == null) return List.of();
        return names.stream()
                .map(n -> cleanText(n, maxLen))
                .filter(n -> n != null && n.length() >= 3)
                .distinct()
                .limit(maxItems)
                .collect(Collectors.toList());
    }
}
