package com.example.llm.asset.retrieval;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@Getter
@Setter
@ConfigurationProperties("app.v2.retrieval")
public class AssetRetrievalProperties {
    private boolean enabled = true;

    @Min(1)
    @Max(20)
    private int defaultTopK = 6;

    @Min(1)
    @Max(20)
    private int maxTopK = 12;

    @Min(1)
    @Max(20)
    private int candidateMultiplier = 4;

    @DecimalMin("0.0")
    @DecimalMax("1.0")
    private double minSemanticScore = 0.55;

    @Min(2000)
    @Max(16000)
    private int defaultContextTokens = 3200;

    @Min(2000)
    @Max(16000)
    private int maxContextTokens = 8000;

    @Min(1)
    @Max(10)
    private int maxChunksPerAsset = 3;

    @Min(1)
    @Max(500)
    private int maxExplicitScopeItems = 20;

    @Min(1)
    @Max(65536)
    private int maxScopedAssets = 10000;

    @Min(1)
    @Max(5000)
    private int maxQueryCharacters = 2000;
}
