package com.example.llm.chatv2.service;

import com.example.llm.chatv2.repository.ChatV2Repository;
import com.example.llm.integration.ai.AiCallResult;
import com.example.llm.integration.ai.AiCapabilityRouter;
import com.example.llm.integration.ai.AiChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/** Generates the first conversation title without extending the response run. */
@Slf4j
@Service
public class ConversationTitleService {
    private static final int MAX_INPUT_CHARACTERS = 1200;
    private static final int MAX_TITLE_CHARACTERS = 40;

    private final AiCapabilityRouter ai;
    private final ChatV2Repository repository;

    public ConversationTitleService(AiCapabilityRouter ai, ChatV2Repository repository) {
        this.ai = ai;
        this.repository = repository;
    }

    @Async("chatV2TaskExecutor")
    public void generateAsync(long userId, String conversationExternalId, String requestText) {
        try {
            String input = requestText == null ? "" : requestText.trim();
            if (input.isBlank()) {
                repository.markAutoTitleFallback(userId, conversationExternalId);
                return;
            }
            if (input.length() > MAX_INPUT_CHARACTERS) {
                input = input.substring(0, MAX_INPUT_CHARACTERS);
            }
            AiCallResult<String> result = ai.completeText(List.of(
                    new AiChatMessage("system", "请把用户问题概括成简短的中文对话标题。只返回标题，不要引号、编号、解释或标点；最多 16 个字。"),
                    new AiChatMessage("user", input)), userId);
            String title = normalize(result.value());
            if (title.isBlank()) {
                repository.markAutoTitleFallback(userId, conversationExternalId);
            } else {
                repository.applyAutoTitle(userId, conversationExternalId, title);
            }
        } catch (RuntimeException exception) {
            log.warn("V2 conversation title generation failed: conversationId={}, code={}",
                    conversationExternalId, exception.getClass().getSimpleName());
            repository.markAutoTitleFallback(userId, conversationExternalId);
        }
    }

    private String normalize(String value) {
        if (value == null) return "";
        String normalized = value.replaceAll("[\\r\\n]+", " ").trim();
        normalized = normalized.replace("\"", "")
                .replace("'", "")
                .replace("“", "")
                .replace("”", "")
                .replace("‘", "")
                .replace("’", "")
                .trim();
        if (normalized.length() > MAX_TITLE_CHARACTERS) {
            normalized = normalized.substring(0, MAX_TITLE_CHARACTERS);
        }
        return normalized.chars().anyMatch(Character::isISOControl) ? "" : normalized;
    }
}
