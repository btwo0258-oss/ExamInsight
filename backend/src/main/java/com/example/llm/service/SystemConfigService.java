package com.example.llm.service;

import com.example.llm.dto.ModelInfoDto;
import java.util.List;
import java.util.Map;

public interface SystemConfigService {
    String getConfig(String key, String defaultValue);
    int getIntConfig(String key, int defaultValue);
    double getDoubleConfig(String key, double defaultValue);
    Map<String, String> getAllConfigs();
    void updateConfig(String key, String value);
    List<ModelInfoDto> getAvailableModels();
}
