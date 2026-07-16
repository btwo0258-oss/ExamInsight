package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.entity.Document;
import com.example.llm.service.DocumentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/library/resources")
public class LibraryResourceController {

    @Autowired
    private DocumentService documentService;

    /**
     * 获取资料资源列表
     */
    @GetMapping
    public Result<List<Map<String, Object>>> list(@RequestParam(value = "libraryId", required = false) Long libraryId) {
        Long userId = UserContext.getUserId();
        List<Document> documents;
        
        if (libraryId != null) {
            documents = documentService.getDocumentList(userId, libraryId);
        } else {
            // 返回当前用户的所有文档
            documents = documentService.getUserDocuments(userId);
        }
        
        List<Map<String, Object>> resources = documents.stream()
            .map(this::convertToResource)
            .collect(Collectors.toList());
        
        return Result.success(resources);
    }

    /**
     * 上传资料资源
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "libraryId", required = false) Long libraryId,
            @RequestParam(value = "projectId", required = false) Long projectId) {
        Long userId = UserContext.getUserId();
        
        // 如果没有指定 libraryId，创建一个默认的"未分类"知识库
        if (libraryId == null) {
            libraryId = documentService.getOrCreateUncategorizedLibrary(userId);
        }
        
        Document document = documentService.uploadDocument(userId, libraryId, file);
        return Result.success("上传成功", convertToResource(document));
    }

    /**
     * 重命名资料资源
     */
    @PatchMapping("/{id}")
    public Result<Map<String, Object>> rename(
            @PathVariable("id") Long id,
            @RequestBody Map<String, String> request) {
        Long userId = UserContext.getUserId();
        String name = request.get("name");
        
        Document document = documentService.getDocumentDetail(userId, id);
        document.setFileName(name);
        documentService.updateDocument(document);
        
        return Result.success("重命名成功", convertToResource(document));
    }

    /**
     * 移动资料资源到指定知识库
     */
    @PostMapping("/{id}/move")
    public Result<Map<String, Object>> move(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Long> request) {
        Long userId = UserContext.getUserId();
        Long libraryId = request.get("libraryId");
        
        Document document = documentService.getDocumentDetail(userId, id);
        document.setKbId(libraryId);
        documentService.updateDocument(document);
        
        return Result.success("移动成功", convertToResource(document));
    }

    /**
     * 重试解析失败的资料资源
     */
    @PostMapping("/{id}/retry")
    public Result<Map<String, Object>> retry(@PathVariable("id") Long id) {
        Long userId = UserContext.getUserId();
        
        Document document = documentService.getDocumentDetail(userId, id);
        documentService.retryDocument(userId, id);
        
        // 重新获取更新后的文档
        document = documentService.getDocumentDetail(userId, id);
        return Result.success("重试成功", convertToResource(document));
    }

    /**
     * 预览资料资源
     */
    @GetMapping("/{id}/preview")
    public void preview(@PathVariable("id") Long id, HttpServletResponse response) {
        Long userId = UserContext.getUserId();
        documentService.previewDocument(userId, id, response);
    }

    /**
     * 下载资料资源
     */
    @GetMapping("/{id}/download")
    public void download(@PathVariable("id") Long id, HttpServletResponse response) {
        Long userId = UserContext.getUserId();
        documentService.downloadDocument(userId, id, response);
    }

    /**
     * 删除资料资源
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        Long userId = UserContext.getUserId();
        documentService.deleteDocument(userId, id);
        return Result.success("删除成功", null);
    }

    /**
     * 将 Document 转换为前端需要的资源格式
     */
    private Map<String, Object> convertToResource(Document doc) {
        Map<String, Object> resource = new HashMap<>();
        // 使用聚合ID格式：document:123
        resource.put("id", "document:" + doc.getId());
        resource.put("name", doc.getFileName());
        resource.put("type", doc.getFileType() != null ? doc.getFileType().toUpperCase() : "FILE");
        resource.put("size", formatFileSize(doc.getFileSize()));
        resource.put("status", convertStatus(doc.getStatus()));
        resource.put("updatedAt", doc.getUpdateTime() != null ? doc.getUpdateTime().toString() : doc.getCreateTime().toString());
        resource.put("category", determineCategory(doc.getFileType()));
        resource.put("source", "资料库上传");
        resource.put("projectId", null);
        resource.put("libraryId", doc.getKbId());
        
        if (doc.getStatus() != null && doc.getStatus() == 3) { // failed
            resource.put("errorMessage", doc.getErrorMsg());
        }
        
        return resource;
    }

    /**
     * 转换文档状态
     * 后端状态：0-处理中, 1-就绪, 2-失败
     * 前端状态：processing, ready, failed
     */
    private String convertStatus(Integer status) {
        if (status == null) return "processing";
        switch (status) {
            case 0: return "processing";   // 处理中
            case 1: return "ready";        // 就绪
            case 2: return "failed";       // 失败
            default: return "processing";
        }
    }

    /**
     * 根据文件类型确定分类
     */
    private String determineCategory(String fileType) {
        if (fileType == null) return "file";
        String lowerType = fileType.toLowerCase();
        if (lowerType.contains("image") || lowerType.equals("png") || lowerType.equals("jpg") || 
            lowerType.equals("jpeg") || lowerType.equals("gif") || lowerType.equals("bmp")) {
            return "image";
        }
        return "file";
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(Long size) {
        if (size == null) return "0 B";
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024));
        return String.format("%.1f GB", size / (1024.0 * 1024 * 1024));
    }
}
