package com.credereai.llm;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class LlmRuntimeSettingsService {

    private static final String PROVIDER_GEMINI = "gemini";
    private static final String PROVIDER_GROQ = "groq";

    private static final Map<String, List<String>> MODELS_BY_PROVIDER = Map.of(
            PROVIDER_GEMINI, List.of("gemini-2.5-flash", "gemini-2.0-flash"),
            PROVIDER_GROQ, List.of("llama-3.3-70b-versatile", "llama-3.1-8b-instant")
    );

    private volatile String provider;
    private volatile String geminiModel;
    private volatile String groqModel;

    public LlmRuntimeSettingsService(LlmProviderConfig config) {
        this.provider = normalizeProvider(config.getProvider());
        this.geminiModel = pickModel(PROVIDER_GEMINI, config.getGeminiModel());
        this.groqModel = pickModel(PROVIDER_GROQ, config.getGroqModel());
    }

    public synchronized SettingsSnapshot update(String requestedProvider, String requestedModel) {
        String normalizedProvider = normalizeProvider(requestedProvider);
        String normalizedModel = pickModel(normalizedProvider, requestedModel);

        provider = normalizedProvider;
        if (PROVIDER_GEMINI.equals(normalizedProvider)) {
            geminiModel = normalizedModel;
        } else {
            groqModel = normalizedModel;
        }
        return snapshot();
    }

    public SettingsSnapshot snapshot() {
        String activeProvider = provider;
        String activeModel = activeModelForProvider(activeProvider);

        Map<String, List<String>> models = new LinkedHashMap<>();
        models.put(PROVIDER_GEMINI, MODELS_BY_PROVIDER.get(PROVIDER_GEMINI));
        models.put(PROVIDER_GROQ, MODELS_BY_PROVIDER.get(PROVIDER_GROQ));

        return new SettingsSnapshot(
                activeProvider,
                activeModel,
                geminiModel,
                groqModel,
                List.of(PROVIDER_GEMINI, PROVIDER_GROQ),
                models
        );
    }

    public String activeProvider() {
        return provider;
    }

    public String resolveModelForProvider(String providerName, String overrideModel) {
        if (overrideModel != null && !overrideModel.isBlank()) {
            return overrideModel;
        }

        String normalizedProvider = normalizeProvider(providerName);
        return activeModelForProvider(normalizedProvider);
    }

    private String activeModelForProvider(String providerName) {
        return PROVIDER_GROQ.equals(providerName) ? groqModel : geminiModel;
    }

    private String normalizeProvider(String raw) {
        if (raw == null) {
            return PROVIDER_GEMINI;
        }

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        return PROVIDER_GROQ.equals(normalized) ? PROVIDER_GROQ : PROVIDER_GEMINI;
    }

    private String pickModel(String providerName, String requestedModel) {
        List<String> allowed = MODELS_BY_PROVIDER.get(providerName);
        if (requestedModel != null && !requestedModel.isBlank() && allowed.contains(requestedModel)) {
            return requestedModel;
        }
        return allowed.get(0);
    }

    public record SettingsSnapshot(
            String provider,
            String activeModel,
            String geminiModel,
            String groqModel,
            List<String> availableProviders,
            Map<String, List<String>> modelsByProvider
    ) {}
}
