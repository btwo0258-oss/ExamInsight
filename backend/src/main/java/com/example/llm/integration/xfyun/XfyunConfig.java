package com.example.llm.integration.xfyun;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class XfyunConfig {

    @Value("${xfyun.app-id:}")
    private String appId;

    @Value("${xfyun.api-key:}")
    private String apiKey;

    @Value("${xfyun.api-secret:}")
    private String apiSecret;

    @Value("${xfyun.spark.api-password:}")
    private String sparkApiPassword;

    @Value("${xfyun.spark.url:https://spark-api-open.xf-yun.com/x2/chat/completions}")
    private String sparkUrl;

    @Value("${xfyun.image-generation.url:https://spark-api.cn-huabei-1.xf-yun.com/v2.1/tti}")
    private String imageGenerationUrl;

    @Value("${xfyun.image-understanding.url:wss://spark-api.cn-huabei-1.xf-yun.com/v2.1/image}")
    private String imageUnderstandingUrl;

    @Value("${xfyun.ocr.url:https://cbm01.cn-huabei-1.xf-yun.com/v1/private/se75ocrbm}")
    private String ocrUrl;

    @Value("${xfyun.speech.url:wss://iat.xf-yun.com/v1}")
    private String speechUrl;

    @Value("${xfyun.ppt.base-url:https://zwapi.xfyun.cn/api/ppt/v2}")
    private String pptBaseUrl;

    @Value("${xfyun.embedding.document-url:https://emb-cn-huabei-1.xf-yun.com/}")
    private String embeddingDocumentUrl;

    @Value("${xfyun.embedding.query-url:https://emb-cn-huabei-1.xf-yun.com/}")
    private String embeddingQueryUrl;

    @Value("${xfyun.embedding.dimensions:2048}")
    private int embeddingDimensions;

    public String getAppId() { return appId; }
    public String getApiKey() { return apiKey; }
    public String getApiSecret() { return apiSecret; }
    public String getSparkUrl() { return sparkUrl; }
    public String getImageGenerationUrl() { return imageGenerationUrl; }
    public String getImageUnderstandingUrl() { return imageUnderstandingUrl; }
    public String getOcrUrl() { return ocrUrl; }
    public String getSpeechUrl() { return speechUrl; }
    public String getPptBaseUrl() { return pptBaseUrl; }
    public String getEmbeddingDocumentUrl() { return embeddingDocumentUrl; }
    public String getEmbeddingQueryUrl() { return embeddingQueryUrl; }
    public int getEmbeddingDimensions() { return embeddingDimensions; }

    public String getSparkApiPassword() {
        if (sparkApiPassword != null && !sparkApiPassword.isBlank()) return sparkApiPassword.trim();
        requireApiCredentials();
        return apiKey.trim() + ":" + apiSecret.trim();
    }

    public void requireApiCredentials() {
        if (isBlank(appId) || isBlank(apiKey) || isBlank(apiSecret)) {
            throw new IllegalStateException("讯飞服务未配置，请设置 XFYUN_APP_ID、XFYUN_API_KEY 和 XFYUN_API_SECRET");
        }
    }

    public void requirePptCredentials() {
        if (isBlank(appId) || isBlank(apiSecret)) {
            throw new IllegalStateException("讯飞 PPT 服务未配置，请设置 XFYUN_APP_ID 和 XFYUN_API_SECRET");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
