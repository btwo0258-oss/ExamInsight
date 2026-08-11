package com.example.llm.asset.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class UploadDtos {
    private UploadDtos() {
    }

    public record CreateUploadRequest(
            @NotBlank
            @Size(max = 160)
            @Pattern(regexp = "[A-Za-z0-9._:-]+", message = "uploadKey 只能包含字母、数字、点、下划线、冒号或短横线")
            String uploadKey,

            @NotBlank
            @Size(max = 255)
            String originalFilename,

            @Size(max = 160)
            String declaredMime,

            @Positive
            @Max(104_857_600)
            long expectedSize,

            @Pattern(regexp = "[0-9a-fA-F]{64}", message = "expectedSha256 必须是 64 位十六进制摘要")
            String expectedSha256) {
    }

    public record UploadSessionResponse(
            String uploadId,
            String originalFilename,
            String status,
            long expectedSize,
            long uploadedBytes,
            int partSize,
            int expectedPartCount,
            Instant expiresAt) {
    }

    public record UploadPartResponse(
            String uploadId,
            String status,
            int partNumber,
            long uploadedBytes,
            long expectedSize) {
    }

    public record UploadCompletionResponse(
            String uploadId,
            String status,
            AssetSummary asset,
            VersionSummary version,
            JobSummary securityScanJob) {
    }

    public record AssetSummary(String assetId, String name, String status) {
    }

    public record VersionSummary(
            String versionId,
            int versionNumber,
            String status,
            String mimeType,
            long sizeBytes,
            String sha256) {
    }

    public record JobSummary(String jobId, String status, String stage) {
    }
}
