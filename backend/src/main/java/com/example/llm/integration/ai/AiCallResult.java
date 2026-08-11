package com.example.llm.integration.ai;

import java.util.Map;

public record AiCallResult<T>(
        T value,
        String provider,
        String model,
        long durationMs,
        Map<String, Object> usage) {

    public AiCallResult {
        usage = usage == null ? Map.of() : Map.copyOf(usage);
    }

    public String routeKey() {
        return provider + ":" + model;
    }
}
