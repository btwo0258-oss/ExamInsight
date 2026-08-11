package com.example.llm.integration.dashscope;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@ConfigurationProperties(prefix = "dashscope")
public class DashScopeProperties {
    private String apiKey = "";
    private String openaiBaseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1";
    private String nativeBaseUrl = "https://dashscope.aliyuncs.com/api/v1";
    private Duration connectTimeout = Duration.ofSeconds(15);
    private Duration requestTimeout = Duration.ofMinutes(2);
    private final Model chat = new Model("qwen3.7-plus");
    private final Model vision = new Model("qwen3.7-plus");
    private final Model ocr = new Model("qwen3.5-ocr");
    private final Model imageGeneration = new Model("qwen-image-3.0");
    private final Model speech = new Model("qwen3-asr-flash");

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank()
                && openaiBaseUrl != null && !openaiBaseUrl.isBlank()
                && nativeBaseUrl != null && !nativeBaseUrl.isBlank();
    }

    public String openaiEndpoint(String path) {
        return join(openaiBaseUrl, path);
    }

    public String nativeEndpoint(String path) {
        return join(nativeBaseUrl, path);
    }

    private String join(String base, String path) {
        String normalizedBase = base == null ? "" : base.trim().replaceAll("/+$", "");
        String normalizedPath = path == null ? "" : path.trim().replaceAll("^/+", "");
        return normalizedBase + "/" + normalizedPath;
    }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getOpenaiBaseUrl() { return openaiBaseUrl; }
    public void setOpenaiBaseUrl(String openaiBaseUrl) { this.openaiBaseUrl = openaiBaseUrl; }
    public String getNativeBaseUrl() { return nativeBaseUrl; }
    public void setNativeBaseUrl(String nativeBaseUrl) { this.nativeBaseUrl = nativeBaseUrl; }
    public Duration getConnectTimeout() { return connectTimeout; }
    public void setConnectTimeout(Duration connectTimeout) { this.connectTimeout = connectTimeout; }
    public Duration getRequestTimeout() { return requestTimeout; }
    public void setRequestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; }
    public Model getChat() { return chat; }
    public Model getVision() { return vision; }
    public Model getOcr() { return ocr; }
    public Model getImageGeneration() { return imageGeneration; }
    public Model getSpeech() { return speech; }

    public static class Model {
        private String model;

        public Model() { }
        public Model(String model) { this.model = model; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
    }
}
