package com.credereai.module2.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class Module2Config {

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    @Value("${GEMINI_MODEL:gemini-2.5-flash}")
    private String geminiModel;

    @Value("${MODULE1_API_BASE:http://localhost:8001/api}")
    private String module1ApiBase;

    private final String fallbackModel = "gemini-2.0-flash";
}
