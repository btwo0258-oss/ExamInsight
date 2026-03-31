package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/config")
public class SystemConfigController {

    @Autowired
    private SystemConfigService systemConfigService;

    @GetMapping("/all")
    public Result<Map<String, String>> getAll() {
        return Result.success(systemConfigService.getAllConfigs());
    }

    @PostMapping("/update")
    public Result<Void> updateConfig(@RequestParam("key") String key, @RequestParam("value") String value) {
        systemConfigService.updateConfig(key, value);
        return Result.success("更新配置成功", null);
    }
}
