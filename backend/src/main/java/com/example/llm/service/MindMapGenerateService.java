package com.example.llm.service;

import com.example.llm.dto.MindMapGenerateReq;

import java.util.Map;

public interface MindMapGenerateService {
    Map<String, Object> generateFromAiContent(MindMapGenerateReq req, Long userId);
}
