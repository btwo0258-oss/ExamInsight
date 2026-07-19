package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.entity.Document;
import com.example.llm.entity.MindMap;
import com.example.llm.mapper.DocumentMapper;
import com.example.llm.mapper.MindMapMapper;
import com.example.llm.vo.LibraryResourceVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/library")
public class LibraryController {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private MindMapMapper mindMapMapper;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/resources")
    public Result<List<LibraryResourceVO>> listResources(@RequestParam(required = false) Long knowledgeBaseId) {
        Long userId = UserContext.getUserId();
        List<LibraryResourceVO> resources = new ArrayList<>();

        // 查询文档
        LambdaQueryWrapper<Document> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(Document::getUserId, userId);
        if (knowledgeBaseId != null) {
            docWrapper.eq(Document::getKbId, knowledgeBaseId);
        }
        List<Document> documents = documentMapper.selectList(docWrapper);
        
        for (Document doc : documents) {
            LibraryResourceVO vo = new LibraryResourceVO();
            vo.setResourceId("doc-" + doc.getId());
            vo.setName(doc.getFileName());
            vo.setFormat(getFormat(doc.getFileName()));
            vo.setFileType(getFileType(doc.getFileName(), doc.getFileType()));
            vo.setMimeType(doc.getFileType());
            vo.setSizeBytes(doc.getFileSize());
            vo.setStatus(convertDocStatus(doc));
            vo.setErrorMessage(doc.getErrorMsg());
            vo.setUpdatedAt(doc.getUpdateTime() != null ? doc.getUpdateTime().format(FORMATTER) : "");
            vo.setSourceType("uploaded");
            vo.setOrigin("resource-library");
            vo.setProjectId(null);
            vo.setKnowledgeBaseId(doc.getKbId());
            vo.setExternalKey(doc.getExternalKey() != null && !doc.getExternalKey().isBlank()
                    ? doc.getExternalKey()
                    : "document:" + doc.getId());
            resources.add(vo);
        }

        // 查询思维导图
        LambdaQueryWrapper<MindMap> mindMapWrapper = new LambdaQueryWrapper<>();
        mindMapWrapper.eq(MindMap::getUserId, userId);
        if (knowledgeBaseId != null) {
            mindMapWrapper.eq(MindMap::getKbId, knowledgeBaseId);
        }
        List<MindMap> mindMaps = mindMapMapper.selectList(mindMapWrapper);
        
        for (MindMap mindMap : mindMaps) {
            LibraryResourceVO vo = new LibraryResourceVO();
            vo.setResourceId("mindmap-" + mindMap.getId());
            vo.setName(mindMap.getTitle() != null ? mindMap.getTitle() : "思维导图");
            vo.setFormat("思维导图");
            vo.setFileType("mindmap");
            vo.setMimeType("application/json");
            vo.setSizeBytes(mindMap.getContent() != null ? (long) mindMap.getContent().length() : 0L);
            vo.setStatus("ready");
            vo.setUpdatedAt(mindMap.getUpdateTime() != null ? mindMap.getUpdateTime().format(FORMATTER) : "");
            vo.setSourceType("generated");
            vo.setOrigin("mindmap");
            vo.setProjectId(null);
            vo.setKnowledgeBaseId(mindMap.getKbId());
            vo.setExternalKey("mindmap:" + mindMap.getId());
            resources.add(vo);
        }

        return Result.success(resources);
    }

    private String getFormat(String fileName) {
        if (fileName == null) return "FILE";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "PDF";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "Word";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "Excel";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "PPT";
        if (lower.endsWith(".txt")) return "TXT";
        if (lower.endsWith(".md")) return "Markdown";
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "图片";
        return "FILE";
    }

    private String getFileType(String fileName, String mimeType) {
        if (fileName == null) return "document";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")) {
            return "image";
        }
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv")) return "spreadsheet";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "presentation";
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) return "archive";
        if (lower.endsWith(".mindmap")) return "mindmap";
        return "document";
    }

    private String convertDocStatus(Document doc) {
        Integer status = doc.getStatus();
        if (status == null) return "waiting";
        switch (status) {
            case 0: return "processing";
            case 1: return "ready";
            case 2:
                return doc.getErrorMsg() != null && !doc.getErrorMsg().trim().isEmpty()
                        ? "failed"
                        : "ready";
            case 3: return "failed";
            default: return "waiting";
        }
    }
}
