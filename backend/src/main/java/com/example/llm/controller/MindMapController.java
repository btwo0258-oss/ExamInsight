package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.dto.MindMapCreateReq;
import com.example.llm.dto.MindMapGenerateReq;
import com.example.llm.dto.MindMapUpdateReq;
import com.example.llm.entity.MindMap;
import com.example.llm.service.MindMapService;
import com.example.llm.service.MindMapGenerateService;
import com.example.llm.vo.MindMapVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/mindmap")
public class MindMapController {

    @Autowired
    private MindMapService mindMapService;

    @Autowired
    private MindMapGenerateService mindMapGenerateService;

    @PostMapping("/create")
    public Result<Long> createMindMap(@RequestBody MindMapCreateReq req) {
        Long userId = UserContext.getUserId();
        Long id = mindMapService.createMindMap(req, userId);
        return Result.success(id);
    }

    @PostMapping("/update")
    public Result<Map<String, Object>> updateMindMap(@RequestBody MindMapUpdateReq req) {
        Long userId = UserContext.getUserId();
        mindMapService.updateMindMap(req, userId);
        MindMap updated = mindMapService.getMindMapDetail(req.getId(), userId);
        return Result.success(buildUpdateResult(updated));
    }

    @PostMapping("/delete/{id}")
    public Result<Void> deleteMindMap(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        mindMapService.deleteMindMap(id, userId);
        return Result.success(null);
    }

    @GetMapping("/list")
    public Result<List<MindMapVO>> listMindMaps(@RequestParam(required = false) Long kbId) {
        Long userId = UserContext.getUserId();
        List<MindMap> list = mindMapService.listMindMaps(kbId, userId);
        List<MindMapVO> voList = list.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }

    @GetMapping("/detail/{id}")
    public Result<MindMapVO> getMindMapDetail(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        MindMap detail = mindMapService.getMindMapDetail(id, userId);
        return Result.success(convertToVO(detail));
    }

    @PostMapping("/generate-from-ai")
    public Result<Map<String, Object>> generateFromAi(@RequestBody MindMapGenerateReq req) {
        Long userId = UserContext.getUserId();
        Map<String, Object> result = mindMapGenerateService.generateFromAiContent(req, userId);
        return Result.success(result);
    }

    private MindMapVO convertToVO(MindMap mindMap) {
        MindMapVO vo = new MindMapVO();
        BeanUtils.copyProperties(mindMap, vo);
        return vo;
    }

    private Map<String, Object> buildUpdateResult(MindMap mindMap) {
        Map<String, Object> result = new HashMap<>();
        result.put("id", mindMap.getId());
        result.put("resourceId", "mindmap-" + mindMap.getId());
        result.put("version", mindMap.getUpdateTime() != null
                ? mindMap.getUpdateTime().toString()
                : String.valueOf(System.currentTimeMillis()));
        result.put("updatedAt", mindMap.getUpdateTime() != null ? mindMap.getUpdateTime().toString() : "");
        result.put("previewData", buildPreviewData(mindMap));
        return result;
    }

    private Map<String, Object> buildPreviewData(MindMap mindMap) {
        Map<String, Object> previewData = new HashMap<>();
        previewData.put("kind", "mindmap");
        previewData.put("mindMap", parseMindMapTree(mindMap));
        Map<String, Object> config = new HashMap<>();
        config.put("theme", "classic");
        config.put("layout", "logicalStructure");
        previewData.put("mindMapConfig", config);
        return previewData;
    }

    private Map<String, Object> parseMindMapTree(MindMap mindMap) {
        try {
            String content = mindMap.getContent();
            if (content != null && !content.trim().isEmpty() && content.trim().startsWith("{")) {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> contentMap = mapper.readValue(content, Map.class);
                return normalizeTreeNode(contentMap, mindMap.getTitle());
            }
        } catch (Exception ignored) {
        }
        return fallbackTree(mindMap.getTitle());
    }

    private Map<String, Object> normalizeTreeNode(Map<String, Object> node, String defaultTitle) {
        Map<String, Object> normalized = new HashMap<>(node);
        Object dataObj = node.get("data");
        Map<String, Object> data = dataObj instanceof Map
                ? new HashMap<>((Map<String, Object>) dataObj)
                : new HashMap<>();
        if (!data.containsKey("text") || String.valueOf(data.get("text")).trim().isEmpty()) {
            data.put("text", defaultTitle != null && !defaultTitle.trim().isEmpty() ? defaultTitle : "思维导图");
        }
        normalized.put("data", data);

        List<Map<String, Object>> children = new ArrayList<>();
        Object childrenObj = node.get("children");
        if (childrenObj instanceof List) {
            for (Object child : (List<?>) childrenObj) {
                if (child instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> childMap = (Map<String, Object>) child;
                    children.add(normalizeTreeNode(childMap, null));
                }
            }
        }
        normalized.put("children", children);
        return normalized;
    }

    private Map<String, Object> fallbackTree(String title) {
        Map<String, Object> tree = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("text", title != null && !title.trim().isEmpty() ? title : "思维导图");
        tree.put("data", data);
        tree.put("children", new ArrayList<>());
        return tree;
    }
}
