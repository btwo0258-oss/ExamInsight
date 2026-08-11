package com.example.llm.asset.processing.config;

import com.example.llm.asset.retrieval.AssetRetrievalProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties({AssetProcessingProperties.class, AssetRetrievalProperties.class})
public class AssetProcessingConfiguration {
}
