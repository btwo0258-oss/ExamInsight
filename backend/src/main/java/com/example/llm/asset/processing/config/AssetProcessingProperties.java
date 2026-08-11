package com.example.llm.asset.processing.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@Getter
@Setter
@ConfigurationProperties("app.v2.processing")
public class AssetProcessingProperties {
    private boolean enabled = true;

    @NotNull
    private Duration pollInterval = Duration.ofSeconds(2);

    @NotNull
    private Duration leaseDuration = Duration.ofMinutes(10);

    @Min(1)
    @Max(20)
    private int batchSize = 3;

    @Valid
    @NotNull
    private Scanner scanner = new Scanner();

    @Valid
    @NotNull
    private Parser parser = new Parser();

    @Valid
    @NotNull
    private Indexing indexing = new Indexing();

    @Getter
    @Setter
    public static class Scanner {
        @NotBlank
        private String mode = "clamav";

        @NotBlank
        private String host = "localhost";

        @Min(1)
        @Max(65535)
        private int port = 3310;

        @NotNull
        private Duration connectTimeout = Duration.ofSeconds(5);

        @NotNull
        private Duration readTimeout = Duration.ofMinutes(2);

        @Min(4096)
        @Max(1024 * 1024)
        private int streamChunkSize = 64 * 1024;
    }

    @Getter
    @Setter
    public static class Parser {
        @NotBlank
        private String key = "tika-ocr";

        @NotBlank
        private String version = "2026-08-10.2";

        @Min(1_000)
        private int maxCharacters = 5_000_000;

        @Min(100)
        @Max(1800)
        private int targetTokens = 800;

        @Min(0)
        @Max(500)
        private int overlapTokens = 100;

        @Min(100)
        @Max(2000)
        private int maxTokens = 1800;
    }

    @Getter
    @Setter
    public static class Indexing {
        private boolean enabled = true;

        @NotBlank
        private String provider = "dashscope";

        @NotBlank
        private String providerModel = "qwen3.7-text-embedding";

        @NotBlank
        private String modelKey = "dashscope-qwen3.7-text-embedding-1024";

        @NotBlank
        private String embeddingVersion = "dashscope-qwen3.7-text-embedding-1024-v1";

        @NotBlank
        @jakarta.validation.constraints.Pattern(regexp = "[a-z0-9][a-z0-9._-]+")
        private String indexName = "examinsight-v2-chunks-qwen3.7-embedding-1024-v1";

        @Min(128)
        @Max(4096)
        private int dimensions = 1024;

        @Min(1)
        @Max(1000)
        private int reconcileBatchSize = 200;
    }
}
