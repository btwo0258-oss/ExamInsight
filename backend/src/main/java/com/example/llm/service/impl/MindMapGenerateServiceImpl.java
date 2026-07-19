package com.example.llm.service.impl;

import com.alibaba.dashscope.aigc.generation.Generation;
import com.alibaba.dashscope.aigc.generation.GenerationParam;
import com.alibaba.dashscope.aigc.generation.GenerationResult;
import com.alibaba.dashscope.common.Message;
import com.alibaba.dashscope.common.Role;
import com.example.llm.dto.MindMapCreateReq;
import com.example.llm.dto.MindMapGenerateReq;
import com.example.llm.service.MindMapGenerateService;
import com.example.llm.service.MindMapService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class MindMapGenerateServiceImpl implements MindMapGenerateService {

    @Autowired
    private MindMapService mindMapService;

    @Value("${dashscope.api-key}")
    private String apiKey;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> generateFromAiContent(MindMapGenerateReq req, Long userId) {
        try {
            String prompt = buildMindMapPrompt(req.getContent());

            Generation gen = new Generation();
            GenerationParam param = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model("qwen-plus")
                    .messages(java.util.Collections.singletonList(
                            Message.builder().role(Role.USER.getValue()).content(prompt).build()))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .build();

            GenerationResult result = gen.call(param);
            String responseText = result.getOutput().getChoices().get(0).getMessage().getContent().trim();

            Map<String, Object> treeData = parseToTreeData(responseText, req.getTitle());

            String title = generateMindMapTitle(req.getContent(), req.getTitle());
            String contentJson = objectMapper.writeValueAsString(treeData);

            MindMapCreateReq createReq = new MindMapCreateReq();
            createReq.setTitle(title);
            createReq.setContent(contentJson);
            createReq.setKbId(req.getKbId());
            Long mindMapId = mindMapService.createMindMap(createReq, userId);

            Map<String, Object> resultMap = new HashMap<>();
            resultMap.put("id", mindMapId);
            resultMap.put("resourceId", "mindmap-" + mindMapId);
            resultMap.put("title", title);
            resultMap.put("treeData", treeData);
            return resultMap;

        } catch (Exception e) {
            log.error("Failed to generate mind map from AI content", e);
            throw new RuntimeException("生成思维导图失败：" + e.getMessage());
        }
    }

    private String buildMindMapPrompt(String aiContent) {
        return "请将以下内容整理为清晰的知识结构，用JSON格式返回，要求：\n" +
                "1. 以主题为根节点\n" +
                "2. 分层展示知识点\n" +
                "3. 层级不超过3层\n" +
                "4. 简洁清晰\n" +
                "5. 返回格式必须严格如下：\n" +
                "{\n" +
                "  \"data\": { \"text\": \"主题名称\" },\n" +
                "  \"children\": [\n" +
                "    {\n" +
                "      \"data\": { \"text\": \"一级知识点\" },\n" +
                "      \"children\": [\n" +
                "        { \"data\": { \"text\": \"二级知识点\" }, \"children\": [] }\n" +
                "      ]\n" +
                "    }\n" +
                "  ]\n" +
                "}\n\n" +
                "内容：\n" + aiContent;
    }

    private String generateMindMapTitle(String content, String fallbackTitle) {
        try {
            String truncatedContent = content.length() > 500 ? content.substring(0, 500) : content;
            String titlePrompt = "请根据以下内容，生成一个简短的知识图谱标题（不超过8个字），" +
                    "只返回标题内容，不要有任何标点符号和其他废话：" + truncatedContent;
            Generation titleGen = new Generation();
            GenerationParam titleParam = GenerationParam.builder()
                    .apiKey(apiKey)
                    .model("qwen-plus")
                    .messages(java.util.Collections.singletonList(
                            Message.builder().role(Role.USER.getValue()).content(titlePrompt).build()))
                    .resultFormat(GenerationParam.ResultFormat.MESSAGE)
                    .build();
            GenerationResult titleResult = titleGen.call(titleParam);
            String generatedTitle = titleResult.getOutput().getChoices().get(0).getMessage().getContent().trim();
            if (!generatedTitle.isEmpty()) {
                return generatedTitle;
            }
        } catch (Exception e) {
            log.warn("Failed to generate mind map title by AI, using fallback", e);
        }
        if (fallbackTitle != null && !fallbackTitle.isEmpty()) {
            return fallbackTitle.length() > 10 ? fallbackTitle.substring(0, 10) : fallbackTitle;
        }
        return "知识图谱";
    }

    private Map<String, Object> parseToTreeData(String responseText, String title) {
        try {
            String jsonStr = responseText;
            if (jsonStr.contains("```json")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```json") + 7);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            } else if (jsonStr.contains("```")) {
                jsonStr = jsonStr.substring(jsonStr.indexOf("```") + 3);
                jsonStr = jsonStr.substring(0, jsonStr.indexOf("```"));
            }
            jsonStr = jsonStr.trim();

            com.fasterxml.jackson.databind.JsonNode root = objectMapper.readTree(jsonStr);

            if (root.has("data") && root.get("data").has("text")) {
                return objectMapper.convertValue(root, Map.class);
            }

            return convertFlatJsonToTree(root, title);
        } catch (Exception e) {
            log.warn("Failed to parse mind map JSON, creating simple structure", e);
            Map<String, Object> treeData = new HashMap<>();
            Map<String, String> rootData = new HashMap<>();
            rootData.put("text", title != null ? title : "知识图谱");
            treeData.put("data", rootData);
            treeData.put("children", java.util.Collections.emptyList());
            return treeData;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> convertFlatJsonToTree(com.fasterxml.jackson.databind.JsonNode root, String title) {
        Map<String, Object> treeData = new HashMap<>();
        Map<String, String> rootData = new HashMap<>();
        rootData.put("text", title != null ? title : "知识图谱");
        treeData.put("data", rootData);

        java.util.List<Map<String, Object>> children = new java.util.ArrayList<>();
        if (root.isObject()) {
            var fields = root.fields();
            while (fields.hasNext()) {
                var entry = fields.next();
                Map<String, Object> child = new HashMap<>();
                Map<String, String> childData = new HashMap<>();
                childData.put("text", entry.getKey());
                child.put("data", childData);

                java.util.List<Map<String, Object>> subChildren = new java.util.ArrayList<>();
                com.fasterxml.jackson.databind.JsonNode value = entry.getValue();
                if (value.isObject()) {
                    var subFields = value.fields();
                    while (subFields.hasNext()) {
                        var subEntry = subFields.next();
                        Map<String, Object> subChild = new HashMap<>();
                        Map<String, String> subData = new HashMap<>();
                        subData.put("text", subEntry.getKey());
                        subChild.put("data", subData);
                        subChild.put("children", java.util.Collections.emptyList());
                        subChildren.add(subChild);
                    }
                } else if (value.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode item : value) {
                        Map<String, Object> subChild = new HashMap<>();
                        Map<String, String> subData = new HashMap<>();
                        subData.put("text", item.isValueNode() ? item.asText() : item.toString());
                        subChild.put("data", subData);
                        subChild.put("children", java.util.Collections.emptyList());
                        subChildren.add(subChild);
                    }
                } else if (value.isValueNode()) {
                    Map<String, Object> subChild = new HashMap<>();
                    Map<String, String> subData = new HashMap<>();
                    subData.put("text", value.asText());
                    subChild.put("data", subData);
                    subChild.put("children", java.util.Collections.emptyList());
                    subChildren.add(subChild);
                }

                child.put("children", subChildren);
                children.add(child);
            }
        }

        treeData.put("children", children);
        return treeData;
    }
}
