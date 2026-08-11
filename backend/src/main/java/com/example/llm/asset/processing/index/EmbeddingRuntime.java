package com.example.llm.asset.processing.index;

import com.example.llm.asset.processing.config.AssetProcessingProperties;
import com.example.llm.integration.dashscope.DashScopeEmbeddingClient;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class EmbeddingRuntime {
    private final AssetProcessingProperties properties;
    private final DashScopeEmbeddingClient dashScope;
    private final AtomicReference<String> suspensionCode = new AtomicReference<>();

    public EmbeddingRuntime(
            AssetProcessingProperties properties,
            DashScopeEmbeddingClient dashScope) {
        this.properties = properties;
        this.dashScope = dashScope;
    }

    public String provider() {
        String value = properties.getIndexing().getProvider();
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isSemanticIndexAvailable() {
        if (!properties.getIndexing().isEnabled() || suspensionCode.get() != null) return false;
        return "dashscope".equals(provider()) && dashScope.isConfigured();
    }

    public void suspend(String code) {
        if (code != null && !code.isBlank()) suspensionCode.compareAndSet(null, code);
    }

    public String suspensionCode() {
        return suspensionCode.get();
    }
}
