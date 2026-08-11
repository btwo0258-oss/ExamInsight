package com.example.llm.asset.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AssetStorageProperties.class)
public class AssetStorageConfiguration {
}
