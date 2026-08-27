package com.example.llm.chatv2.agent;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bounds the complete first model request.  The estimator is intentionally
 * conservative; provider usage is recorded separately and can calibrate these
 * application limits without pretending that a character count is exact.
 */
@Component
public class ContextBudget {
    private final ContextBudgetProperties properties;

    public ContextBudget(ContextBudgetProperties properties) {
        this.properties = properties;
    }

    public Selection select(List<Message> messages, int toolCount) {
        if (messages == null || messages.isEmpty()) {
            throw new ContextBudgetExceededException("没有可发送给模型的上下文。");
        }
        int budget = Math.max(1_000, properties.getMaxInputTokens());
        int toolTokens = Math.max(0, toolCount)
                * Math.max(0, properties.getToolSchemaTokensPerTool());
        Message system = messages.get(0);
        int used = estimate(system) + toolTokens;
        if (used >= budget) {
            throw new ContextBudgetExceededException("系统提示词和工具定义已超过当前上下文预算。");
        }

        List<Message> history = messages.subList(1, messages.size());
        List<Message> selectedReversed = new ArrayList<>();
        int omitted = 0;
        int index = history.size() - 1;
        while (index >= 0) {
            int start = index;
            // Keep an assistant reply with the user turn that produced it.
            if (history.get(index) instanceof AssistantMessage && index > 0
                    && history.get(index - 1) instanceof UserMessage) {
                start = index - 1;
            }
            int cost = 0;
            for (int i = start; i <= index; i++) cost += estimate(history.get(i));
            if (used + cost > budget) {
                // The newest user question must never be silently truncated.
                if (selectedReversed.isEmpty()) {
                    throw new ContextBudgetExceededException("当前问题超过可用上下文预算，请拆分后重试。");
                }
                omitted += index + 1;
                break;
            }
            for (int i = index; i >= start; i--) selectedReversed.add(history.get(i));
            used += cost;
            index = start - 1;
        }
        Collections.reverse(selectedReversed);
        List<Message> result = new ArrayList<>(selectedReversed.size() + 1);
        result.add(system);
        result.addAll(selectedReversed);
        return new Selection(List.copyOf(result), used, omitted);
    }

    private int estimate(Message message) {
        String text = message == null || message.getText() == null ? "" : message.getText();
        int divisor = Math.max(1, properties.getEstimatedTokensPerCharacter());
        return Math.max(1, (text.length() + divisor - 1) / divisor + 4);
    }

    public record Selection(List<Message> messages, int estimatedInputTokens, int omittedMessages) {
    }

    public static class ContextBudgetExceededException extends RuntimeException {
        public ContextBudgetExceededException(String message) {
            super(message);
        }
    }
}
