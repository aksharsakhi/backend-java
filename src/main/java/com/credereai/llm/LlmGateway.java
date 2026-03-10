package com.credereai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlmGateway {

    private final LlmProviderConfig config;
    private final LlmRuntimeSettingsService runtimeSettings;
    @Qualifier("geminiWebClient")
    private final WebClient geminiWebClient;
    @Qualifier("groqWebClient")
    private final WebClient groqWebClient;
    private final ObjectMapper objectMapper;

    public String generateText(String prompt, boolean useSearch, String preferredModel, String fallbackModel) {
        String provider = runtimeSettings.activeProvider();
        if ("groq".equalsIgnoreCase(provider)) {
            try {
                return callGroq(prompt, preferredModel);
            } catch (Exception e) {
                log.warn("Groq primary call failed: {}", e.getMessage());
                if (fallbackModel != null && !fallbackModel.isBlank()) {
                    return callGroq(prompt, fallbackModel);
                }
                throw e;
            }
        }

        try {
            return callGemini(prompt, useSearch, preferredModel);
        } catch (Exception e) {
            log.warn("Gemini primary call failed: {}", e.getMessage());
            if (fallbackModel != null && !fallbackModel.isBlank()) {
                return callGemini(prompt, useSearch, fallbackModel);
            }
            throw e;
        }
    }

    private String callGemini(String prompt, boolean useSearch, String modelOverride) {
        String model = runtimeSettings.resolveModelForProvider("gemini", modelOverride);
        String url = String.format("/v1beta/models/%s:generateContent?key=%s", model, config.getGeminiApiKey());

        var baseBody = new java.util.LinkedHashMap<String, Object>();
        baseBody.put("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
        if (useSearch) {
            baseBody.put("tools", List.of(Map.of("google_search", Map.of())));
        }
        baseBody.put("generationConfig", Map.of("temperature", 0.2, "maxOutputTokens", 8192));

        String responseBody = geminiWebClient.post().uri(url).bodyValue(baseBody)
                .retrieve().bodyToMono(String.class).block();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode candidates = root.path("candidates");
            if (candidates.isArray() && !candidates.isEmpty()) {
                JsonNode parts = candidates.get(0).path("content").path("parts");
                for (JsonNode part : parts) {
                    if (part.has("text")) return part.get("text").asText();
                }
            }
            throw new RuntimeException("No text in Gemini response");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Gemini response: " + e.getMessage());
        }
    }

    private String callGroq(String prompt, String modelOverride) {
        String model = runtimeSettings.resolveModelForProvider("groq", modelOverride);
        String apiKey = config.getGroqApiKey();
        if (apiKey == null || apiKey.isBlank()) {
            throw new RuntimeException("GROQ_API_KEY is not configured");
        }

        Map<String, Object> body = Map.of(
                "model", model,
                "temperature", 0.2,
                "messages", List.of(Map.of("role", "user", "content", prompt))
        );

        String responseBody = groqWebClient.post()
                .uri("/openai/v1/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .bodyValue(body)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode content = root.path("choices").get(0).path("message").path("content");
            if (!content.isMissingNode()) return content.asText();
            throw new RuntimeException("No content in Groq response");
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse Groq response: " + e.getMessage());
        }
    }
}
