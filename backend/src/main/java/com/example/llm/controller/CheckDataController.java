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
@RequestMapping("/api/check")
public class CheckDataController {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private MindMapMapper mindMapMapper;

    @GetMapping("/data")
    public Result<Map<String, Object>> checkData() {
        Long userId = UserContext.getUserId();
        Map<String, Object> result = new HashMap<>();
        
        // 查询文档
        LambdaQueryWrapper<Document> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(Document::getUserId, userId);
        List<Document> documents = documentMapper.selectList(docWrapper);
        result.put("documents", documents);
        result.put("documentCount", documents.size());
        
        // 查询思维导图
        LambdaQueryWrapper<MindMap> mindMapWrapper = new LambdaQueryWrapper<>();
        mindMapWrapper.eq(MindMap::getUserId, userId);
        List<MindMap> mindMaps = mindMapMapper.selectList(mindMapWrapper);
        result.put("mindMaps", mindMaps);
        result.put("mindMapCount", mindMaps.size());
        
        return Result.success(result);
    }
}
