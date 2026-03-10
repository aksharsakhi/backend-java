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
public class NewsResearcher {

    private final Module2Config config;
    private final LlmGateway llmGateway;
    private final ObjectMapper objectMapper;
    private final JsonUtils jsonUtils;

    private static final String PROMPT_TEMPLATE = """
            You are a financial news analyst. Research recent news and media signals for: %s
            %s
            Return ONLY a valid JSON object:
            {
              "signals": [
                {"title": "headline", "source": "publication name", "date": "YYYY-MM-DD or approximate",
                 "sentiment": "positive|negative|neutral", "relevanceScore": 0.0-1.0,
                 "summary": "brief description", "url": "source url or null"}
              ]
            }
            Focus on: financial performance news, management changes, regulatory news, market sentiment,
            ESG concerns, major contracts/deals, credit rating changes, merger/acquisition activity.
            Return the most recent and relevant 5-10 signals.
            """;

    public List<NewsSignal> research(ResearchInput input) {
        String context = "";
        if (input.getIndustry() != null) context += "Industry: " + input.getIndustry() + ". ";
        String prompt = String.format(PROMPT_TEMPLATE, input.getCompanyName(), context);

        try {
            String response = llmGateway.generateText(prompt, true, null, null);
            return parseSignals(response);
        } catch (Exception e) {
            log.error("News research failed completely: {}", e.getMessage());
            return List.of();
        }
    }

    private List<NewsSignal> parseSignals(String response) throws Exception {
        String json = jsonUtils.parseLlmJson(response);
        JsonNode root = objectMapper.readTree(json);
        JsonNode signals = root.has("signals") ? root.get("signals") : root;
        return objectMapper.readValue(signals.toString(),
                objectMapper.getTypeFactory().constructCollectionType(List.class, NewsSignal.class));
    }
}
