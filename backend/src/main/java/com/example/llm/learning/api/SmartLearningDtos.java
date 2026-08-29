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
            int learningProgress,
            int completedTaskCount,
            int totalTaskCount,
            LocalDateTime pinnedAt,
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
            LocalDateTime pinnedAt,
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

    public record PinRequest(boolean pinned) {
    }

    public record SidebarProjectView(
            String projectId,
            String name,
            String icon,
            String iconColor,
            String stage,
            LocalDateTime pinnedAt,
            List<TutorConversationView> conversations,
            LocalDateTime updatedAt) {
    }

    public record TutorConversationView(
            String conversationId,
            String title,
            String taskId,
            String taskTitle,
            String contextType,
            LocalDateTime updatedAt) {
    }

    public record Workspace(
            String projectId,
            String projectName,
            String stage,
            int progress,
            int completedTaskCount,
            int totalTaskCount,
            int wrongItemCount,
            int pendingWrongItemCount,
            Map<String, Object> profile,
            List<TaskView> tasks,
            List<ResourceView> resources,
            ExecutionView activeExecution,
            LocalDateTime updatedAt) {
    }

    public record WrongItemView(
            String wrongItemId,
            String projectId,
            String taskId,
            String questionId,
            String stem,
            String userAnswer,
            String correctAnswer,
            String explanation,
            String knowledgeKey,
            String status,
            LocalDateTime updatedAt) {
    }

    public record ReviewWrongItemRequest(String answer) {
    }

    public record TutorThreadView(
            String threadId,
            String conversationId,
            String projectId,
            String taskId,
            String contextType) {
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

    public record ExerciseQuestionGrade(
            String questionId,
            int index,
            boolean answered,
            boolean correct,
            String answer,
            String correctAnswer,
            String explanation) {
    }

    public record ExerciseGrade(
            int total,
            int answered,
            int correct,
            double accuracy,
            List<ExerciseQuestionGrade> items) {
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
            LocalDateTime updatedAt,
            ExerciseGrade grading) {

        public ExecutionView(
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
            this(executionId, projectId, taskId, status, progress, accumulatedSeconds,
                    position, answers, score, lastHeartbeatSeq, startedAt, pausedAt,
                    completedAt, updatedAt, null);
        }
    }

    public record StartExecutionRequest(String idempotencyKey) {
    }

    public record ExecutionProgressRequest(double progress, int secondsDelta) {
    }

    public record ExecutionHeartbeatRequest(long sequence, int secondsDelta) {
    }
}
