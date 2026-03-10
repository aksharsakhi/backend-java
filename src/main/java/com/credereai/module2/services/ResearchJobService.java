package com.credereai.module2.services;

import com.credereai.module2.models.ResearchData.ResearchInput;
import com.credereai.module2.models.ResearchData.ResearchReport;
import lombok.Builder;
import lombok.Data;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ResearchJobService {

    private final ResearchOrchestrator researchOrchestrator;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    private final Map<String, ResearchJobStatus> jobs = new ConcurrentHashMap<>();

    public ResearchJobService(ResearchOrchestrator researchOrchestrator) {
        this.researchOrchestrator = researchOrchestrator;
    }

    public ResearchJobStatus submit(ResearchInput input) {
        String jobId = "JOB-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        ResearchJobStatus status = ResearchJobStatus.builder()
                .jobId(jobId)
                .status("QUEUED")
                .submittedAt(LocalDateTime.now())
                .build();
        jobs.put(jobId, status);

        CompletableFuture.runAsync(() -> runJob(jobId, input), executor);
        return status;
    }

    public ResearchJobStatus get(String jobId) {
        return jobs.get(jobId);
    }

    private void runJob(String jobId, ResearchInput input) {
        ResearchJobStatus running = jobs.get(jobId);
        if (running == null) {
            return;
        }
        running.setStatus("RUNNING");
        running.setStartedAt(LocalDateTime.now());

        try {
            ResearchReport report = researchOrchestrator.conductResearch(input);
            running.setStatus("COMPLETED");
            running.setCompletedAt(LocalDateTime.now());
            running.setReport(report);
        } catch (Exception e) {
            running.setStatus("FAILED");
            running.setCompletedAt(LocalDateTime.now());
            running.setError(e.getMessage());
        }
    }

    @Data
    @Builder
    public static class ResearchJobStatus {
        private String jobId;
        private String status;
        private LocalDateTime submittedAt;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private String error;
        private ResearchReport report;
    }
}
