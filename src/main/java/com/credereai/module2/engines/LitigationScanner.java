package com.credereai.module2.engines;

import com.credereai.module2.config.Module2Config;
import com.credereai.llm.LlmGateway;
import com.credereai.module2.models.ResearchData.*;
import com.credereai.module2.utils.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class LitigationScanner {

    private final Module2Config config;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;
    private final JsonUtils jsonUtils;

    private static final String PROMPT_TEMPLATE = """
            You are a legal research analyst. Search for litigation records and regulatory actions for: %s
            %s
            Return ONLY a valid JSON object:
            {
              "litigationRecords": [
                {"caseNumber": "str", "court": "str", "parties": "str", "status": "pending|disposed|settled",
                 "nature": "civil|criminal|regulatory|tax", "filingDate": "str", "amount": number_or_null,
                 "riskLevel": "LOW|MEDIUM|HIGH|CRITICAL", "summary": "brief description"}
              ],
              "regulatoryActions": [
                {"authority": "SEBI|RBI|MCA|NCLT|etc", "actionType": "penalty|warning|ban|investigation",
                 "date": "str", "description": "str", "penalty": number_or_null,
                 "severity": "LOW|MEDIUM|HIGH|CRITICAL"}
              ]
            }
            Search for: NCLT cases, SEBI orders, RBI actions, MCA defaults, tax disputes, consumer complaints, environmental violations.
            """;

    public LitigationResult scan(ResearchInput input) {
        String context = buildContext(input);
        String prompt = String.format(PROMPT_TEMPLATE, input.getCompanyName(), context);

        try {
            String response = llmGateway.generateText(prompt, true, null, null);
            return parseLitigationResult(response);
        } catch (Exception e) {
            log.error("Litigation scan failed completely: {}", e.getMessage());
            return new LitigationResult(List.of(), List.of());
        }
    }

    private String buildContext(ResearchInput input) {
        StringBuilder ctx = new StringBuilder();
        if (input.getCin() != null) ctx.append("CIN: ").append(input.getCin()).append(". ");
        if (input.getPromoterNames() != null && !input.getPromoterNames().isEmpty()) {
            ctx.append("Promoters: ").append(String.join(", ", input.getPromoterNames())).append(". ");
        }
        return ctx.toString();
    }

    private LitigationResult parseLitigationResult(String response) throws Exception {
        String json = jsonUtils.parseLlmJson(response);
        JsonNode root = objectMapper.readTree(json);

        List<LitigationRecord> records = List.of();
        List<RegulatoryAction> actions = List.of();

        if (root.has("litigationRecords")) {
            records = objectMapper.readValue(root.get("litigationRecords").toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, LitigationRecord.class));
        }
        if (root.has("regulatoryActions")) {
            actions = objectMapper.readValue(root.get("regulatoryActions").toString(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, RegulatoryAction.class));
        }
        return new LitigationResult(records, actions);
    }

    public record LitigationResult(List<LitigationRecord> records, List<RegulatoryAction> actions) {}
}
