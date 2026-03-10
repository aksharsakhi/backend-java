package com.credereai.module2.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

public class Responses {

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ApiResponse<T> {
        private boolean success;
        private String message;
        @Builder.Default
        private T data = null;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class ResearchResponse {
        private ResearchData.ResearchReport report;
    }

    @Data @Builder @NoArgsConstructor @AllArgsConstructor
    public static class Module1DataResponse {
        private String companyName;
        private String cin;
        private String industry;
        private java.util.List<String> promoterNames;
        private java.util.List<String> directors;
    }
}
