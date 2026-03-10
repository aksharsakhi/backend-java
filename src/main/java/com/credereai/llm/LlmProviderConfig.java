package com.credereai.llm;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class LlmProviderConfig {

    @Value("${LLM_PROVIDER:groq}")
    private String provider;

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    @Value("${GEMINI_MODEL:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${GROQ_API_KEY:}")
    private String groqApiKey;

    @Value("${GROQ_MODEL:llama-3.3-70b-versatile}")
    private String groqModel;

    public boolean useGroq() {
        return "groq".equalsIgnoreCase(provider);
    }
}
