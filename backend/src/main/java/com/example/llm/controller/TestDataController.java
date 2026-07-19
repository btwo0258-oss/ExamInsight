package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.entity.Document;
import com.example.llm.entity.MindMap;
import com.example.llm.mapper.DocumentMapper;
import com.example.llm.mapper.MindMapMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class TestDataController {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private MindMapMapper mindMapMapper;

    @GetMapping("/data-count")
    public Result<Map<String, Object>> getDataCount() {
        Long userId = UserContext.getUserId();
        Map<String, Object> result = new HashMap<>();
        
        // 查询文档数量
        LambdaQueryWrapper<Document> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(Document::getUserId, userId);
        Long docCount = documentMapper.selectCount(docWrapper);
        result.put("documentCount", docCount);
        
        // 查询思维导图数量
        LambdaQueryWrapper<MindMap> mindMapWrapper = new LambdaQueryWrapper<>();
        mindMapWrapper.eq(MindMap::getUserId, userId);
        Long mindMapCount = mindMapMapper.selectCount(mindMapWrapper);
        result.put("mindMapCount", mindMapCount);
        
        // 查询文档列表（前5条）
        List<Document> documents = documentMapper.selectList(
            new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, userId)
                .last("LIMIT 5")
        );
        result.put("documents", documents);
        
        // 查询思维导图列表（前5条）
        List<MindMap> mindMaps = mindMapMapper.selectList(
            new LambdaQueryWrapper<MindMap>()
                .eq(MindMap::getUserId, userId)
                .last("LIMIT 5")
        );
        result.put("mindMaps", mindMaps);
        
        return Result.success(result);
    }
}
