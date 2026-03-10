package com.credereai.module1.engines;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class LlmOutputContractValidator {

    private final ObjectMapper objectMapper;

    private static final List<String> REQUIRED_FIELDS = List.of(
            "companyName", "financialYear", "documentType", "revenue", "profit", "totalDebt", "totalAssets",
            "totalLiabilities", "cashFlow", "equity", "interestExpense", "ebit", "currentAssets", "currentLiabilities",
            "gstRevenue", "bankInflow", "bankOutflow", "prev_revenue", "prev_profit", "prev_total_debt",
            "cin", "registeredAddress", "auditor", "directors", "promoters", "promoterHolding", "confidence", "warnings"
    );

    public JsonNode validateExtraction(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isObject()) {
                throw new IllegalArgumentException("Extraction contract violation: root is not a JSON object.");
            }

            for (String field : REQUIRED_FIELDS) {
                if (!root.has(field)) {
                    throw new IllegalArgumentException("Extraction contract violation: missing field '" + field + "'.");
                }
            }

            validateNumberOrNull(root, "confidence");
            validateNumberOrNull(root, "revenue");
            validateNumberOrNull(root, "profit");
            validateNumberOrNull(root, "totalDebt");
            validateNumberOrNull(root, "totalAssets");
            validateNumberOrNull(root, "totalLiabilities");

            JsonNode warnings = root.get("warnings");
            if (!(warnings.isNull() || warnings.isArray())) {
                throw new IllegalArgumentException("Extraction contract violation: 'warnings' must be array or null.");
            }

            JsonNode directors = root.get("directors");
            if (!(directors.isNull() || directors.isArray())) {
                throw new IllegalArgumentException("Extraction contract violation: 'directors' must be array or null.");
            }

            JsonNode promoters = root.get("promoters");
            if (!(promoters.isNull() || promoters.isArray())) {
                throw new IllegalArgumentException("Extraction contract violation: 'promoters' must be array or null.");
            }

            return root;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Extraction contract violation: malformed JSON. " + e.getMessage());
        }
    }

    private void validateNumberOrNull(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (!(node.isNull() || node.isNumber())) {
            throw new IllegalArgumentException("Extraction contract violation: '" + field + "' must be number or null.");
        }
    }
}
