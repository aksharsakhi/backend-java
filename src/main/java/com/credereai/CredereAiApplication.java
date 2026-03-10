package com.credereai;

import io.github.cdimascio.dotenv.Dotenv;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class CredereAiApplication {
    public static void main(String[] args) {
        // Load .env (if present) and expose keys as system properties.
        Dotenv dotenv = Dotenv.configure().ignoreIfMissing().load();
        setIfPresent(dotenv, "LLM_PROVIDER");
        setIfPresent(dotenv, "GEMINI_API_KEY");
        setIfPresent(dotenv, "GEMINI_MODEL");
        setIfPresent(dotenv, "GROQ_API_KEY");
        setIfPresent(dotenv, "GROQ_MODEL");

        SpringApplication.run(CredereAiApplication.class, args);
    }

    private static void setIfPresent(Dotenv dotenv, String key) {
        String value = dotenv.get(key);
        if (value != null && !value.isBlank() && System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }
}
