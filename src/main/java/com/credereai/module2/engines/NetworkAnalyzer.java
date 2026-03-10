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
public class NetworkAnalyzer {

    private final Module2Config config;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;
    private final JsonUtils jsonUtils;

    private static final String PROMPT_TEMPLATE = """
            You are a corporate intelligence analyst. Research the corporate network and relationships for: %s
            %s
            Return ONLY a valid JSON object:
            {
              "nodes": [{"id": "unique_id", "name": "Entity Name", "type": "company|individual|government", "relationship": "description", "riskScore": 0.0-1.0}],
              "edges": [{"source": "node_id", "target": "node_id", "relationship": "description", "weight": 0.0-1.0}],
              "summary": "Brief network analysis summary"
            }
            Focus on: subsidiaries, group companies, key related parties, common directors, significant shareholders.
            If you find risk indicators (shell companies, circular ownership, etc.), flag them with higher risk scores.
            """;

    public NetworkGraph analyze(ResearchInput input) {
        String context = "";
        if (input.getCin() != null) context += "CIN: " + input.getCin() + ". ";
        if (input.getIndustry() != null) context += "Industry: " + input.getIndustry() + ". ";
        if (input.getPromoterNames() != null && !input.getPromoterNames().isEmpty()) {
            context += "Promoters: " + String.join(", ", input.getPromoterNames()) + ". ";
        }

        String prompt = String.format(PROMPT_TEMPLATE, input.getCompanyName(), context);

        // Try with primary model + google search grounding
        try {
            String response = llmGateway.generateText(prompt, true, null, null);
            return parseNetworkGraph(response);
        } catch (Exception e) {
            log.error("Network analysis failed completely: {}", e.getMessage());
            return NetworkGraph.builder()
                    .nodes(List.of(NetworkNode.builder()
                            .id("target").name(input.getCompanyName()).type("company")
                            .relationship("Target company").riskScore(0.0).build()))
                    .edges(List.of())
                    .summary("Network analysis could not be completed. Limited data available.")
                    .build();
        }
    }

    private NetworkGraph parseNetworkGraph(String response) throws Exception {
        String json = jsonUtils.parseLlmJson(response);
        return objectMapper.readValue(json, NetworkGraph.class);
    }
}
