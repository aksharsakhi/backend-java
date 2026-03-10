package com.credereai.module2.utils;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;

@Slf4j
@Component
public class JsonUtils {

    /**
     * Parse LLM JSON response: strip markdown fences, extract JSON, repair truncation.
     */
    public String parseLlmJson(String response) {
        if (response == null || response.isBlank()) return "{}";
        String cleaned = response.trim();

        // Remove markdown fences
        cleaned = cleaned.replaceAll("```(?:json)?\\s*", "").replaceAll("```\\s*$", "");

        // Find outermost JSON
        int objStart = cleaned.indexOf('{');
        int arrStart = cleaned.indexOf('[');
        if (objStart == -1 && arrStart == -1) return "{}";

        boolean isArray = (arrStart != -1 && (objStart == -1 || arrStart < objStart));
        int start = isArray ? arrStart : objStart;
        cleaned = cleaned.substring(start);

        // Repair truncated JSON
        return repairTruncatedJson(cleaned);
    }

    private String repairTruncatedJson(String json) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\' && inString) { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;

            if (c == '{' || c == '[') stack.push(c);
            else if (c == '}' || c == ']') {
                if (!stack.isEmpty()) stack.pop();
            }
        }

        StringBuilder sb = new StringBuilder(json);
        if (inString) sb.append('"');

        while (!stack.isEmpty()) {
            char open = stack.pop();
            sb.append(open == '{' ? '}' : ']');
        }
        return sb.toString();
    }
}
