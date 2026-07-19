package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.service.SystemConfigService;
import com.example.llm.integration.xfyun.XfyunConfig;
import com.example.llm.vo.ModelInfoVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/config")
public class SystemConfigController {

    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private XfyunConfig xfyunConfig;

    @Value("${dashscope.api-key:}")
    private String dashScopeApiKey;

    @Value("${dashscope.chat.model:qwen-plus-2025-07-28}")
    private String dashScopeChatModel;

    @GetMapping("/model")
    public Result<List<ModelInfoVO>> getModels() {
        List<ModelInfoVO> models = new ArrayList<>();
        if (dashScopeApiKey != null && !dashScopeApiKey.isBlank()) {
            models.add(new ModelInfoVO(
                    dashScopeChatModel,
                    "qwen-plus",
                    "Qwen Plus",
                    "通义千问通用对话模型",
                    true,
                    List.of("chat")
            ));
        }
        try {
            xfyunConfig.requireApiCredentials();
            models.add(new ModelInfoVO(
                    "spark-x2",
                    "spark-x2",
                    "讯飞星火 Spark X2",
                    "星火深度推理 X2，支持长上下文与动态思考",
                    true,
                    List.of("chat", "reasoning")
            ));
        } catch (IllegalStateException ignored) {
        }
        return Result.success(models);
    }

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
