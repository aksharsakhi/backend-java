package com.credereai.module1.services;

import com.credereai.module1.config.Module1Config;
import com.credereai.module1.engines.*;
import com.credereai.module1.models.FinancialData;
import com.credereai.module1.models.FinancialData.*;
import com.credereai.module1.models.PolicyModels.RiskPolicyRules;
import com.credereai.module1.models.PolicyModels.RiskPolicyVersion;
import com.credereai.module1.models.Responses.*;
import com.credereai.module1.utils.PdfUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PdfProcessorService {

    private final Module1Config config;
    private final PdfUtils pdfUtils;
    private final UnderstandingEngine understandingEngine;
    private final DocumentValidator documentValidator;
    private final GstBankVerifier gstBankVerifier;
    private final DebtCashflowAnalyzer debtCashflowAnalyzer;
    private final TableParser tableParser;
    private final RiskPolicyService riskPolicyService;

    private final Map<String, UploadedDocument> documents = new ConcurrentHashMap<>();
        private volatile ReviewWorkflowResponse reviewWorkflow = ReviewWorkflowResponse.builder()
            .status("DRAFT")
            .build();

    public ExtractionResponse uploadAndProcess(MultipartFile file, String category) throws IOException {
        validateUploadInput(file, category);

        String documentId = UUID.randomUUID().toString();
        String filename = file.getOriginalFilename();

        // Save file
        File uploadDir = new File(config.getUploadDir());
        uploadDir.mkdirs();
        File savedFile = new File(uploadDir, documentId + "_" + filename);
        file.transferTo(savedFile);

        int pageCount = pdfUtils.getPageCount(savedFile);
        String text = extractTextForProcessing(savedFile, pageCount);

        if (pageCount > 1500) {
            throw new IllegalArgumentException("Document exceeds safe processing page limit (1500 pages).");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("No extractable text found in the uploaded PDF.");
        }

        log.info("Processing {} - {} pages, {} chars", filename, pageCount, text.length());

        // Extract financial data (disable heavy LLM path for very large reports to keep uploads responsive)
        boolean fastMode = pageCount > 250 || text.length() > 220_000;
        ExtractedFinancialData extractedData = understandingEngine.extractFinancialData(text, category, !fastMode);
        extractedData.setSourceDocument(filename);
        if (fastMode) {
            List<String> warnings = extractedData.getWarnings() == null ? new ArrayList<>() : new ArrayList<>(extractedData.getWarnings());
            warnings.add("Fast-mode extraction enabled for large document; deterministic parser prioritized.");
            extractedData.setWarnings(warnings);
        }

        // Retry extraction with section-focused text for long reports if core fields are missing.
        if (countCoreFields(extractedData) < 3) {
            Map<String, String> sections = pdfUtils.extractFinancialSections(text);
            String focusedText = buildFocusedText(text, sections);
            ExtractedFinancialData retryData = understandingEngine.extractFinancialData(focusedText, category, !fastMode);
            retryData.setSourceDocument(filename);
            extractedData = mergeExtractedData(extractedData, retryData);
        }

        // Store document
        UploadedDocument doc = UploadedDocument.builder()
                .id(documentId)
                .filename(filename)
                .category(category)
                .fileSize(file.getSize())
                .pageCount(pageCount)
                .textLength(text.length())
                .uploadedAt(LocalDateTime.now())
                .extractedData(extractedData)
                .build();
        documents.put(documentId, doc);

        return ExtractionResponse.builder()
                .documentId(documentId)
                .filename(filename)
                .category(category)
                .pageCount(pageCount)
                .textLength(text.length())
                .extractedData(extractedData)
                .build();
    }

    private String extractTextForProcessing(File file, int pageCount) throws IOException {
        // Large annual reports can be slow to fully OCR/parse; sample key page windows for fast reliable extraction.
        if (pageCount > 250) {
            int window = 40;
            StringBuilder sampled = new StringBuilder();

            sampled.append(pdfUtils.extractTextFromPdf(file, 1, Math.min(window, pageCount)));

            if (pageCount > window * 2) {
                int midStart = Math.max(1, (pageCount / 2) - (window / 2));
                int midEnd = Math.min(pageCount, midStart + window);
                sampled.append("\n\n").append(pdfUtils.extractTextFromPdf(file, midStart, midEnd));
            }

            if (pageCount > window) {
                int endStart = Math.max(1, pageCount - window + 1);
                sampled.append("\n\n").append(pdfUtils.extractTextFromPdf(file, endStart, pageCount));
            }

            return sampled.toString();
        }

        return pdfUtils.extractTextFromPdf(file);
    }

    private void validateUploadInput(MultipartFile file, String category) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Upload failed: file is empty.");
        }

        String safeCategory = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        if (!Module1Config.DOCUMENT_CATEGORIES.containsKey(safeCategory)) {
            throw new IllegalArgumentException("Invalid document category provided.");
        }

        String name = file.getOriginalFilename();
        if (name == null || !name.toLowerCase(Locale.ROOT).endsWith(".pdf")) {
            throw new IllegalArgumentException("Only PDF files are accepted.");
        }

        long maxBytes = (long) config.getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("File exceeds configured upload limit of " + config.getMaxFileSizeMb() + "MB.");
        }
    }

    private String buildFocusedText(String fullText, Map<String, String> sections) {
        StringBuilder sb = new StringBuilder();

        // Prioritize key financial sections when available.
        for (String key : List.of("profit_loss", "balance_sheet", "cash_flow", "notes")) {
            String part = sections.get(key);
            if (part != null && !part.isBlank()) {
                sb.append("\n\n### ").append(key).append("\n").append(part);
            }
        }

        // Add sampled windows from start/middle/end for large PDFs.
        int n = fullText.length();
        int chunk = Math.min(120_000, n);
        if (n > 0) {
            sb.append("\n\n### start_chunk\n").append(fullText.substring(0, chunk));
        }
        if (n > chunk * 2) {
            int midStart = Math.max(0, n / 2 - chunk / 2);
            int midEnd = Math.min(n, midStart + chunk);
            sb.append("\n\n### middle_chunk\n").append(fullText, midStart, midEnd);
        }
        if (n > chunk) {
            sb.append("\n\n### end_chunk\n").append(fullText.substring(Math.max(0, n - chunk)));
        }

        return sb.toString();
    }

    private int countCoreFields(ExtractedFinancialData d) {
        int c = 0;
        if (d.getRevenue() != null) c++;
        if (d.getProfit() != null) c++;
        if (d.getTotalDebt() != null) c++;
        if (d.getTotalAssets() != null) c++;
        if (d.getEquity() != null) c++;
        return c;
    }

    private ExtractedFinancialData mergeExtractedData(ExtractedFinancialData base, ExtractedFinancialData retry) {
        ExtractedFinancialData out = copyExtractedData(base);

        if (out.getCompanyName() == null) out.setCompanyName(retry.getCompanyName());
        if (out.getFinancialYear() == null) out.setFinancialYear(retry.getFinancialYear());
        if (out.getRevenue() == null) out.setRevenue(retry.getRevenue());
        if (out.getProfit() == null) out.setProfit(retry.getProfit());
        if (out.getTotalDebt() == null) out.setTotalDebt(retry.getTotalDebt());
        if (out.getTotalAssets() == null) out.setTotalAssets(retry.getTotalAssets());
        if (out.getTotalLiabilities() == null) out.setTotalLiabilities(retry.getTotalLiabilities());
        if (out.getCashFlow() == null) out.setCashFlow(retry.getCashFlow());
        if (out.getEquity() == null) out.setEquity(retry.getEquity());
        if (out.getInterestExpense() == null) out.setInterestExpense(retry.getInterestExpense());
        if (out.getEbit() == null) out.setEbit(retry.getEbit());
        if (out.getCurrentAssets() == null) out.setCurrentAssets(retry.getCurrentAssets());
        if (out.getCurrentLiabilities() == null) out.setCurrentLiabilities(retry.getCurrentLiabilities());
        if (out.getPrevRevenue() == null) out.setPrevRevenue(retry.getPrevRevenue());
        if (out.getPrevProfit() == null) out.setPrevProfit(retry.getPrevProfit());
        if (out.getPrevTotalDebt() == null) out.setPrevTotalDebt(retry.getPrevTotalDebt());
        if (out.getCin() == null) out.setCin(retry.getCin());
        if (out.getRegisteredAddress() == null) out.setRegisteredAddress(retry.getRegisteredAddress());
        if (out.getAuditor() == null) out.setAuditor(retry.getAuditor());
        if (out.getDirectors() == null || out.getDirectors().isEmpty()) out.setDirectors(retry.getDirectors());
        if (out.getPromoters() == null || out.getPromoters().isEmpty()) out.setPromoters(retry.getPromoters());

        if (out.getExtractedTables() == null) out.setExtractedTables(new ArrayList<>());
        if (retry.getExtractedTables() != null) out.getExtractedTables().addAll(retry.getExtractedTables());

        return out;
    }

    private ExtractedFinancialData copyExtractedData(ExtractedFinancialData src) {
        if (src == null) return ExtractedFinancialData.builder().build();
        return ExtractedFinancialData.builder()
                .companyName(src.getCompanyName())
                .financialYear(src.getFinancialYear())
                .documentType(src.getDocumentType())
                .sourceDocument(src.getSourceDocument())
                .revenue(src.getRevenue())
                .profit(src.getProfit())
                .totalDebt(src.getTotalDebt())
                .totalAssets(src.getTotalAssets())
                .totalLiabilities(src.getTotalLiabilities())
                .cashFlow(src.getCashFlow())
                .equity(src.getEquity())
                .interestExpense(src.getInterestExpense())
                .ebit(src.getEbit())
                .currentAssets(src.getCurrentAssets())
                .currentLiabilities(src.getCurrentLiabilities())
                .gstRevenue(src.getGstRevenue())
                .bankInflow(src.getBankInflow())
                .bankOutflow(src.getBankOutflow())
                .prevRevenue(src.getPrevRevenue())
                .prevProfit(src.getPrevProfit())
                .prevTotalDebt(src.getPrevTotalDebt())
                .cin(src.getCin())
                .registeredAddress(src.getRegisteredAddress())
                .auditor(src.getAuditor())
                .directors(src.getDirectors() == null ? null : new ArrayList<>(src.getDirectors()))
                .promoters(src.getPromoters() == null ? null : new ArrayList<>(src.getPromoters()))
                .promoterHolding(src.getPromoterHolding())
                .confidence(src.getConfidence())
                .warnings(src.getWarnings() == null ? null : new ArrayList<>(src.getWarnings()))
                .extractedTables(src.getExtractedTables() == null ? null : new ArrayList<>(src.getExtractedTables()))
                .build();
    }

    public List<UploadedDocument> getDocuments() {
        return new ArrayList<>(documents.values());
    }

    public boolean deleteDocument(String documentId) {
        UploadedDocument removed = documents.remove(documentId);
        if (removed != null) {
            // Delete file
            File uploadDir = new File(config.getUploadDir());
            File[] files = uploadDir.listFiles((dir, name) -> name.startsWith(documentId));
            if (files != null) {
                for (File f : files) f.delete();
            }
            return true;
        }
        return false;
    }

    public ExtractedFinancialData getConsolidatedFinancials() {
        if (documents.isEmpty()) return null;

        // Merge data from all documents, prioritizing non-null values
        ExtractedFinancialData consolidated = ExtractedFinancialData.builder()
                .warnings(new ArrayList<>())
                .extractedTables(new ArrayList<>())
                .build();

        int confidenceCount = 0;
        double confidenceSum = 0.0;

        for (UploadedDocument doc : documents.values()) {
            ExtractedFinancialData d = doc.getExtractedData();
            if (d == null) continue;

            if (consolidated.getCompanyName() == null) consolidated.setCompanyName(d.getCompanyName());
            if (consolidated.getFinancialYear() == null) consolidated.setFinancialYear(d.getFinancialYear());
            if (consolidated.getRevenue() == null) consolidated.setRevenue(d.getRevenue());
            if (consolidated.getProfit() == null) consolidated.setProfit(d.getProfit());
            if (consolidated.getTotalDebt() == null) consolidated.setTotalDebt(d.getTotalDebt());
            if (consolidated.getTotalAssets() == null) consolidated.setTotalAssets(d.getTotalAssets());
            if (consolidated.getTotalLiabilities() == null) consolidated.setTotalLiabilities(d.getTotalLiabilities());
            if (consolidated.getCashFlow() == null) consolidated.setCashFlow(d.getCashFlow());
            if (consolidated.getEquity() == null) consolidated.setEquity(d.getEquity());
            if (consolidated.getInterestExpense() == null) consolidated.setInterestExpense(d.getInterestExpense());
            if (consolidated.getEbit() == null) consolidated.setEbit(d.getEbit());
            if (consolidated.getCurrentAssets() == null) consolidated.setCurrentAssets(d.getCurrentAssets());
            if (consolidated.getCurrentLiabilities() == null) consolidated.setCurrentLiabilities(d.getCurrentLiabilities());
            if (consolidated.getGstRevenue() == null) consolidated.setGstRevenue(d.getGstRevenue());
            if (consolidated.getBankInflow() == null) consolidated.setBankInflow(d.getBankInflow());
            if (consolidated.getBankOutflow() == null) consolidated.setBankOutflow(d.getBankOutflow());
            if (consolidated.getPrevRevenue() == null) consolidated.setPrevRevenue(d.getPrevRevenue());
            if (consolidated.getPrevProfit() == null) consolidated.setPrevProfit(d.getPrevProfit());
            if (consolidated.getPrevTotalDebt() == null) consolidated.setPrevTotalDebt(d.getPrevTotalDebt());
            if (consolidated.getCin() == null) consolidated.setCin(d.getCin());
            if (consolidated.getRegisteredAddress() == null) consolidated.setRegisteredAddress(d.getRegisteredAddress());
            if (consolidated.getAuditor() == null) consolidated.setAuditor(d.getAuditor());
            if (consolidated.getDirectors() == null) consolidated.setDirectors(d.getDirectors());
            if (consolidated.getPromoters() == null) consolidated.setPromoters(d.getPromoters());
            if (consolidated.getPromoterHolding() == null) consolidated.setPromoterHolding(d.getPromoterHolding());

            if (d.getConfidence() != null) {
                confidenceCount++;
                confidenceSum += d.getConfidence();
            }

            if (d.getWarnings() != null) {
                consolidated.getWarnings().addAll(d.getWarnings());
            }

            if (d.getExtractedTables() != null) consolidated.getExtractedTables().addAll(d.getExtractedTables());
        }

        if (confidenceCount > 0) {
            consolidated.setConfidence(Math.round((confidenceSum / confidenceCount) * 100.0) / 100.0);
        }

        if (consolidated.getWarnings() != null) {
            LinkedHashSet<String> unique = new LinkedHashSet<>(consolidated.getWarnings());
            consolidated.setWarnings(new ArrayList<>(unique));
        }

        return consolidated;
    }

    public CompletenessResponse runCompletenessCheck() {
        ExtractedFinancialData consolidated = getConsolidatedFinancials();
        if (consolidated == null) {
            return CompletenessResponse.builder()
                    .completeness(DataCompletenessReport.builder()
                            .completenessScore(0).totalFields(14).presentFields(0)
                            .fields(List.of()).overallSuggestions(List.of("No documents uploaded yet."))
                            .build())
                    .build();
        }
        DataCompletenessReport report = documentValidator.checkCompleteness(consolidated);
        return CompletenessResponse.builder().completeness(report).build();
    }

    public TrendAnalysisResponse runTrendAnalysis() {
        ExtractedFinancialData consolidated = getConsolidatedFinancials();
        if (consolidated == null) return TrendAnalysisResponse.builder().trends(List.of()).summary("No data available.").build();

        List<TrendAnalysis> trends = tableParser.parseTrends(consolidated.getExtractedTables(), consolidated);
        String summary = trends.isEmpty() ? "Insufficient data for trend analysis."
                : String.format("Analyzed %d financial metrics across available periods.", trends.size());

        return TrendAnalysisResponse.builder().trends(trends).summary(summary).build();
    }

    public CrossVerificationResponse runCrossVerification() {
        ExtractedFinancialData consolidated = getConsolidatedFinancials();
        if (consolidated == null) {
            return CrossVerificationResponse.builder().alerts(List.of())
                    .summary("No data available.").totalAlerts(0).riskBreakdown(Map.of()).build();
        }

        List<CrossVerificationAlert> alerts = gstBankVerifier.verify(consolidated);
        String summary = gstBankVerifier.generateSummary(alerts);

        Map<String, Integer> riskBreakdown = alerts.stream()
                .collect(Collectors.groupingBy(a -> a.getRiskLevel().name(), Collectors.summingInt(a -> 1)));

        return CrossVerificationResponse.builder()
                .alerts(alerts).summary(summary)
                .totalAlerts(alerts.size()).riskBreakdown(riskBreakdown)
                .build();
    }

    public RatioAnalysisResponse runRatioAnalysis() {
        ExtractedFinancialData consolidated = getConsolidatedFinancials();
        if (consolidated == null) {
            return RatioAnalysisResponse.builder()
                    .ratios(FinancialData.FinancialRatiosReport.builder()
                            .overallHealth("UNKNOWN")
                            .recommendations(List.of("No data available for ratio analysis."))
                            .build())
                    .build();
        }

        FinancialRatiosReport ratios = debtCashflowAnalyzer.analyzeRatios(consolidated);
        return RatioAnalysisResponse.builder().ratios(ratios).build();
    }

    public FullAnalysisResponse runFullAnalysis() {
        ExtractedFinancialData consolidated = getConsolidatedFinancials();
        Map<String, Object> consolidatedMap = new LinkedHashMap<>();
        if (consolidated != null) {
            consolidatedMap.put("companyName", consolidated.getCompanyName());
            consolidatedMap.put("financialYear", consolidated.getFinancialYear());
            consolidatedMap.put("revenue", consolidated.getRevenue());
            consolidatedMap.put("profit", consolidated.getProfit());
            consolidatedMap.put("totalDebt", consolidated.getTotalDebt());
            consolidatedMap.put("totalAssets", consolidated.getTotalAssets());
            consolidatedMap.put("equity", consolidated.getEquity());
            consolidatedMap.put("cashFlow", consolidated.getCashFlow());
            consolidatedMap.put("gstRevenue", consolidated.getGstRevenue());
            consolidatedMap.put("bankInflow", consolidated.getBankInflow());
            consolidatedMap.put("bankOutflow", consolidated.getBankOutflow());
            consolidatedMap.put("confidence", consolidated.getConfidence());
            consolidatedMap.put("warnings", consolidated.getWarnings() != null ? consolidated.getWarnings() : List.of());
            consolidatedMap.put("sourceCount", documents.size());
        }

        return FullAnalysisResponse.builder()
                .consolidatedData(consolidatedMap)
                .completeness(runCompletenessCheck().getCompleteness())
                .trendAnalysis(runTrendAnalysis())
                .crossVerification(runCrossVerification())
                .ratioAnalysis(runRatioAnalysis())
                .build();
    }

    public DashboardResponse runDashboardAnalysis() {
        ExtractedFinancialData consolidated = getConsolidatedFinancials();
        CompletenessResponse completeness = runCompletenessCheck();
        TrendAnalysisResponse trends = runTrendAnalysis();
        CrossVerificationResponse cross = runCrossVerification();
        RatioAnalysisResponse ratio = runRatioAnalysis();

        if (consolidated == null) {
            return DashboardResponse.builder()
                    .companyName("N/A")
                    .financialYear("N/A")
                    .kpis(List.of())
                    .trendSeries(List.of())
                    .completenessByPriority(Map.of())
                    .missingCriticalFields(List.of())
                    .riskBreakdown(Map.of())
                    .insights(List.of("Upload documents to generate dashboard insights."))
                    .build();
        }

        List<DashboardKpi> kpis = buildKpis(consolidated, ratio.getRatios());

        List<DashboardTrendSeries> trendSeries = trends.getTrends().stream()
                .map(t -> DashboardTrendSeries.builder()
                        .metric(t.getMetric())
                        .trend(t.getTrend())
                        .growthRate(t.getGrowthRate())
                        .points(t.getDataPoints())
                        .build())
                .toList();

        Map<String, Integer> completenessByPriority = new LinkedHashMap<>();
        completenessByPriority.put("CRITICAL", 0);
        completenessByPriority.put("HIGH", 0);
        completenessByPriority.put("MEDIUM", 0);
        completenessByPriority.put("LOW", 0);

        List<String> missingCriticalFields = new ArrayList<>();
        for (FieldCompleteness f : completeness.getCompleteness().getFields()) {
            if (!f.isPresent()) {
                completenessByPriority.computeIfPresent(f.getPriority(), (k, v) -> v + 1);
                if ("CRITICAL".equals(f.getPriority())) {
                    missingCriticalFields.add(f.getField());
                }
            }
        }

        List<String> insights = buildInsights(consolidated, ratio.getRatios(), completeness.getCompleteness(), cross);

        return DashboardResponse.builder()
                .companyName(coalesce(consolidated.getCompanyName(), "Unknown Company"))
                .financialYear(coalesce(consolidated.getFinancialYear(), "Current Year"))
                .kpis(kpis)
                .trendSeries(trendSeries)
                .completenessByPriority(completenessByPriority)
                .missingCriticalFields(missingCriticalFields)
                .riskBreakdown(cross.getRiskBreakdown() != null ? cross.getRiskBreakdown() : Map.of())
                .insights(insights)
                .build();
    }

    public UnderwritingResponse runUnderwritingAssessment() {
        ExtractedFinancialData data = getConsolidatedFinancials();
        FinancialRatiosReport ratios = runRatioAnalysis().getRatios();
        DataCompletenessReport completeness = runCompletenessCheck().getCompleteness();
        RiskPolicyVersion policy = riskPolicyService.getActivePolicy();
        RiskPolicyRules rules = policy.getRules();

        if (data == null) {
            return UnderwritingResponse.builder()
                    .decision("REJECT")
                    .riskBand("UNKNOWN")
                    .recommendedExposureCr(0.0)
                    .expectedPricingSpreadBps(0.0)
                    .policyVersionId(policy.getVersionId())
                    .policyRuleId(policy.getRuleId())
                    .covenants(List.of("Upload valid documents for underwriting."))
                    .rationale(List.of("No financial data available."))
                    .build();
        }

                double confidence = data.getConfidence() == null ? 0.0 : data.getConfidence();
                if (confidence < rules.getMinConfidence()) {
                    return UnderwritingResponse.builder()
                        .decision("REFER_MANUAL_REVIEW")
                        .riskBand("UNVERIFIED")
                        .recommendedExposureCr(0.0)
                        .expectedPricingSpreadBps(0.0)
                        .policyVersionId(policy.getVersionId())
                        .policyRuleId(policy.getRuleId())
                        .covenants(List.of("Re-upload clearer core financial documents and re-run extraction"))
                        .rationale(List.of(
                            String.format("Extraction confidence is %.2f which is below policy threshold (%.2f).", confidence, rules.getMinConfidence()),
                            "Automated sanctioning blocked to prevent hallucination-driven decisioning."
                        ))
                        .build();
                }

                if (completeness.getCompletenessScore() < rules.getMinCompletenessScore()) {
                    return UnderwritingResponse.builder()
                        .decision("REFER_MANUAL_REVIEW")
                        .riskBand("INSUFFICIENT_DATA")
                        .recommendedExposureCr(0.0)
                        .expectedPricingSpreadBps(0.0)
                        .policyVersionId(policy.getVersionId())
                        .policyRuleId(policy.getRuleId())
                        .covenants(List.of("Provide missing critical documents before credit decisioning"))
                        .rationale(List.of(
                            String.format("Data completeness is %.1f%% which is below policy threshold (%.1f%%).", completeness.getCompletenessScore(), rules.getMinCompletenessScore()),
                            "Credit recommendation withheld to avoid false positives from incomplete evidence."
                        ))
                        .build();
                }

        double score = 0;
        List<String> rationale = new ArrayList<>();

        if (ratios != null && ratios.getRevenueGrowth() != null && ratios.getRevenueGrowth().getValue() != null) {
            double growth = ratios.getRevenueGrowth().getValue();
            if (growth > 0.15) { score += 2; rationale.add("Strong revenue growth trend."); }
            else if (growth > 0.05) { score += 1; rationale.add("Moderate revenue growth."); }
            else rationale.add("Weak revenue growth.");
        }

        if (ratios != null && ratios.getDebtToEquity() != null && ratios.getDebtToEquity().getValue() != null) {
            double de = ratios.getDebtToEquity().getValue();
            if (de <= 1.0) { score += 2; rationale.add("Low leverage profile."); }
            else if (de <= 2.0) { score += 1; rationale.add("Moderate leverage."); }
            else rationale.add("High leverage risk.");
        }

        if (ratios != null && ratios.getProfitMargin() != null && ratios.getProfitMargin().getValue() != null) {
            double pm = ratios.getProfitMargin().getValue();
            if (pm >= 0.10) { score += 2; rationale.add("Healthy profit margin."); }
            else if (pm >= 0.05) { score += 1; rationale.add("Thin but acceptable margin."); }
            else rationale.add("Low profitability.");
        }

        if (completeness.getCompletenessScore() >= 80) score += 2;
        else if (completeness.getCompletenessScore() >= 60) score += 1;
        else rationale.add("Limited document completeness for strong credit comfort.");

        if (data.getGstRevenue() != null && data.getBankInflow() != null && data.getGstRevenue() > 0) {
            double gstBankDeviation = Math.abs(data.getBankInflow() - data.getGstRevenue()) / data.getGstRevenue();
            if (gstBankDeviation > rules.getGstDeviationHigh()) {
                score -= 2;
                rationale.add(String.format("GST-bank mismatch of %.1f%% indicates elevated circular-trading/revenue-quality risk.", gstBankDeviation * 100));
            } else if (gstBankDeviation > rules.getGstDeviationMedium()) {
                score -= 1;
                rationale.add(String.format("Moderate GST-bank mismatch of %.1f%% requires tighter monitoring.", gstBankDeviation * 100));
            }
        }

        String decision;
        String riskBand;
        double spread;

        if (score >= rules.getApproveScore()) {
            decision = "APPROVE";
            riskBand = "LOW";
            spread = rules.getLowSpreadBps();
        } else if (score >= rules.getApproveWithConditionsScore()) {
            decision = "APPROVE_WITH_CONDITIONS";
            riskBand = "MEDIUM";
            spread = rules.getMediumSpreadBps();
        } else if (score >= rules.getReferManualScore()) {
            decision = "REFER_MANUAL_REVIEW";
            riskBand = "ELEVATED";
            spread = rules.getElevatedSpreadBps();
        } else {
            decision = "REJECT";
            riskBand = "HIGH";
            spread = 0;
        }

        double baseExposure = data.getRevenue() != null ? data.getRevenue() * rules.getBaseExposureRatio() : 0.0;
        double riskMultiplier = switch (riskBand) {
            case "LOW" -> 1.0;
            case "MEDIUM" -> rules.getMediumRiskExposureMultiplier();
            case "ELEVATED" -> rules.getElevatedRiskExposureMultiplier();
            default -> 0.0;
        };
        double recommendedExposure = Math.round(baseExposure * riskMultiplier * 100.0) / 100.0;

        List<String> covenants = new ArrayList<>(List.of(
                "Quarterly financial reporting submission",
                "No additional secured borrowing without lender consent",
                "Maintain minimum DSCR covenant as per sanction terms"
        ));
        if (completeness.getCompletenessScore() < 70) {
            covenants.add("Submit GST and bank statements within 30 days post-disbursement");
        }

        return UnderwritingResponse.builder()
                .decision(decision)
                .riskBand(riskBand)
                .recommendedExposureCr(recommendedExposure)
                .expectedPricingSpreadBps(spread)
            .policyVersionId(policy.getVersionId())
            .policyRuleId(policy.getRuleId())
                .covenants(covenants)
                .rationale(rationale)
                .build();
    }

    public EnterpriseAssessmentResponse runEnterpriseAssessment() {
        ExtractedFinancialData data = getConsolidatedFinancials();
        FinancialRatiosReport ratios = runRatioAnalysis().getRatios();
        DataCompletenessReport completeness = runCompletenessCheck().getCompleteness();

        if (data == null) {
            return EnterpriseAssessmentResponse.builder()
                    .internalRiskScore(100)
                    .rating("UNRATED")
                    .probabilityOfDefault1Y(0.35)
                    .expectedLossPct(18.0)
                    .stressScenarios(List.of())
                    .controls(List.of("Upload at least one annual report and one financial statement."))
                    .recommendations(List.of("No data available for enterprise assessment."))
                    .build();
        }

        double score = 45.0;
        List<String> recommendations = new ArrayList<>();
        List<String> controls = new ArrayList<>(List.of(
                "Dual approval for exposure changes above policy threshold",
                "Monthly covenant and variance monitoring",
                "Automated early warning alerts for deterioration triggers"
        ));

        if (completeness.getCompletenessScore() >= 80) score -= 8;
        else if (completeness.getCompletenessScore() < 50) {
            score += 10;
            recommendations.add("Increase evidentiary coverage before sanction.");
        }

        if (ratios != null && ratios.getDebtToEquity() != null && ratios.getDebtToEquity().getValue() != null) {
            double de = ratios.getDebtToEquity().getValue();
            if (de > 2.5) {
                score += 14;
                recommendations.add("Leverage is elevated; impose additional debt covenant protections.");
            } else if (de < 1.0) {
                score -= 6;
            }
        }

        if (ratios != null && ratios.getProfitMargin() != null && ratios.getProfitMargin().getValue() != null) {
            double margin = ratios.getProfitMargin().getValue();
            if (margin < 0.04) {
                score += 12;
                recommendations.add("Profitability thin; require quarterly margin tracking and escalation.");
            } else if (margin > 0.10) {
                score -= 5;
            }
        }

        if (data.getGstRevenue() != null && data.getBankInflow() != null && data.getGstRevenue() > 0) {
            double deviation = Math.abs(data.getBankInflow() - data.getGstRevenue()) / data.getGstRevenue();
            if (deviation > 0.30) {
                score += 16;
                controls.add("Trigger forensic reconciliation of GST and banking transactions.");
                recommendations.add("High revenue consistency deviation, require fraud desk review.");
            }
        }

        if (data.getConfidence() != null && data.getConfidence() < 0.60) {
            score += 10;
            recommendations.add("Extraction confidence low; request cleaner statements and rerun extraction.");
        }

        int internalRiskScore = (int) Math.max(1, Math.min(100, Math.round(score)));
        String rating = mapRating(internalRiskScore);
        double pd = Math.round((0.01 + (internalRiskScore / 100.0) * 0.22) * 10000.0) / 10000.0;
        double lgd = 0.45;
        double eadFactor = 0.85;
        double expectedLossPct = Math.round(pd * lgd * eadFactor * 10000.0) / 100.0;

        List<StressScenarioResult> stressScenarios = buildStressScenarios(data);
        if (recommendations.isEmpty()) {
            recommendations.add("Maintain current monitoring cadence; no immediate adverse signal detected.");
        }

        return EnterpriseAssessmentResponse.builder()
                .internalRiskScore(internalRiskScore)
                .rating(rating)
                .probabilityOfDefault1Y(pd)
                .expectedLossPct(expectedLossPct)
                .stressScenarios(stressScenarios)
                .controls(controls)
                .recommendations(recommendations)
                .build();
    }

    public RecommendationEngineResponse runRecommendationEngine() {
        ExtractedFinancialData data = getConsolidatedFinancials();
        DataCompletenessReport completeness = runCompletenessCheck().getCompleteness();

        if (data == null) {
            return RecommendationEngineResponse.builder()
                    .status("WITHHELD")
                    .decision("WITHHOLD_RECOMMENDATION")
                    .riskBand("UNKNOWN")
                    .recommendedLimitCr(null)
                    .pricingSpreadBps(null)
                    .indicativeRatePct(null)
                    .internalRiskScore(null)
                    .internalRating(null)
                    .probabilityOfDefault1Y(null)
                    .expectedLossPct(null)
                    .confidence(null)
                    .completenessScore(0.0)
                    .grounded(false)
                    .guardrailReasons(List.of("No extracted financial evidence available."))
                    .recommendations(List.of("Upload and process at least one annual report and one financial statement."))
                    .covenants(List.of())
                    .rationale(List.of("Recommendation withheld to avoid unsupported output."))
                    .evidence(List.of())
                    .build();
        }

        UnderwritingResponse underwriting = runUnderwritingAssessment();
        EnterpriseAssessmentResponse enterprise = runEnterpriseAssessment();
        DashboardResponse dashboard = runDashboardAnalysis();

        double confidence = data.getConfidence() == null ? 0.0 : data.getConfidence();
        double completenessScore = completeness == null ? 0.0 : completeness.getCompletenessScore();

        List<String> guardrailReasons = new ArrayList<>();
        boolean grounded = true;

        if (confidence < 0.60) {
            grounded = false;
            guardrailReasons.add(String.format("Extraction confidence %.2f below threshold 0.60.", confidence));
        }
        if (completenessScore < 55.0) {
            grounded = false;
            guardrailReasons.add(String.format("Data completeness %.1f%% below threshold 55%%.", completenessScore));
        }
        if (dashboard != null && dashboard.getMissingCriticalFields() != null && !dashboard.getMissingCriticalFields().isEmpty()) {
            grounded = false;
            guardrailReasons.add("Critical fields missing: " + String.join(", ", dashboard.getMissingCriticalFields()));
        }

        List<RecommendationEvidence> evidence = buildRecommendationEvidence(data, underwriting, enterprise, completenessScore, confidence);

        if (!grounded) {
            List<String> actions = new ArrayList<>();
            actions.add("Recommendation withheld to prevent hallucination from low-evidence context.");
            actions.add("Provide missing critical documents and rerun extraction.");
            if (dashboard != null && dashboard.getMissingCriticalFields() != null && !dashboard.getMissingCriticalFields().isEmpty()) {
                actions.add("Focus on critical fields: " + String.join(", ", dashboard.getMissingCriticalFields()));
            }

            return RecommendationEngineResponse.builder()
                    .status("WITHHELD")
                    .decision("WITHHOLD_RECOMMENDATION")
                    .riskBand(underwriting != null ? underwriting.getRiskBand() : "UNKNOWN")
                    .recommendedLimitCr(null)
                    .pricingSpreadBps(null)
                    .indicativeRatePct(null)
                    .internalRiskScore(enterprise != null ? enterprise.getInternalRiskScore() : null)
                    .internalRating(enterprise != null ? enterprise.getRating() : null)
                    .probabilityOfDefault1Y(enterprise != null ? enterprise.getProbabilityOfDefault1Y() : null)
                    .expectedLossPct(enterprise != null ? enterprise.getExpectedLossPct() : null)
                    .confidence(confidence)
                    .completenessScore(completenessScore)
                    .grounded(false)
                    .guardrailReasons(guardrailReasons)
                    .recommendations(actions)
                    .covenants(underwriting != null && underwriting.getCovenants() != null ? underwriting.getCovenants() : List.of())
                    .rationale(underwriting != null && underwriting.getRationale() != null ? underwriting.getRationale() : List.of())
                    .evidence(evidence)
                    .build();
        }

        Double spreadBps = underwriting != null ? underwriting.getExpectedPricingSpreadBps() : null;
        Double indicativeRate = spreadBps == null ? null : Math.round((8.50 + (spreadBps / 100.0)) * 100.0) / 100.0;

        return RecommendationEngineResponse.builder()
                .status("READY")
                .decision(underwriting != null ? underwriting.getDecision() : "REFER_MANUAL_REVIEW")
                .riskBand(underwriting != null ? underwriting.getRiskBand() : "UNKNOWN")
                .recommendedLimitCr(underwriting != null ? underwriting.getRecommendedExposureCr() : null)
                .pricingSpreadBps(spreadBps)
                .indicativeRatePct(indicativeRate)
                .internalRiskScore(enterprise != null ? enterprise.getInternalRiskScore() : null)
                .internalRating(enterprise != null ? enterprise.getRating() : null)
                .probabilityOfDefault1Y(enterprise != null ? enterprise.getProbabilityOfDefault1Y() : null)
                .expectedLossPct(enterprise != null ? enterprise.getExpectedLossPct() : null)
                .confidence(confidence)
                .completenessScore(completenessScore)
                .grounded(true)
                .guardrailReasons(List.of())
                .recommendations(enterprise != null && enterprise.getRecommendations() != null ? enterprise.getRecommendations() : List.of())
                .covenants(underwriting != null && underwriting.getCovenants() != null ? underwriting.getCovenants() : List.of())
                .rationale(underwriting != null && underwriting.getRationale() != null ? underwriting.getRationale() : List.of())
                .evidence(evidence)
                .build();
    }

    private List<RecommendationEvidence> buildRecommendationEvidence(
            ExtractedFinancialData data,
            UnderwritingResponse underwriting,
            EnterpriseAssessmentResponse enterprise,
            double completeness,
            double confidence) {
        List<RecommendationEvidence> out = new ArrayList<>();
        out.add(evidenceItem("company", "Company", data.getCompanyName()));
        out.add(evidenceItem("financial_year", "Financial Year", data.getFinancialYear()));
        out.add(evidenceItem("revenue_cr", "Revenue (Cr)", toNum(data.getRevenue())));
        out.add(evidenceItem("profit_cr", "Profit (Cr)", toNum(data.getProfit())));
        out.add(evidenceItem("debt_cr", "Total Debt (Cr)", toNum(data.getTotalDebt())));
        out.add(evidenceItem("confidence", "Extraction Confidence", String.format("%.2f", confidence)));
        out.add(evidenceItem("completeness", "Completeness Score", String.format("%.1f%%", completeness)));
        out.add(evidenceItem("underwriting_decision", "Underwriting Decision", underwriting == null ? null : underwriting.getDecision()));
        out.add(evidenceItem("risk_score", "Internal Risk Score", enterprise == null || enterprise.getInternalRiskScore() == null ? null : String.valueOf(enterprise.getInternalRiskScore())));
        return out;
    }

    private RecommendationEvidence evidenceItem(String key, String label, String value) {
        return RecommendationEvidence.builder()
                .key(key)
                .label(label)
                .value(value)
                .available(value != null && !value.isBlank())
                .build();
    }

    private String toNum(Double value) {
        return value == null ? null : String.format("%.2f", value);
    }

    public ReviewWorkflowResponse getReviewWorkflow() {
        return reviewWorkflow;
    }

    public ReviewWorkflowResponse submitForReview(String submittedBy, String notes) {
        reviewWorkflow = ReviewWorkflowResponse.builder()
                .status("PENDING_APPROVAL")
                .submittedBy(submittedBy)
                .notes(notes)
                .submittedAt(LocalDateTime.now().toString())
                .approvedBy(null)
                .approvedAt(null)
                .approvedNotes(null)
                .build();
        return reviewWorkflow;
    }

    public ReviewWorkflowResponse approveReview(String approvedBy, String approvedNotes) {
        if (reviewWorkflow == null || !"PENDING_APPROVAL".equals(reviewWorkflow.getStatus())) {
            reviewWorkflow = ReviewWorkflowResponse.builder()
                    .status("DRAFT")
                    .build();
            return reviewWorkflow;
        }

        reviewWorkflow.setStatus("APPROVED");
        reviewWorkflow.setApprovedBy(approvedBy);
        reviewWorkflow.setApprovedNotes(approvedNotes);
        reviewWorkflow.setApprovedAt(LocalDateTime.now().toString());
        return reviewWorkflow;
    }

    private List<StressScenarioResult> buildStressScenarios(ExtractedFinancialData data) {
        double baseProfit = data.getProfit() == null ? 0.0 : data.getProfit();
        double baseDebt = data.getTotalDebt() == null ? 0.0 : data.getTotalDebt();
        double baseEquity = data.getEquity() == null ? 0.0 : Math.max(1.0, data.getEquity());

        StressScenarioResult mild = StressScenarioResult.builder()
                .scenario("Mild slowdown")
                .projectedProfitCr(round2(baseProfit * 0.9))
                .projectedDebtToEquity(round2((baseDebt * 1.05) / baseEquity))
                .riskImpact("LOW")
                .commentary("10% profit compression and minor leverage increase remains manageable.")
                .build();

        StressScenarioResult moderate = StressScenarioResult.builder()
                .scenario("Demand shock")
                .projectedProfitCr(round2(baseProfit * 0.72))
                .projectedDebtToEquity(round2((baseDebt * 1.15) / baseEquity))
                .riskImpact("MEDIUM")
                .commentary("Profit erosion and leverage pressure require tighter covenant monitoring.")
                .build();

        StressScenarioResult severe = StressScenarioResult.builder()
                .scenario("Severe stress")
                .projectedProfitCr(round2(baseProfit * 0.5))
                .projectedDebtToEquity(round2((baseDebt * 1.30) / baseEquity))
                .riskImpact("HIGH")
                .commentary("Severe downturn can materially impair repayment capacity; contingency needed.")
                .build();

        return List.of(mild, moderate, severe);
    }

    private String mapRating(int score) {
        if (score <= 20) return "AAA";
        if (score <= 30) return "AA";
        if (score <= 40) return "A";
        if (score <= 52) return "BBB";
        if (score <= 65) return "BB";
        if (score <= 78) return "B";
        return "CCC";
    }

    private double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private List<DashboardKpi> buildKpis(ExtractedFinancialData data, FinancialRatiosReport ratios) {
        List<DashboardKpi> kpis = new ArrayList<>();
        kpis.add(kpi("revenue", "Revenue", data.getRevenue(), "Cr", "NEUTRAL"));
        kpis.add(kpi("profit", "Profit", data.getProfit(), "Cr", "NEUTRAL"));
        kpis.add(kpi("debt", "Total Debt", data.getTotalDebt(), "Cr", "WARNING"));
        kpis.add(kpi("equity", "Equity", data.getEquity(), "Cr", "HEALTHY"));

        if (ratios != null) {
            if (ratios.getDebtToEquity() != null) {
                kpis.add(kpi("de_ratio", "Debt/Equity", ratios.getDebtToEquity().getValue(), "x",
                        safeName(ratios.getDebtToEquity().getHealth())));
            }
            if (ratios.getProfitMargin() != null && ratios.getProfitMargin().getValue() != null) {
                kpis.add(kpi("profit_margin", "Profit Margin", ratios.getProfitMargin().getValue() * 100, "%",
                        safeName(ratios.getProfitMargin().getHealth())));
            }
            if (ratios.getRevenueGrowth() != null && ratios.getRevenueGrowth().getValue() != null) {
                kpis.add(kpi("revenue_growth", "Revenue Growth", ratios.getRevenueGrowth().getValue() * 100, "%",
                        safeName(ratios.getRevenueGrowth().getHealth())));
            }
        }
        return kpis;
    }

    private List<String> buildInsights(
            ExtractedFinancialData data,
            FinancialRatiosReport ratios,
            DataCompletenessReport completeness,
            CrossVerificationResponse cross) {

        List<String> insights = new ArrayList<>();
        insights.add(String.format("Data completeness is %.1f%% (%d/%d fields present).",
                completeness.getCompletenessScore(), completeness.getPresentFields(), completeness.getTotalFields()));

        if (ratios != null) {
            insights.add("Overall financial health: " + coalesce(ratios.getOverallHealth(), "UNKNOWN"));
            if (ratios.getRevenueGrowth() != null && ratios.getRevenueGrowth().getValue() != null) {
                insights.add(String.format("Revenue growth recorded at %.1f%%.", ratios.getRevenueGrowth().getValue() * 100));
            }
            if (ratios.getDebtToEquity() != null && ratios.getDebtToEquity().getValue() != null) {
                insights.add(String.format("Leverage (D/E) stands at %.2fx.", ratios.getDebtToEquity().getValue()));
            }
        }

        if (cross != null) {
            insights.add("Cross-verification alerts detected: " + cross.getTotalAlerts());
        }

        if (data.getWarnings() != null && data.getWarnings().stream().anyMatch(w -> w != null && w.toLowerCase().contains("hybrid"))) {
            insights.add("Hybrid extraction active: deterministic parser fused with LLM reasoning.");
        }

        if (data.getGstRevenue() != null && data.getBankInflow() != null && data.getGstRevenue() > 0) {
            double deviation = Math.abs(data.getBankInflow() - data.getGstRevenue()) / data.getGstRevenue();
            if (deviation > 0.25) {
                insights.add(String.format("GST vs Bank inflow deviation is high at %.1f%% (possible inflation/circularity signal).", deviation * 100));
            }
        }

        if (data.getGstRevenue() == null || data.getBankInflow() == null || data.getBankOutflow() == null) {
            insights.add("Upload GST filings and bank statements to unlock deeper anomaly checks.");
        }

        return insights;
    }

    private DashboardKpi kpi(String key, String label, Double value, String unit, String status) {
        return DashboardKpi.builder()
                .key(key)
                .label(label)
                .value(value)
                .unit(unit)
                .status(status)
                .build();
    }

    private String safeName(Enum<?> value) {
        return value != null ? value.name() : "UNKNOWN";
    }

    private String coalesce(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    public void reset() {
        // Delete all uploaded files
        File uploadDir = new File(config.getUploadDir());
        File[] files = uploadDir.listFiles();
        if (files != null) {
            for (File f : files) f.delete();
        }
        documents.clear();
        reviewWorkflow = ReviewWorkflowResponse.builder().status("DRAFT").build();
    }
}
