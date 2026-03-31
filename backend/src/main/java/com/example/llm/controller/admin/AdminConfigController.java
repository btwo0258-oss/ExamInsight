package com.example.llm.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.llm.common.Result;
import com.example.llm.entity.SystemConfig;
import com.example.llm.mapper.SystemConfigMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/configs")
public class AdminConfigController {

    @Autowired
    private SystemConfigMapper systemConfigMapper;

    @GetMapping
    public Result<List<SystemConfig>> getAllConfigs() {
        return Result.success(systemConfigMapper.selectList(null));
    }

    @PutMapping("/{key}")
    public Result<Map<String, Boolean>> updateConfig(@PathVariable String key, @RequestBody Map<String, String> payload) {
        QueryWrapper<SystemConfig> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("config_key", key);
        SystemConfig config = systemConfigMapper.selectOne(queryWrapper);
        if (config != null) {
            config.setConfigValue(payload.get("value"));
            systemConfigMapper.updateById(config);
        } else {
            config = new SystemConfig();
            config.setConfigKey(key);
            config.setConfigValue(payload.get("value"));
            systemConfigMapper.insert(config);
        }
        return Result.success(Map.of("success", true));
    }
}