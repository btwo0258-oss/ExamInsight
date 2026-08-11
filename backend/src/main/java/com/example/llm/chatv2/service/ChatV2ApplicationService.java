package com.example.llm.chatv2.service;

import com.example.llm.chatv2.api.ChatV2ApiException;
import com.example.llm.chatv2.api.ChatV2Dtos.AiRunView;
import com.example.llm.chatv2.api.ChatV2Dtos.ConversationDetail;
import com.example.llm.chatv2.api.ChatV2Dtos.ConversationPage;
import com.example.llm.chatv2.api.ChatV2Dtos.ConversationSummary;
import com.example.llm.chatv2.api.ChatV2Dtos.CreateConversationRequest;
import com.example.llm.chatv2.api.ChatV2Dtos.SendMessageAccepted;
import com.example.llm.chatv2.api.ChatV2Dtos.SendMessageRequest;
import com.example.llm.chatv2.api.ChatV2Dtos.UpdateConversationRequest;
import com.example.llm.chatv2.repository.ChatV2Repository;
import com.example.llm.chatv2.repository.ChatV2Repository.PreparedRun;
import com.example.llm.chatv2.stream.AiRunEventBus;
import com.example.llm.common.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class ChatV2ApplicationService {
    private final ChatV2Repository repository;
    private final ChatRunExecutor runExecutor;
    private final AiRunEventBus eventBus;

    public ChatV2ApplicationService(
            ChatV2Repository repository,
            ChatRunExecutor runExecutor,
            AiRunEventBus eventBus) {
        this.repository = repository;
        this.runExecutor = runExecutor;
        this.eventBus = eventBus;
    }

    public ConversationSummary create(CreateConversationRequest request) {
        long userId = UserContext.requireSession().userId();
        return repository.createConversation(userId, request.title(), normalizeExternalId(request.knowledgeBaseId()));
    }

    public ConversationPage list(String cursor, int limit) {
        return repository.listConversations(UserContext.requireSession().userId(), normalizeCursor(cursor), limit);
    }

    public ConversationDetail get(String conversationId) {
        return repository.getConversation(UserContext.requireSession().userId(), requireExternalId(conversationId));
    }

    public ConversationSummary update(String conversationId, UpdateConversationRequest request) {
        if (Boolean.TRUE.equals(request.clearKnowledgeBase())
                && request.knowledgeBaseId() != null && !request.knowledgeBaseId().isBlank()) {
            throw new ChatV2ApiException(HttpStatus.BAD_REQUEST,
                    "INVALID_KNOWLEDGE_BASE_UPDATE", "不能同时清除并设置知识库。");
        }
        return repository.updateConversation(
                UserContext.requireSession().userId(), requireExternalId(conversationId), request.title(),
                normalizeExternalId(request.knowledgeBaseId()), Boolean.TRUE.equals(request.clearKnowledgeBase()));
    }

    public void trash(String conversationId) {
        repository.moveConversationToTrash(
                UserContext.requireSession().userId(), requireExternalId(conversationId));
    }

    public SendMessageAccepted send(
            String conversationId,
            SendMessageRequest request,
            String idempotencyKey) {
        long userId = UserContext.requireSession().userId();
        PreparedRun prepared = repository.prepareRun(
                userId, requireExternalId(conversationId), request.content(), request.sourceAssetIds(), idempotencyKey);
        if (!prepared.replayed()) {
            runExecutor.execute(prepared);
        }
        return new SendMessageAccepted(
                prepared.requestMessageExternalId(), prepared.responseMessageExternalId(),
                prepared.runExternalId(), eventUrl(prepared.runExternalId()));
    }

    public AiRunView getRun(String runId) {
        return repository.getRun(UserContext.requireSession().userId(), requireExternalId(runId));
    }

    public AiRunView cancel(String runId) {
        long userId = UserContext.requireSession().userId();
        String normalized = requireExternalId(runId);
        repository.requestCancellation(userId, normalized);
        return repository.getRun(userId, normalized);
    }

    public SseEmitter events(String runId, String lastEventId) {
        long userId = UserContext.requireSession().userId();
        String normalized = requireExternalId(runId);
        repository.getRun(userId, normalized);
        return eventBus.subscribe(normalized, lastEventId);
    }

    private String eventUrl(String runId) {
        return "/api/v2/ai-runs/" + runId + "/events";
    }

    private String normalizeExternalId(String value) {
        return value == null || value.isBlank() ? null : requireExternalId(value);
    }

    private String normalizeCursor(String value) {
        return value == null || value.isBlank() ? null : requireExternalId(value);
    }

    private String requireExternalId(String value) {
        if (value == null || !value.trim().matches("^[0-9A-HJKMNP-TV-Z]{26}$")) {
            throw new ChatV2ApiException(HttpStatus.BAD_REQUEST, "INVALID_EXTERNAL_ID", "资源标识格式不正确。");
        }
        return value.trim();
    }
}
