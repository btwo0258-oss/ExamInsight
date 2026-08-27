package com.example.llm.chatv2.api;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.List;

public final class ChatV2Dtos {
    private ChatV2Dtos() {
    }

    public record CreateConversationRequest(
            @Pattern(regexp = "[0-7][0-9A-HJKMNP-TV-Z]{25}") String conversationId,
            @Size(max = 160) String title,
            @Size(max = 26) String knowledgeBaseId) {
    }

    public record UpdateConversationRequest(
            @Size(max = 160) String title,
            @Size(max = 26) String knowledgeBaseId,
            Boolean clearKnowledgeBase,
            Boolean pinned) {
    }

    public record SendMessageRequest(
            @Size(max = 50000) String content,
            @Size(max = 20) List<@Size(max = 26) String> sourceAssetIds) {
        public SendMessageRequest {
            content = content == null ? "" : content;
            sourceAssetIds = sourceAssetIds == null ? List.of() : List.copyOf(sourceAssetIds);
        }
    }

    public record EditMessageRequest(@Size(max = 50000) String content) {
        public EditMessageRequest {
            content = content == null ? "" : content;
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
            String titleSource,
            String type,
            String status,
            String knowledgeBaseId,
            String activeBranchId,
            long messageCount,
            long version,
            Instant pinnedAt,
            Instant lastMessageAt,
            Instant createdAt,
            Instant updatedAt) {
    }

    public record ConversationDetail(
            ConversationSummary conversation,
            List<MessageView> messages,
            List<MessageVersionGroup> versionGroups) {
        public ConversationDetail {
            messages = messages == null ? List.of() : List.copyOf(messages);
            versionGroups = versionGroups == null ? List.of() : List.copyOf(versionGroups);
        }
    }

    /**
     * A bounded page of messages for the active branch.  The segment list is
     * deliberately lightweight so the client can navigate to an unloaded
     * question without downloading the full conversation body.
     */
    public record ConversationMessagesPage(
            ConversationSummary conversation,
            List<MessageView> messages,
            List<MessageVersionGroup> versionGroups,
            List<MessageSegment> segments,
            String nextCursor,
            boolean hasMore) {
        public ConversationMessagesPage {
            messages = messages == null ? List.of() : List.copyOf(messages);
            versionGroups = versionGroups == null ? List.of() : List.copyOf(versionGroups);
            segments = segments == null ? List.of() : List.copyOf(segments);
        }
    }

    public record MessageSegment(String id, long sequence, String preview) {
    }

    public record MessageView(
            String id,
            String branchId,
            String versionGroupId,
            String parentMessageId,
            String role,
            String status,
            long sequence,
            String content,
            String runId,
            List<MessageAttachmentView> attachments,
            List<CitationView> citations,
            Instant createdAt,
            Instant finalizedAt) {
        public MessageView {
            attachments = attachments == null ? List.of() : List.copyOf(attachments);
            citations = citations == null ? List.of() : List.copyOf(citations);
        }
    }

    public record MessageVersionGroup(
            String id,
            String role,
            List<MessageVersionView> versions) {
        public MessageVersionGroup {
            versions = versions == null ? List.of() : List.copyOf(versions);
        }
    }

    public record MessageVersionView(
            String messageId,
            String branchId,
            Instant createdAt) {
    }

    public record MessageAttachmentView(
            String assetId,
            String assetVersionId,
            String name,
            String mimeType,
            long sizeBytes,
            String assetType) {
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
