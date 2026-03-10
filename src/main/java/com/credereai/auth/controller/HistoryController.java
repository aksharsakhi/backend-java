package com.credereai.auth.controller;

import com.credereai.auth.models.AuthModels.AnalysisHistoryRecord;
import com.credereai.auth.models.AuthModels.SaveHistoryRequest;
import com.credereai.auth.services.AuthService;
import com.credereai.auth.services.HistoryService;
import com.credereai.module1.models.Responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/history")
@RequiredArgsConstructor
public class HistoryController {

    private final AuthService authService;
    private final HistoryService historyService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AnalysisHistoryRecord>>> list(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @RequestParam(value = "limit", defaultValue = "25") int limit) {

        String username = authService.resolveUsername(token).orElse(null);
        if (username == null) {
            return unauthorizedList();
        }

        List<AnalysisHistoryRecord> items = historyService.list(username, limit);
        return ResponseEntity.ok(ApiResponse.<List<AnalysisHistoryRecord>>builder()
                .success(true)
                .message("History fetched")
                .data(items)
                .build());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<AnalysisHistoryRecord>> getById(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @PathVariable String id) {

        String username = authService.resolveUsername(token).orElse(null);
        if (username == null) {
            return unauthorizedItem();
        }

        return historyService.getById(username, id)
                .map(item -> ResponseEntity.ok(ApiResponse.<AnalysisHistoryRecord>builder()
                        .success(true)
                        .message("History record fetched")
                        .data(item)
                        .build()))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiResponse.<AnalysisHistoryRecord>builder()
                                .success(false)
                                .message("History record not found")
                                .build()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AnalysisHistoryRecord>> save(
            @RequestHeader(value = "X-Auth-Token", required = false) String token,
            @RequestBody SaveHistoryRequest request) {

        String username = authService.resolveUsername(token).orElse(null);
        if (username == null) {
            return unauthorizedItem();
        }

        AnalysisHistoryRecord saved = historyService.save(username, request);
        return ResponseEntity.ok(ApiResponse.<AnalysisHistoryRecord>builder()
                .success(true)
                .message("Analysis snapshot saved")
                .data(saved)
                .build());
    }

    private ResponseEntity<ApiResponse<List<AnalysisHistoryRecord>>> unauthorizedList() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.<List<AnalysisHistoryRecord>>builder()
                        .success(false)
                        .message("Unauthorized. Please login.")
                        .build());
    }

    private ResponseEntity<ApiResponse<AnalysisHistoryRecord>> unauthorizedItem() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.<AnalysisHistoryRecord>builder()
                        .success(false)
                        .message("Unauthorized. Please login.")
                        .build());
    }
}
