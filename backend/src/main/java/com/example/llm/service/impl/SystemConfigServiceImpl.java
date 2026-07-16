package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.dto.ModelInfoDto;
import com.example.llm.entity.SystemConfig;
import com.example.llm.mapper.SystemConfigMapper;
import com.example.llm.service.SystemConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SystemConfigServiceImpl implements SystemConfigService {

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    @Cacheable(value = "systemConfig", key = "#key", unless = "#result == null")
    public String getConfig(String key, String defaultValue) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigKey, key);
        SystemConfig config = systemConfigMapper.selectOne(wrapper);
        return config != null ? config.getConfigValue() : defaultValue;
    }

    @Override
    public int getIntConfig(String key, int defaultValue) {
        String val = getConfig(key, null);
        if (val == null) return defaultValue;
        try {
            return Integer.parseInt(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public double getDoubleConfig(String key, double defaultValue) {
        String val = getConfig(key, null);
        if (val == null) return defaultValue;
        try {
            return Double.parseDouble(val);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    @Override
    public Map<String, String> getAllConfigs() {
        List<SystemConfig> configs = systemConfigMapper.selectList(null);
        return configs.stream().collect(Collectors.toMap(SystemConfig::getConfigKey, SystemConfig::getConfigValue));
    }

    @Override
    @CacheEvict(value = "systemConfig", key = "#key")
    public void updateConfig(String key, String value) {
        LambdaQueryWrapper<SystemConfig> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SystemConfig::getConfigKey, key);
        SystemConfig config = systemConfigMapper.selectOne(wrapper);
        if (config != null) {
            config.setConfigValue(value);
            systemConfigMapper.updateById(config);
        } else {
            SystemConfig newConfig = new SystemConfig();
            newConfig.setConfigKey(key);
            newConfig.setConfigValue(value);
            systemConfigMapper.insert(newConfig);
        }
    }

    @Override
    public List<ModelInfoDto> getAvailableModels() {
        // 从 system_config 表中读取 models.available 配置（JSON 格式）
        String modelsJson = getConfig("models.available", null);
        if (modelsJson == null || modelsJson.isEmpty()) {
            // 返回默认模型列表
            return getDefaultModels();
        }
        
        try {
            return objectMapper.readValue(modelsJson, objectMapper.getTypeFactory()
                .constructCollectionType(List.class, ModelInfoDto.class));
        } catch (Exception e) {
            // JSON 解析失败，返回默认模型列表
            return getDefaultModels();
        }
    }

    private List<ModelInfoDto> getDefaultModels() {
        List<ModelInfoDto> models = new ArrayList<>();
        
        ModelInfoDto qwenPlus = new ModelInfoDto();
        qwenPlus.setName("qwen-plus");
        qwenPlus.setLabel("qwen-plus");
        qwenPlus.setDisplayName("通义千问-Plus");
        qwenPlus.setDescription("高性能通用大模型，支持多轮对话、文档解析等场景");
        qwenPlus.setEnabled(true);
        qwenPlus.setCapabilities(List.of("chat"));
        models.add(qwenPlus);
        
        return models;
    }
}
