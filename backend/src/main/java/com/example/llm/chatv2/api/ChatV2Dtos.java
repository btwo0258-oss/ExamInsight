package com.example.llm.chatv2.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class ChatV2Dtos {
    private ChatV2Dtos() {
    }

    public record CreateConversationRequest(
            @Size(max = 160) String title,
            @Size(max = 26) String knowledgeBaseId) {
    }

    public record UpdateConversationRequest(
            @Size(max = 160) String title,
            @Size(max = 26) String knowledgeBaseId,
            Boolean clearKnowledgeBase) {
    }

    public record SendMessageRequest(
            @NotBlank @Size(max = 50000) String content,
            @Size(max = 20) List<@Size(max = 26) String> sourceAssetIds) {
        public SendMessageRequest {
            sourceAssetIds = sourceAssetIds == null ? List.of() : List.copyOf(sourceAssetIds);
        }
    }

    public record ConversationPage(List<ConversationSummary> items, String nextCursor, boolean hasMore) {
        public ConversationPage {
            items = items == null ? List.of() : List.copyOf(items);
        }
    }

    public record ConversationSummary(
            String id,
            String title,
            String type,
            String status,
            String knowledgeBaseId,
            String activeBranchId,
            long messageCount,
            long version,
            Instant lastMessageAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ConversationDetail(
            ConversationSummary conversation,
            List<MessageView> messages) {
        public ConversationDetail {
            messages = messages == null ? List.of() : List.copyOf(messages);
        }
    }

    public record MessageView(
            String id,
            String branchId,
            String parentMessageId,
            String role,
            String status,
            long sequence,
            String content,
            String runId,
            List<CitationView> citations,
            Instant createdAt,
            Instant finalizedAt) {
        public MessageView {
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }

    public record CitationView(
            int number,
            String assetId,
            String assetName,
            String assetVersionId,
            String chunkId,
            String quotedText,
            String locator,
            Double score) {
    }

    public record SendMessageAccepted(
            String userMessageId,
            String assistantMessageId,
            String runId,
            String eventUrl) {
    }

    public record AiRunView(
            String id,
            String conversationId,
            String branchId,
            String requestMessageId,
            String responseMessageId,
            String status,
            String stage,
            boolean cancellable,
            String errorCode,
            String safeErrorMessage,
            Instant createdAt,
            Instant startedAt,
            Instant completedAt) {

        public boolean terminal() {
            return List.of("SUCCEEDED", "FAILED", "CANCELLED").contains(status);
        }
    }
}
