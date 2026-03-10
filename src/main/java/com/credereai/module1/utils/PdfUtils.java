package com.credereai.module1.utils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PdfUtils {

    private static final Map<String, String[]> SECTION_PATTERNS = Map.ofEntries(
            Map.entry("balance_sheet", new String[]{"(?i)balance\\s*sheet", "(?i)statement\\s*of\\s*financial\\s*position"}),
            Map.entry("profit_loss", new String[]{"(?i)profit\\s*and\\s*loss", "(?i)statement\\s*of\\s*profit", "(?i)income\\s*statement"}),
            Map.entry("cash_flow", new String[]{"(?i)cash\\s*flow\\s*statement", "(?i)statement\\s*of\\s*cash\\s*flows"}),
            Map.entry("notes", new String[]{"(?i)notes\\s*to\\s*(?:the\\s*)?financial\\s*statements", "(?i)notes\\s*forming\\s*part"}),
            Map.entry("auditor_report", new String[]{"(?i)independent\\s*auditor", "(?i)auditor'?s\\s*report"}),
            Map.entry("directors_report", new String[]{"(?i)director'?s\\s*report", "(?i)board'?s\\s*report"}),
            Map.entry("corporate_governance", new String[]{"(?i)corporate\\s*governance"}),
            Map.entry("management_discussion", new String[]{"(?i)management\\s*discussion", "(?i)MD\\s*&\\s*A"}),
            Map.entry("equity_changes", new String[]{"(?i)changes\\s*in\\s*equity", "(?i)statement\\s*of\\s*changes"}),
            Map.entry("schedules", new String[]{"(?i)schedule\\s+\\d+", "(?i)annexure"}),
            Map.entry("ratios", new String[]{"(?i)key\\s*(?:financial\\s*)?ratios", "(?i)ratio\\s*analysis"})
    );

    public String extractTextFromPdf(File file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);
        }
    }

    public String extractTextFromPdf(File file, int startPage, int endPage) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(startPage);
            stripper.setEndPage(endPage);
            return stripper.getText(document);
        }
    }

    public int getPageCount(File file) throws IOException {
        try (PDDocument document = Loader.loadPDF(file)) {
            return document.getNumberOfPages();
        }
    }

    /**
     * Extract financial sections from PDF text using pattern matching.
     * Returns a map of section-name -> extracted text for that section.
     */
    public Map<String, String> extractFinancialSections(String text) {
        Map<String, String> sections = new LinkedHashMap<>();
        List<SectionMatch> matches = new ArrayList<>();

        for (Map.Entry<String, String[]> entry : SECTION_PATTERNS.entrySet()) {
            String sectionName = entry.getKey();
            for (String patternStr : entry.getValue()) {
                Pattern pattern = Pattern.compile(patternStr);
                Matcher matcher = pattern.matcher(text);
                if (matcher.find()) {
                    matches.add(new SectionMatch(sectionName, matcher.start(), patternStr));
                    break;
                }
            }
        }

        matches.sort(Comparator.comparingInt(a -> a.start));

        for (int i = 0; i < matches.size(); i++) {
            SectionMatch current = matches.get(i);
            int end = (i + 1 < matches.size()) ? matches.get(i + 1).start : Math.min(current.start + 50000, text.length());
            String sectionText = text.substring(current.start, end);
            if (sectionText.length() > 30000) {
                sectionText = sectionText.substring(0, 30000);
            }
            sections.put(current.name, sectionText);
        }

        return sections;
    }

    public Double parseCurrencyValue(String text) {
        if (text == null || text.isBlank()) return null;
        String cleaned = text.replaceAll("[₹$,\\s]", "").trim();

        // Handle lakh and crore
        double multiplier = 1.0;
        String lower = cleaned.toLowerCase();
        if (lower.contains("crore") || lower.contains("cr")) {
            multiplier = 1e7;
            cleaned = lower.replaceAll("(?:crore|cr\\.?)", "").trim();
        } else if (lower.contains("lakh") || lower.contains("lac")) {
            multiplier = 1e5;
            cleaned = lower.replaceAll("(?:lakh|lac)", "").trim();
        }

        // Handle parentheses for negative values
        boolean negative = cleaned.startsWith("(") && cleaned.endsWith(")");
        if (negative) cleaned = cleaned.substring(1, cleaned.length() - 1);

        try {
            double value = Double.parseDouble(cleaned) * multiplier;
            return negative ? -value : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private record SectionMatch(String name, int start, String pattern) {}
}
