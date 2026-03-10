package com.credereai.module1.engines;

import com.credereai.module1.config.Module1Config;
import com.credereai.llm.LlmGateway;
import com.credereai.module1.models.FinancialData;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class UnderstandingEngine {

    private final Module1Config config;
    private final LlmGateway llmGateway;
    private final LlmOutputContractValidator contractValidator;
    private final ObjectMapper objectMapper;

    private static final String EXTRACTION_PROMPT = """
            You are an expert financial document analyst. Extract structured financial data from the following document text.
            Return ONLY a valid JSON object with these fields (use null for unavailable data):
            {
              "companyName": "string",
              "financialYear": "string (e.g. 2023-24)",
              "documentType": "string",
              "revenue": number_in_crores_or_null,
              "profit": number_net_profit_in_crores_or_null,
              "totalDebt": number_in_crores_or_null,
              "totalAssets": number_in_crores_or_null,
              "totalLiabilities": number_in_crores_or_null,
              "cashFlow": number_operating_cash_flow_in_crores_or_null,
              "equity": number_shareholders_equity_in_crores_or_null,
              "interestExpense": number_in_crores_or_null,
              "ebit": number_in_crores_or_null,
              "currentAssets": number_in_crores_or_null,
              "currentLiabilities": number_in_crores_or_null,
              "gstRevenue": number_or_null,
              "bankInflow": number_or_null,
              "bankOutflow": number_or_null,
              "prev_revenue": number_previous_year_revenue_or_null,
              "prev_profit": number_previous_year_profit_or_null,
              "prev_total_debt": number_previous_year_debt_or_null,
              "cin": "Corporate Identity Number or null",
              "registeredAddress": "string or null",
              "auditor": "auditor firm name or null",
              "directors": ["list of director names"] or null,
              "promoters": ["list of promoter names"] or null,
              "promoterHolding": number_percentage_or_null,
              "confidence": number_0_to_1,
              "warnings": ["list of extraction warnings"]
            }
            IMPORTANT: Return ONLY the JSON object, no markdown fences, no explanation.
            Document text:
            """;

    private static final String TABLE_EXTRACTION_PROMPT = """
            Extract financial tables from this document text. Return a JSON array of table objects:
            [{"title": "table name", "headers": ["col1","col2"], "rows": [["val1","val2"]], "unit": "₹ in Crores"}]
            Focus on: Balance Sheet, P&L Statement, Cash Flow, Ratio tables.
            Return ONLY the JSON array, no markdown fences.
            Document text:
            """;

    public FinancialData.ExtractedFinancialData extractFinancialData(String text, String documentType) {
        return extractFinancialData(text, documentType, true);
    }

    public FinancialData.ExtractedFinancialData extractFinancialData(String text, String documentType, boolean enableLlm) {
        // Truncate text if needed
        String truncatedText = text.length() > config.getMaxTextChars()
                ? text.substring(0, config.getMaxTextChars()) : text;

        FinancialData.ExtractedFinancialData parserData = extractWithDeterministicParser(truncatedText, documentType);
        FinancialData.ExtractedFinancialData llmData = null;
        String llmError = null;

        if (enableLlm) {
            try {
                String response = callGemini(EXTRACTION_PROMPT + truncatedText);
                String cleanJson = cleanJsonResponse(response);
                JsonNode validated = contractValidator.validateExtraction(cleanJson);
                llmData = objectMapper.treeToValue(validated, FinancialData.ExtractedFinancialData.class);
            } catch (Exception e) {
                llmError = e.getMessage();
                log.warn("LLM extraction pass failed, continuing with parser fallback: {}", e.getMessage());
            }
        } else {
            llmError = "LLM skipped for fast-mode processing";
        }

        FinancialData.ExtractedFinancialData fused = fuseExtractions(parserData, llmData, documentType, llmError);

        // Try to extract tables (LLM first, deterministic fallback inside method)
        try {
            List<FinancialData.FinancialTable> tables = enableLlm
                    ? extractTables(truncatedText)
                    : extractTablesFromText(truncatedText);
            fused.setExtractedTables(tables);
        } catch (Exception e) {
            log.warn("Table extraction failed: {}", e.getMessage());
            fused.setExtractedTables(List.of());
        }

        if (fused.getDocumentType() == null) fused.setDocumentType(documentType);
        enrichWithHeuristics(fused, truncatedText);
        applyReliabilityGuards(fused, truncatedText);
        return fused;
    }

    private FinancialData.ExtractedFinancialData extractWithDeterministicParser(String text, String documentType) {
        double docScaleToCrores = detectDocumentScale(text);
        FinancialData.ExtractedFinancialData parsed = FinancialData.ExtractedFinancialData.builder()
                .documentType(documentType)
                .companyName(matchGroup(text, "(?im)^(.*?(?:limited|ltd\\.?|private\\s+limited|pvt\\.?\\s*ltd\\.?))$"))
                .financialYear(matchGroup(text, "(?i)(20\\d{2}\\s*[-/]\\s*\\d{2})"))
                .cin(matchGroup(text, "(?i)\\b([LU][0-9]{5}[A-Z]{2}[0-9]{4}[A-Z]{3}[0-9]{6})\\b"))
                .revenue(extractMetricValue(text, "revenue|total income|income from operations", docScaleToCrores))
                .profit(extractMetricValue(text, "net profit|profit after tax|pat", docScaleToCrores))
                .totalDebt(extractMetricValue(text, "total debt|borrowings|long term borrowings", docScaleToCrores))
                .totalAssets(extractMetricValue(text, "total assets", docScaleToCrores))
                .totalLiabilities(extractMetricValue(text, "total liabilities", docScaleToCrores))
                .cashFlow(extractMetricValue(text, "net cash from operating activities|operating cash flow|cash flow from operations", docScaleToCrores))
                .equity(extractMetricValue(text, "equity|shareholders'? funds|net worth", docScaleToCrores))
                .interestExpense(extractMetricValue(text, "interest expense|finance cost", docScaleToCrores))
                .ebit(extractMetricValue(text, "ebit|earnings before interest and tax", docScaleToCrores))
                .currentAssets(extractMetricValue(text, "current assets", docScaleToCrores))
                .currentLiabilities(extractMetricValue(text, "current liabilities", docScaleToCrores))
                .prevRevenue(extractSecondMetricValue(text, "revenue|total income|income from operations", docScaleToCrores))
                .prevProfit(extractSecondMetricValue(text, "net profit|profit after tax|pat", docScaleToCrores))
                .prevTotalDebt(extractSecondMetricValue(text, "total debt|borrowings|long term borrowings", docScaleToCrores))
                .directors(extractEntityList(text, "directors|board of directors"))
                .promoters(extractEntityList(text, "promoters?|promoter group|shareholding"))
                .confidence(0.45)
                .warnings(new ArrayList<>(List.of(
                        "Deterministic parser extraction enabled.",
                        "Hybrid mode will fuse parser + LLM when available."
                )))
                .build();

        return parsed;
    }

    private FinancialData.ExtractedFinancialData fuseExtractions(
            FinancialData.ExtractedFinancialData parser,
            FinancialData.ExtractedFinancialData llm,
            String documentType,
            String llmError) {

        if (llm == null) {
            parser.setDocumentType(documentType);
            parser.setConfidence(Math.max(0.35, Optional.ofNullable(parser.getConfidence()).orElse(0.35)));
            parser.setWarnings(mergeWarnings(List.of(
                parser.getWarnings(),
                nullableList("LLM unavailable, parser-only fallback used.", llmError != null ? "LLM error: " + llmError : null)
            )));
            return parser;
        }

        // Keep LLM interpretation, but fill missing values deterministically.
        applyFallbacks(llm, parser);
        llm.setDocumentType(llm.getDocumentType() == null ? documentType : llm.getDocumentType());

        double parserCoverage = computeCoverage(parser);
        double llmCoverage = computeCoverage(llm);
        double agreement = computeNumericAgreement(parser, llm);
        double fusedConfidence = clamp01(0.25 + (0.35 * llmCoverage) + (0.25 * parserCoverage) + (0.15 * agreement));
        llm.setConfidence(Math.round(fusedConfidence * 100.0) / 100.0);
        llm.setWarnings(mergeWarnings(List.of(
            llm.getWarnings(),
            parser.getWarnings(),
            nullableList(
                "Hybrid extraction completed with parser + LLM fusion.",
                String.format("Coverage(parser=%.0f%%, llm=%.0f%%), agreement=%.0f%%.",
                    parserCoverage * 100, llmCoverage * 100, agreement * 100),
                llmError != null ? "Partial LLM issue: " + llmError : null
            )
        )));
        return llm;
    }

    private void enrichWithHeuristics(FinancialData.ExtractedFinancialData data, String text) {
        if (data.getCompanyName() == null) {
            data.setCompanyName(matchGroup(text, "(?im)^(.*?(?:limited|ltd\\.?|private\\s+limited|pvt\\.?\\s*ltd\\.?))$"));
        }
        if (data.getFinancialYear() == null) {
            data.setFinancialYear(matchGroup(text, "(?i)(20\\d{2}\\s*[-/]\\s*\\d{2})"));
        }

        if (data.getRevenue() == null) data.setRevenue(extractMetricValue(text, "revenue|total income|income from operations"));
        if (data.getProfit() == null) data.setProfit(extractMetricValue(text, "net profit|profit after tax|pat"));
        if (data.getTotalDebt() == null) data.setTotalDebt(extractMetricValue(text, "total debt|borrowings|long term borrowings"));
        if (data.getTotalAssets() == null) data.setTotalAssets(extractMetricValue(text, "total assets"));
        if (data.getTotalLiabilities() == null) data.setTotalLiabilities(extractMetricValue(text, "total liabilities"));
        if (data.getEquity() == null) data.setEquity(extractMetricValue(text, "equity|shareholders'? funds|net worth"));
        if (data.getCashFlow() == null) data.setCashFlow(extractMetricValue(text, "net cash from operating activities|operating cash flow|cash flow from operations"));
        if (data.getEbit() == null) data.setEbit(extractMetricValue(text, "ebit|earnings before interest and tax"));
        if (data.getInterestExpense() == null) data.setInterestExpense(extractMetricValue(text, "interest expense|finance cost"));
        if (data.getCurrentAssets() == null) data.setCurrentAssets(extractMetricValue(text, "current assets"));
        if (data.getCurrentLiabilities() == null) data.setCurrentLiabilities(extractMetricValue(text, "current liabilities"));

        if (data.getPrevRevenue() == null) {
            data.setPrevRevenue(extractSecondMetricValue(text, "revenue|total income|income from operations"));
        }
        if (data.getPrevProfit() == null) {
            data.setPrevProfit(extractSecondMetricValue(text, "net profit|profit after tax|pat"));
        }
        if (data.getPrevTotalDebt() == null) {
            data.setPrevTotalDebt(extractSecondMetricValue(text, "total debt|borrowings|long term borrowings"));
        }
    }

    private String matchGroup(String text, String regex) {
        try {
            Matcher m = Pattern.compile(regex).matcher(text);
            if (m.find()) return m.group(1).trim();
        } catch (Exception ignored) {}
        return null;
    }

    private Double extractMetricValue(String text, String metricRegex, double docScaleToCrores) {
        try {
            Pattern p = Pattern.compile("(?is)(" + metricRegex + ")[^\\n\\r:]{0,60}[:\\-\\s]*([0-9][0-9,]{0,20}(?:\\.[0-9]{1,2})?)\\s*(crores?|cr|lakhs?|lacs?|million|mn|billion|bn)?");
            Matcher m = p.matcher(text);
            if (m.find()) {
                return scaleToCrores(parseLooseNumber(m.group(2)), m.group(3), docScaleToCrores);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Double extractMetricValue(String text, String metricRegex) {
        return extractMetricValue(text, metricRegex, detectDocumentScale(text));
    }

    private Double extractSecondMetricValue(String text, String metricRegex, double docScaleToCrores) {
        try {
            Pattern p = Pattern.compile("(?is)(" + metricRegex + ")[^\\n\\r:]{0,60}[:\\-\\s]*([0-9][0-9,]{0,20}(?:\\.[0-9]{1,2})?)\\s*(crores?|cr|lakhs?|lacs?|million|mn|billion|bn)?");
            Matcher m = p.matcher(text);
            if (m.find() && m.find()) {
                return scaleToCrores(parseLooseNumber(m.group(2)), m.group(3), docScaleToCrores);
            }
        } catch (Exception ignored) {}
        return null;
    }

    private Double extractSecondMetricValue(String text, String metricRegex) {
        return extractSecondMetricValue(text, metricRegex, detectDocumentScale(text));
    }

    private double detectDocumentScale(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (lower.contains("in lakhs") || lower.contains("in lacs")) return 0.01;
        if (lower.contains("in millions") || lower.contains("in million")) return 0.1;
        if (lower.contains("in billions") || lower.contains("in billion")) return 100.0;
        return 1.0;
    }

    private Double scaleToCrores(Double value, String localUnit, double docScaleToCrores) {
        if (value == null) return null;
        if (localUnit == null || localUnit.isBlank()) return value * docScaleToCrores;

        String u = localUnit.toLowerCase(Locale.ROOT);
        double unitScale;
        if (u.startsWith("cr") || u.startsWith("crore")) unitScale = 1.0;
        else if (u.startsWith("lakh") || u.startsWith("lac")) unitScale = 0.01;
        else if (u.startsWith("mn") || u.startsWith("million")) unitScale = 0.1;
        else if (u.startsWith("bn") || u.startsWith("billion")) unitScale = 100.0;
        else unitScale = docScaleToCrores;
        return value * unitScale;
    }

    private List<String> extractEntityList(String text, String headingRegex) {
        try {
            Pattern p = Pattern.compile("(?is)(?:" + headingRegex + ")[^\\n]{0,40}\\n([^\\n]{5,220})");
            Matcher m = p.matcher(text);
            if (!m.find()) return null;
            String line = m.group(1).replaceAll("[;|]", ",");
            List<String> items = Arrays.stream(line.split(","))
                    .map(String::trim)
                    .filter(s -> s.length() > 3)
                    .filter(s -> !s.matches("(?i).*(limited|ltd|private|pvt).*"))
                    .limit(12)
                    .toList();
            return items.isEmpty() ? null : items;
        } catch (Exception ignored) {
            return null;
        }
    }

    private void applyFallbacks(FinancialData.ExtractedFinancialData target, FinancialData.ExtractedFinancialData fallback) {
        if (target.getCompanyName() == null) target.setCompanyName(fallback.getCompanyName());
        if (target.getFinancialYear() == null) target.setFinancialYear(fallback.getFinancialYear());
        if (target.getRevenue() == null) target.setRevenue(fallback.getRevenue());
        if (target.getProfit() == null) target.setProfit(fallback.getProfit());
        if (target.getTotalDebt() == null) target.setTotalDebt(fallback.getTotalDebt());
        if (target.getTotalAssets() == null) target.setTotalAssets(fallback.getTotalAssets());
        if (target.getTotalLiabilities() == null) target.setTotalLiabilities(fallback.getTotalLiabilities());
        if (target.getCashFlow() == null) target.setCashFlow(fallback.getCashFlow());
        if (target.getEquity() == null) target.setEquity(fallback.getEquity());
        if (target.getInterestExpense() == null) target.setInterestExpense(fallback.getInterestExpense());
        if (target.getEbit() == null) target.setEbit(fallback.getEbit());
        if (target.getCurrentAssets() == null) target.setCurrentAssets(fallback.getCurrentAssets());
        if (target.getCurrentLiabilities() == null) target.setCurrentLiabilities(fallback.getCurrentLiabilities());
        if (target.getPrevRevenue() == null) target.setPrevRevenue(fallback.getPrevRevenue());
        if (target.getPrevProfit() == null) target.setPrevProfit(fallback.getPrevProfit());
        if (target.getPrevTotalDebt() == null) target.setPrevTotalDebt(fallback.getPrevTotalDebt());
        if (target.getCin() == null) target.setCin(fallback.getCin());
        if (target.getDirectors() == null || target.getDirectors().isEmpty()) target.setDirectors(fallback.getDirectors());
        if (target.getPromoters() == null || target.getPromoters().isEmpty()) target.setPromoters(fallback.getPromoters());
    }

    private double computeCoverage(FinancialData.ExtractedFinancialData d) {
        int present = 0;
        int total = 0;
        List<Object> fields = Arrays.asList(
                d.getCompanyName(), d.getFinancialYear(), d.getRevenue(), d.getProfit(), d.getTotalDebt(),
                d.getTotalAssets(), d.getTotalLiabilities(), d.getCashFlow(), d.getEquity(), d.getInterestExpense(),
                d.getEbit(), d.getCurrentAssets(), d.getCurrentLiabilities(), d.getGstRevenue(), d.getBankInflow(),
                d.getBankOutflow(), d.getPrevRevenue(), d.getPrevProfit(), d.getPrevTotalDebt(), d.getCin(),
                d.getRegisteredAddress(), d.getAuditor(), d.getPromoterHolding()
        );
        for (Object f : fields) {
            total++;
            if (f != null) present++;
        }
        return total == 0 ? 0 : (double) present / total;
    }

    private double computeNumericAgreement(FinancialData.ExtractedFinancialData parser, FinancialData.ExtractedFinancialData llm) {
        List<Double> deltas = new ArrayList<>();
        addDelta(deltas, parser.getRevenue(), llm.getRevenue());
        addDelta(deltas, parser.getProfit(), llm.getProfit());
        addDelta(deltas, parser.getTotalDebt(), llm.getTotalDebt());
        addDelta(deltas, parser.getTotalAssets(), llm.getTotalAssets());
        addDelta(deltas, parser.getCashFlow(), llm.getCashFlow());
        addDelta(deltas, parser.getEquity(), llm.getEquity());

        if (deltas.isEmpty()) return 0.5;
        double avgDelta = deltas.stream().mapToDouble(Double::doubleValue).average().orElse(1.0);
        return Math.max(0.0, Math.min(1.0, 1.0 - avgDelta));
    }

    private void addDelta(List<Double> deltas, Double a, Double b) {
        if (a == null || b == null) return;
        double denom = Math.max(1.0, Math.abs(a));
        deltas.add(Math.min(1.0, Math.abs(a - b) / denom));
    }

    private List<String> mergeWarnings(List<List<String>> groups) {
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (List<String> group : groups) {
            if (group == null) continue;
            for (String msg : group) {
                if (msg != null && !msg.isBlank()) set.add(msg);
            }
        }
        return new ArrayList<>(set);
    }

    private List<String> nullableList(String... values) {
        List<String> out = new ArrayList<>();
        for (String value : values) {
            out.add(value);
        }
        return out;
    }

    private double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private void applyReliabilityGuards(FinancialData.ExtractedFinancialData data, String text) {
        List<String> warnings = data.getWarnings() == null ? new ArrayList<>() : new ArrayList<>(data.getWarnings());

        // Guard 1: Avoid speculative entity names not present in source text.
        if (data.getCompanyName() != null && !containsApprox(text, data.getCompanyName())) {
            warnings.add("Company name did not match source text confidently; value reset to null.");
            data.setCompanyName(null);
        }

        // Guard 2: Sanity checks on numeric fields used by downstream scoring.
        data.setRevenue(sanitizeFinancialValue(data.getRevenue(), "revenue", false, warnings));
        data.setProfit(sanitizeFinancialValue(data.getProfit(), "profit", true, warnings));
        data.setTotalDebt(sanitizeFinancialValue(data.getTotalDebt(), "totalDebt", false, warnings));
        data.setTotalAssets(sanitizeFinancialValue(data.getTotalAssets(), "totalAssets", false, warnings));
        data.setTotalLiabilities(sanitizeFinancialValue(data.getTotalLiabilities(), "totalLiabilities", false, warnings));
        data.setCashFlow(sanitizeFinancialValue(data.getCashFlow(), "cashFlow", true, warnings));
        data.setEquity(sanitizeFinancialValue(data.getEquity(), "equity", true, warnings));
        data.setInterestExpense(sanitizeFinancialValue(data.getInterestExpense(), "interestExpense", false, warnings));
        data.setEbit(sanitizeFinancialValue(data.getEbit(), "ebit", true, warnings));
        data.setCurrentAssets(sanitizeFinancialValue(data.getCurrentAssets(), "currentAssets", false, warnings));
        data.setCurrentLiabilities(sanitizeFinancialValue(data.getCurrentLiabilities(), "currentLiabilities", false, warnings));

        // Guard 3: Logical consistency checks.
        if (data.getTotalAssets() != null && data.getTotalLiabilities() != null && data.getTotalLiabilities() > data.getTotalAssets() * 3.0) {
            warnings.add("Total liabilities appear unusually high vs assets; verify balance sheet extraction.");
        }
        if (data.getRevenue() != null && data.getProfit() != null && Math.abs(data.getProfit()) > data.getRevenue() * 1.2) {
            warnings.add("Profit magnitude appears inconsistent with revenue; verify P&L extraction.");
        }

        // Guard 4: Conservative confidence adjustment for production reliability.
        double baseConfidence = data.getConfidence() == null ? 0.40 : data.getConfidence();
        double coverage = computeCoverage(data);
        int warningPenaltyCount = (int) warnings.stream()
                .filter(w -> w != null)
                .filter(w -> w.toLowerCase(Locale.ROOT).contains("verify")
                        || w.toLowerCase(Locale.ROOT).contains("reset to null")
                        || w.toLowerCase(Locale.ROOT).contains("inconsistent"))
                .count();
        double adjusted = baseConfidence * (0.55 + (0.45 * coverage)) - (warningPenaltyCount * 0.03);
        data.setConfidence(Math.round(clamp01(adjusted) * 100.0) / 100.0);

        data.setWarnings(warnings);
    }

    private boolean containsApprox(String text, String phrase) {
        if (text == null || phrase == null) return false;
        String normalizedText = text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ");
        String normalizedPhrase = phrase.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9\\s]", " ").trim();
        if (normalizedPhrase.isBlank()) return false;
        return normalizedText.contains(normalizedPhrase);
    }

    private Double sanitizeFinancialValue(Double value, String fieldName, boolean allowNegative, List<String> warnings) {
        if (value == null) return null;
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            warnings.add("Invalid numeric value detected for " + fieldName + "; value reset to null.");
            return null;
        }
        if (!allowNegative && value < 0) {
            warnings.add("Negative value detected for " + fieldName + "; value reset to null.");
            return null;
        }
        if (Math.abs(value) > 1_000_000) {
            warnings.add("Extreme value detected for " + fieldName + "; verify extraction units.");
        }
        return value;
    }

    private Double parseLooseNumber(String value) {
        if (value == null) return null;
        String cleaned = value.replace(",", "").trim();
        try {
            return Double.parseDouble(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private List<FinancialData.FinancialTable> extractTables(String text) {
        // Use a limited portion for table extraction
        String tableText = text.length() > 120000 ? text.substring(0, 120000) : text;
        try {
            String response = callGemini(TABLE_EXTRACTION_PROMPT + tableText);
            String cleanJson = cleanJsonResponse(response);
            return objectMapper.readValue(cleanJson,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, FinancialData.FinancialTable.class));
        } catch (Exception e) {
            log.warn("Table extraction via LLM failed: {}", e.getMessage());
            return extractTablesFromText(text);
        }
    }

    private List<FinancialData.FinancialTable> extractTablesFromText(String text) {
        List<FinancialData.FinancialTable> tables = new ArrayList<>();
        // Try to find pipe-separated tables
        Pattern tablePattern = Pattern.compile("(?m)^\\|(.+\\|)+$");
        Matcher matcher = tablePattern.matcher(text);

        List<String> tableLines = new ArrayList<>();
        int lastEnd = -1;

        while (matcher.find()) {
            if (lastEnd != -1 && matcher.start() - lastEnd > 2) {
                if (tableLines.size() >= 2) {
                    tables.add(parseTextTable(tableLines));
                }
                tableLines.clear();
            }
            tableLines.add(matcher.group().trim());
            lastEnd = matcher.end();
        }
        if (tableLines.size() >= 2) {
            tables.add(parseTextTable(tableLines));
        }
        return tables;
    }

    private FinancialData.FinancialTable parseTextTable(List<String> lines) {
        List<String> headers = new ArrayList<>();
        List<List<String>> rows = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i).replaceAll("^\\||\\|$", "");
            String[] cells = line.split("\\|");
            List<String> cellList = Arrays.stream(cells).map(String::trim).toList();

            if (i == 0) {
                headers = cellList;
            } else if (!line.matches("^[-|\\s]+$")) { // skip separator lines
                rows.add(cellList);
            }
        }

        return FinancialData.FinancialTable.builder()
                .title("Extracted Table")
                .headers(headers)
                .rows(rows)
                .build();
    }

    private String callGemini(String prompt) {
        try {
            return llmGateway.generateText(prompt, false, null, "gemini-2.0-flash");
        } catch (Exception e) {
            log.error("LLM call failed: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Clean JSON response: remove markdown fences, repair truncated JSON.
     */
    private String cleanJsonResponse(String response) {
        if (response == null) return "{}";
        String cleaned = response.trim();

        // Remove markdown fences
        cleaned = cleaned.replaceAll("```(?:json)?\\s*", "").replaceAll("```\\s*$", "");

        // Extract the outermost JSON object or array
        int objStart = cleaned.indexOf('{');
        int arrStart = cleaned.indexOf('[');
        if (objStart == -1 && arrStart == -1) return "{}";

        boolean isArray = (arrStart != -1 && (objStart == -1 || arrStart < objStart));
        int start = isArray ? arrStart : objStart;
        cleaned = cleaned.substring(start);

        // Repair truncated JSON
        cleaned = repairTruncatedJson(cleaned);
        return cleaned;
    }

    private String repairTruncatedJson(String json) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;

            if (c == '{' || c == '[') stack.push(c);
            else if (c == '}' || c == ']') {
                if (!stack.isEmpty()) stack.pop();
            }
        }

        // If inside a string, close it
        if (inString) json += "\"";

        // Close any unclosed brackets/braces
        StringBuilder sb = new StringBuilder(json);
        while (!stack.isEmpty()) {
            char open = stack.pop();
            sb.append(open == '{' ? '}' : ']');
        }
        return sb.toString();
    }
}
