package com.example.llm.learning.api;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class SmartLearningDtos {
    private SmartLearningDtos() {
    }

    public record CreateProjectRequest(
            String name,
            String icon,
            String iconColor,
            String knowledgeBaseId) {
    }

    public record ProjectSummary(
            String projectId,
            String name,
            String icon,
            String iconColor,
            String knowledgeBaseId,
            String stage,
            String nextStep,
            int targetVersion,
            int sourceVersion,
            int scopeVersion,
            int diagnosisVersion,
            int planVersion,
            int resourceConfigVersion,
            LocalDateTime updatedAt) {
    }

    public record ProjectDetail(
            String projectId,
            String name,
            String icon,
            String iconColor,
            String knowledgeBaseId,
            String stage,
            String nextStep,
            Map<String, Object> target,
            Map<String, Object> targetDraft,
            Map<String, Object> sources,
            Map<String, Object> sourcesDraft,
            Map<String, Object> scope,
            Map<String, Object> scopeCandidate,
            Map<String, Object> diagnosis,
            Map<String, Object> diagnosisCandidate,
            Map<String, Object> diagnosisAnswersDraft,
            Map<String, Object> plan,
            Map<String, Object> planCandidate,
            Map<String, Object> resourceConfig,
            Map<String, Object> resourceConfigDraft,
            Map<String, Integer> versions,
            JobView activeJob,
            LocalDateTime updatedAt) {
    }

    public record JobView(
            String jobId,
            String projectId,
            String kind,
            String status,
            int progressCurrent,
            int progressTotal,
            Map<String, Object> result,
            String errorMessage,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    public record JobAccepted(String jobId, String projectId, String kind, String status) {
    }

    public record SourceSelection(String assetId, String versionId, String purpose) {
    }

    public record DiagnosisAnswer(String questionId, Object answer) {
    }

    public record RenameRequest(String name, String icon, String iconColor) {
        public RenameRequest(String name) { this(name, null, null); }
    }

    public record Workspace(
            String projectId,
            String projectName,
            String stage,
            int progress,
            List<TaskView> tasks,
            List<ResourceView> resources,
            ExecutionView activeExecution,
            LocalDateTime updatedAt) {
    }

    public record TaskView(
            String taskId,
            String title,
            String taskType,
            String description,
            String completionCriteria,
            LocalDate scheduledDate,
            int durationMinutes,
            String status,
            int sortOrder,
            Map<String, Object> payload,
            List<ResourceView> resources,
            ExecutionView execution,
            LocalDateTime updatedAt) {
    }

    public record ResourceView(
            String resourceId,
            String taskId,
            String kind,
            String title,
            String status,
            Map<String, Object> content,
            String errorMessage,
            LocalDateTime updatedAt) {
    }

    public record ExecutionView(
            String executionId,
            String projectId,
            String taskId,
            String status,
            double progress,
            int accumulatedSeconds,
            Map<String, Object> position,
            Map<String, Object> answers,
            Double score,
            long lastHeartbeatSeq,
            LocalDateTime startedAt,
            LocalDateTime pausedAt,
            LocalDateTime completedAt,
            LocalDateTime updatedAt) {
    }

    public record StartExecutionRequest(String idempotencyKey) {
    }

    public record ExecutionProgressRequest(double progress, int secondsDelta) {
    }

    public record ExecutionHeartbeatRequest(long sequence, int secondsDelta) {
    }
}
