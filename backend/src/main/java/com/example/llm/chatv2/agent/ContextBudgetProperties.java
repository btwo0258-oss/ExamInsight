package com.example.llm.chatv2.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Application-side safety limits for the model input assembled by V2 chat. */
@Component
@ConfigurationProperties(prefix = "chat-v2.context")
public class ContextBudgetProperties {
    /** Input budget after reserving space for the model's answer and a safety margin. */
    private int maxInputTokens = 26_000;
    private int toolSchemaTokensPerTool = 450;
    private int estimatedTokensPerCharacter = 2;

    public int getMaxInputTokens() {
        return maxInputTokens;
    }

    public void setMaxInputTokens(int maxInputTokens) {
        this.maxInputTokens = maxInputTokens;
    }

    public int getToolSchemaTokensPerTool() {
        return toolSchemaTokensPerTool;
    }

    public void setToolSchemaTokensPerTool(int toolSchemaTokensPerTool) {
        this.toolSchemaTokensPerTool = toolSchemaTokensPerTool;
    }

    public int getEstimatedTokensPerCharacter() {
        return estimatedTokensPerCharacter;
    }

    public void setEstimatedTokensPerCharacter(int estimatedTokensPerCharacter) {
        this.estimatedTokensPerCharacter = estimatedTokensPerCharacter;
    }
}
