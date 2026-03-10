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
public class PromoterAnalyzer {

    private final Module2Config config;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;
    private final JsonUtils jsonUtils;

    private static final String PROMPT_TEMPLATE = """
            You are a due diligence analyst. Research the promoters and key management of: %s
            %s
            Return ONLY a valid JSON object:
            {
              "profiles": [
                {"name": "full name", "designation": "role/title", "holdingPercent": number_or_null,
                 "otherCompanies": ["list of other companies associated"],
                 "flags": ["any red flags or concerns"],
                 "backgroundSummary": "brief background", "riskScore": 0.0-1.0}
              ]
            }
            Research: personal background, other directorships, past defaults, regulatory actions,
            criminal cases, political connections, promoter pledge data, related party transactions.
            """;

    public List<PromoterProfile> analyze(ResearchInput input) {
        String context = "";
        if (input.getCin() != null) context += "CIN: " + input.getCin() + ". ";
        if (input.getPromoterNames() != null && !input.getPromoterNames().isEmpty()) {
            context += "Known promoters: " + String.join(", ", input.getPromoterNames()) + ". ";
        }

        String prompt = String.format(PROMPT_TEMPLATE, input.getCompanyName(), context);

        try {
            String response = llmGateway.generateText(prompt, true, null, null);
            return parseProfiles(response);
        } catch (Exception e) {
            log.error("Promoter analysis failed completely: {}", e.getMessage());
            return List.of();
        }
    }

    private List<PromoterProfile> parseProfiles(String response) throws Exception {
        String json = jsonUtils.parseLlmJson(response);
        JsonNode root = objectMapper.readTree(json);
        JsonNode profiles = root.has("profiles") ? root.get("profiles") : root;
        return objectMapper.readValue(profiles.toString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, PromoterProfile.class));
    }
}
