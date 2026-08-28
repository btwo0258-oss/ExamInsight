package com.example.llm.learning.api;

import java.time.LocalDateTime;
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

    public record RenameRequest(String name) {
    }
}
