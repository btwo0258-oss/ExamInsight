package com.example.llm.chatv2.artifact;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ArtifactModels {
    private ArtifactModels() {
    }

    public enum Type {
        DOCUMENT,
        MINDMAP,
        PRESENTATION,
        IMAGE
    }

    public record ArtifactView(
            String id,
            String conversationId,
            String runId,
            Type type,
            String status,
            String title,
            int schemaVersion,
            Map<String, Object> content,
            int revision,
            long version,
            String confirmedAssetId,
            String confirmedAssetVersionId,
            String errorCode,
            Instant createdAt,
            Instant updatedAt,
            Instant confirmedAt) {
        public ArtifactView {
            content = content == null
                    ? Map.of()
                    : Collections.unmodifiableMap(new LinkedHashMap<>(content));
        }
    }

    public record UpdateArtifactRequest(
            @NotBlank @Size(max = 255) String title,
            @NotNull Map<String, Object> content,
            @NotNull Long version) {
    }

    public record DocumentDraftInput(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 100000) String markdown) {
    }

    public record MindMapDraftInput(
            @NotBlank @Size(max = 255) String title,
            @NotNull MindMapNode root) {
    }

    public record MindMapNode(
            @NotBlank @Size(max = 500) String text,
            List<MindMapNode> children) {
        public MindMapNode {
            children = children == null ? List.of() : List.copyOf(children);
        }
    }

    public record PresentationDraftInput(
            @NotBlank @Size(max = 255) String title,
            @NotNull @Size(min = 1, max = 40) List<SlideInput> slides) {
        public PresentationDraftInput {
            slides = slides == null ? List.of() : List.copyOf(slides);
        }
    }

    public record SlideInput(
            @NotBlank @Size(max = 255) String title,
            @Size(max = 20) List<@Size(max = 1000) String> bullets,
            @Size(max = 4000) String speakerNotes) {
        public SlideInput {
            bullets = bullets == null ? List.of() : List.copyOf(bullets);
        }
    }

    public record ImageGenerationInput(
            @NotBlank @Size(max = 255) String title,
            @NotBlank @Size(max = 4000) String prompt,
            Integer width,
            Integer height) {
    }

    public record ToolResult(
            String status,
            String artifactId,
            Type type,
            String title,
            String assetId,
            String message) {
    }
}
