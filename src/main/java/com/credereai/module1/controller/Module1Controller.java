package com.credereai.module1.controller;

import com.credereai.llm.LlmProviderConfig;
import com.credereai.llm.LlmRuntimeSettingsService;
import com.credereai.auth.services.AuthService;
import com.credereai.module1.config.Module1Config;
import com.credereai.module1.models.PolicyModels.*;
import com.credereai.module1.models.FinancialData.UploadedDocument;
import com.credereai.module1.models.Responses.*;
import com.credereai.module1.services.PdfProcessorService;
import com.credereai.module1.services.RiskPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class Module1Controller {

    private final PdfProcessorService pdfProcessorService;
    private final LlmProviderConfig llmProviderConfig;
    private final LlmRuntimeSettingsService llmRuntimeSettingsService;
    private final Module1Config module1Config;
    private final AuthService authService;
    private final RiskPolicyService riskPolicyService;

    @GetMapping("/")
    public Map<String, Object> root() {
        return Map.of(
                "name", "Credere AI - Financial Analysis API",
                "version", "2.0.0",
                "modules", List.of("Module 1: Document Intelligence", "Module 2: Research Agent")
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "healthy", "module", "Module 1 - Document Intelligence Engine");
    }

    @GetMapping("/system/llm-provider")
    public Map<String, String> llmProvider() {
        LlmRuntimeSettingsService.SettingsSnapshot settings = llmRuntimeSettingsService.snapshot();
        return Map.of(
            "provider", settings.provider(),
            "activeModel", settings.activeModel(),
            "geminiModel", settings.geminiModel(),
            "groqModel", settings.groqModel(),
            "defaultProvider", llmProviderConfig.getProvider()
        );
        }

        @GetMapping("/system/llm-provider/settings")
        public Map<String, Object> llmProviderSettings() {
        LlmRuntimeSettingsService.SettingsSnapshot settings = llmRuntimeSettingsService.snapshot();
        return Map.of(
            "provider", settings.provider(),
            "activeModel", settings.activeModel(),
            "geminiModel", settings.geminiModel(),
            "groqModel", settings.groqModel(),
            "availableProviders", settings.availableProviders(),
            "modelsByProvider", settings.modelsByProvider()
        );
        }

        @PostMapping("/system/llm-provider/settings")
        public Map<String, Object> updateLlmProviderSettings(@RequestBody Map<String, String> request) {
        String provider = request.getOrDefault("provider", "gemini");
        String model = request.getOrDefault("model", "");
        LlmRuntimeSettingsService.SettingsSnapshot settings = llmRuntimeSettingsService.update(provider, model);
        return Map.of(
            "provider", settings.provider(),
            "activeModel", settings.activeModel(),
            "geminiModel", settings.geminiModel(),
            "groqModel", settings.groqModel(),
            "availableProviders", settings.availableProviders(),
            "modelsByProvider", settings.modelsByProvider(),
            "message", "LLM runtime settings updated"
        );
    }

    @PostMapping("/upload")
    public ResponseEntity<ApiResponse<ExtractionResponse>> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "annual_report") String category) {
        try {
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.<ExtractionResponse>builder().success(false).message("No file provided").build());
            }

            String filename = file.getOriginalFilename();
            if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
                return ResponseEntity.badRequest().body(
                        ApiResponse.<ExtractionResponse>builder().success(false).message("Only PDF files are accepted").build());
            }

                long maxBytes = (long) module1Config.getMaxFileSizeMb() * 1024 * 1024;
                if (file.getSize() > maxBytes) {
                return ResponseEntity.badRequest().body(
                    ApiResponse.<ExtractionResponse>builder()
                        .success(false)
                        .message("File too large. Max allowed size is " + module1Config.getMaxFileSizeMb() + "MB")
                        .build());
                }

            log.info("Uploading: {} (category: {})", filename, category);
            ExtractionResponse result = pdfProcessorService.uploadAndProcess(file, category);

            return ResponseEntity.ok(ApiResponse.<ExtractionResponse>builder()
                    .success(true)
                    .message("Document processed successfully")
                    .data(result)
                    .build());
        } catch (Exception e) {
            log.error("Upload failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(
                    ApiResponse.<ExtractionResponse>builder().success(false)
                            .message("Processing failed: " + e.getMessage()).build());
        }
    }

    @GetMapping("/documents")
    public ApiResponse<List<UploadedDocument>> getDocuments() {
        List<UploadedDocument> docs = pdfProcessorService.getDocuments();
        return ApiResponse.<List<UploadedDocument>>builder()
                .success(true)
                .message(docs.size() + " document(s) found")
                .data(docs)
                .build();
    }

    @DeleteMapping("/documents/{documentId}")
    public ApiResponse<Void> deleteDocument(@PathVariable String documentId) {
        boolean deleted = pdfProcessorService.deleteDocument(documentId);
        return ApiResponse.<Void>builder()
                .success(deleted)
                .message(deleted ? "Document deleted" : "Document not found")
                .build();
    }

    @GetMapping("/completeness")
    public ApiResponse<CompletenessResponse> getCompleteness() {
        CompletenessResponse result = pdfProcessorService.runCompletenessCheck();
        return ApiResponse.<CompletenessResponse>builder()
                .success(true).message("Completeness check completed").data(result).build();
    }

    @GetMapping("/analysis/trends")
    public ApiResponse<TrendAnalysisResponse> getTrends() {
        TrendAnalysisResponse result = pdfProcessorService.runTrendAnalysis();
        return ApiResponse.<TrendAnalysisResponse>builder()
                .success(true).message("Trend analysis completed").data(result).build();
    }

    @GetMapping("/analysis/cross-verify")
    public ApiResponse<CrossVerificationResponse> crossVerify() {
        CrossVerificationResponse result = pdfProcessorService.runCrossVerification();
        return ApiResponse.<CrossVerificationResponse>builder()
                .success(true).message("Cross-verification completed").data(result).build();
    }

    @GetMapping("/analysis/ratios")
    public ApiResponse<RatioAnalysisResponse> getRatios() {
        RatioAnalysisResponse result = pdfProcessorService.runRatioAnalysis();
        return ApiResponse.<RatioAnalysisResponse>builder()
                .success(true).message("Ratio analysis completed").data(result).build();
    }

    @GetMapping("/analysis/full")
    public ApiResponse<FullAnalysisResponse> getFullAnalysis() {
        FullAnalysisResponse result = pdfProcessorService.runFullAnalysis();
        return ApiResponse.<FullAnalysisResponse>builder()
                .success(true).message("Full analysis completed").data(result).build();
    }

    @GetMapping("/analysis/dashboard")
    public ApiResponse<DashboardResponse> getDashboardAnalysis() {
        DashboardResponse result = pdfProcessorService.runDashboardAnalysis();
        return ApiResponse.<DashboardResponse>builder()
                .success(true).message("Dashboard analysis completed").data(result).build();
    }

    @GetMapping("/analysis/underwriting")
    public ApiResponse<UnderwritingResponse> getUnderwritingAnalysis() {
        UnderwritingResponse result = pdfProcessorService.runUnderwritingAssessment();
        return ApiResponse.<UnderwritingResponse>builder()
                .success(true).message("Underwriting assessment completed").data(result).build();
    }

        @GetMapping("/analysis/enterprise")
        public ApiResponse<EnterpriseAssessmentResponse> getEnterpriseAssessment() {
        EnterpriseAssessmentResponse result = pdfProcessorService.runEnterpriseAssessment();
        return ApiResponse.<EnterpriseAssessmentResponse>builder()
            .success(true)
            .message("Enterprise assessment completed")
            .data(result)
            .build();
        }

        @GetMapping("/analysis/recommendation-engine")
        public ApiResponse<RecommendationEngineResponse> getRecommendationEngine() {
            RecommendationEngineResponse result = pdfProcessorService.runRecommendationEngine();
            return ApiResponse.<RecommendationEngineResponse>builder()
                    .success(true)
                    .message("Recommendation engine completed")
                    .data(result)
                    .build();
        }

        @GetMapping("/analysis/review-workflow")
        public ApiResponse<ReviewWorkflowResponse> getReviewWorkflow() {
        return ApiResponse.<ReviewWorkflowResponse>builder()
            .success(true)
            .message("Review workflow status fetched")
            .data(pdfProcessorService.getReviewWorkflow())
            .build();
        }

        @PostMapping("/analysis/review/submit")
        public ApiResponse<ReviewWorkflowResponse> submitForReview(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @RequestBody(required = false) ReviewWorkflowRequest request) {
        String actor = authService.resolveUsername(token).orElse("system");
        String notes = request != null ? request.getNotes() : null;
        return ApiResponse.<ReviewWorkflowResponse>builder()
            .success(true)
            .message("Case submitted for approval")
            .data(pdfProcessorService.submitForReview(actor, notes))
            .build();
        }

        @PostMapping("/analysis/review/approve")
        public ApiResponse<ReviewWorkflowResponse> approveReview(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @RequestBody(required = false) ReviewWorkflowRequest request) {
        String actor = authService.resolveUsername(token).orElse("approver");
        String notes = request != null ? request.getNotes() : null;
        return ApiResponse.<ReviewWorkflowResponse>builder()
            .success(true)
            .message("Case approved")
            .data(pdfProcessorService.approveReview(actor, notes))
            .build();
        }

    @PostMapping("/reset")
    public ApiResponse<Void> reset() {
        pdfProcessorService.reset();
        return ApiResponse.<Void>builder()
                .success(true).message("All data reset successfully").build();
    }

    @GetMapping("/policy/risk")
    public ApiResponse<RiskPolicyVersion> getActiveRiskPolicy() {
        return ApiResponse.<RiskPolicyVersion>builder()
                .success(true)
                .message("Active risk policy fetched")
                .data(riskPolicyService.getActivePolicy())
                .build();
    }

    @GetMapping("/policy/risk/versions")
    public ApiResponse<List<RiskPolicyVersion>> listRiskPolicyVersions() {
        return ApiResponse.<List<RiskPolicyVersion>>builder()
                .success(true)
                .message("Risk policy versions fetched")
                .data(riskPolicyService.listVersions())
                .build();
    }

    @PostMapping("/policy/risk/versions")
    public ApiResponse<RiskPolicyVersion> upsertRiskPolicy(@RequestBody UpsertPolicyRequest request) {
        RiskPolicyVersion version = riskPolicyService.upsertPolicy(request);
        return ApiResponse.<RiskPolicyVersion>builder()
                .success(true)
                .message("Risk policy version saved")
                .data(version)
                .build();
    }

    @PostMapping("/policy/risk/activate/{versionId}")
    public ResponseEntity<ApiResponse<RiskPolicyVersion>> activateRiskPolicy(@PathVariable String versionId) {
        try {
            RiskPolicyVersion version = riskPolicyService.activate(versionId);
            return ResponseEntity.ok(ApiResponse.<RiskPolicyVersion>builder()
                    .success(true)
                    .message("Risk policy activated")
                    .data(version)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<RiskPolicyVersion>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }

    @PostMapping("/policy/risk/rollback")
    public ResponseEntity<ApiResponse<RiskPolicyVersion>> rollbackRiskPolicy() {
        try {
            RiskPolicyVersion version = riskPolicyService.rollback();
            return ResponseEntity.ok(ApiResponse.<RiskPolicyVersion>builder()
                    .success(true)
                    .message("Risk policy rollback complete")
                    .data(version)
                    .build());
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<RiskPolicyVersion>builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        }
    }
}
