package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.entity.Document;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.entity.MindMap;
import com.example.llm.mapper.DocumentChunkMapper;
import com.example.llm.mapper.DocumentMapper;
import com.example.llm.mapper.KnowledgeBaseMapper;
import com.example.llm.mapper.MindMapMapper;
import com.example.llm.service.AsyncDocumentService;
import com.example.llm.service.EsService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.entity.DocumentChunk;
import com.example.llm.vo.LibraryResourceVO;
import com.example.llm.vo.ResourcePreviewVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/resources")
public class ResourcesController {

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentChunkMapper documentChunkMapper;

    @Autowired
    private MindMapMapper mindMapMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private AsyncDocumentService asyncDocumentService;

    @Autowired
    private EsService esService;

    @Value("${upload.path}")
    private String uploadPath;

    private static final Set<String> SUPPORTED_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "csv", "ppt", "pptx",
            "txt", "md", "json", "java", "py", "js", "ts", "css", "html",
            "png", "jpg", "jpeg", "gif", "webp", "heic", "heif",
            "mp3", "wav", "m4a", "aac", "ogg", "flac",
            "zip", "rar", "7z"
    ));

    private static final Set<String> INDEXABLE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "pdf", "doc", "docx", "xls", "xlsx", "csv", "ppt", "pptx",
            "txt", "md", "json", "java", "py", "js", "ts", "css", "html"
    ));

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping
    public Result<List<LibraryResourceVO>> listResources(
            @RequestParam(value = "knowledgeBaseId", required = false) Long knowledgeBaseId) {
        Long userId = UserContext.getUserId();
        List<LibraryResourceVO> resources = new ArrayList<>();

        LambdaQueryWrapper<Document> docWrapper = new LambdaQueryWrapper<>();
        docWrapper.eq(Document::getUserId, userId);
        if (knowledgeBaseId != null) {
            docWrapper.eq(Document::getKbId, knowledgeBaseId);
        }
        documentMapper.selectList(docWrapper)
                .forEach(doc -> resources.add(buildResourceVO(doc, "resource-library")));

        LambdaQueryWrapper<MindMap> mindMapWrapper = new LambdaQueryWrapper<>();
        mindMapWrapper.eq(MindMap::getUserId, userId);
        if (knowledgeBaseId != null) {
            mindMapWrapper.eq(MindMap::getKbId, knowledgeBaseId);
        }
        mindMapMapper.selectList(mindMapWrapper)
                .forEach(mindMap -> resources.add(buildMindMapResourceVO(mindMap)));

        resources.sort(Comparator.comparing(
                LibraryResourceVO::getUpdatedAt,
                Comparator.nullsLast(String::compareTo)
        ).reversed());

        return Result.success(resources);
    }

    /**
     * 上传资源文件
     */
    @PostMapping("/upload")
    public Result<Map<String, Object>> upload(
            @RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam(value = "origin", defaultValue = "resource-library") String origin,
            @RequestParam(value = "knowledgeBaseId", required = false) Long knowledgeBaseId,
            @RequestParam(value = "projectId", required = false) Long projectId) {
        
        Long userId = UserContext.getUserId();
        
        try {
            String originalFilename = file.getOriginalFilename();
            if (originalFilename == null || originalFilename.trim().isEmpty()) {
                return Result.error(400, "文件名无效");
            }
            String ext = getExtension(originalFilename);
            
            // 校验文件类型
            if (ext.isEmpty() || !SUPPORTED_EXTENSIONS.contains(ext)) {
                return Result.error(400, "不支持的文件类型: ." + ext + "，仅支持: " + String.join(", ", SUPPORTED_EXTENSIONS));
            }

            if (knowledgeBaseId != null && !hasKnowledgeBaseAccess(userId, knowledgeBaseId)) {
                return Result.error(403, "无权访问该知识库");
            }

            boolean shouldIndex = knowledgeBaseId != null && INDEXABLE_EXTENSIONS.contains(ext);
            
            // 创建文档记录
            Document doc = new Document();
            doc.setUserId(userId);
            doc.setKbId(knowledgeBaseId);
            doc.setFileName(originalFilename);
            doc.setFileSize(file.getSize());
            doc.setFileType(ext);
            doc.setCharCount(0);
            doc.setChunkCount(0);
            doc.setStatus(shouldIndex ? 0 : 1);
            
            // 保存文件
            String fileId = UUID.randomUUID().toString().replace("-", "") + "." + ext;
            File uploadRoot = new File(uploadPath).getAbsoluteFile();
            File uploadDir = knowledgeBaseId != null
                    ? new File(uploadRoot, "kb_" + knowledgeBaseId)
                    : new File(uploadRoot, "library");
            if (!uploadDir.exists()) {
                uploadDir.mkdirs();
            }
            File destFile = new File(uploadDir, fileId).getAbsoluteFile();
            file.transferTo(destFile);
            doc.setFilePath(destFile.getAbsolutePath());
            doc.setCreateTime(java.time.LocalDateTime.now());
            doc.setUpdateTime(java.time.LocalDateTime.now());
            
            documentMapper.insert(doc);
            
            // 触发异步文档处理（异步处理会更新文档数量和知识片段数量）
            if (shouldIndex) {
                asyncDocumentService.processDocument(doc);
            }
            
            // 返回资源信息
            Map<String, Object> result = buildResourceMap(doc);
            result.put("origin", origin);
            result.put("projectId", projectId);
            
            return Result.success("上传成功", result);
        } catch (Exception e) {
            return Result.error(500, "上传失败: " + e.getMessage());
        }
    }

    /**
     * 删除资源
     */
    @DeleteMapping("/{resourceId}")
    public Result<Void> delete(@PathVariable String resourceId) {
        Long userId = UserContext.getUserId();
        
        try {
            if (resourceId.startsWith("doc-")) {
                Long docId = Long.parseLong(resourceId.substring(4));
                Document doc = documentMapper.selectById(docId);
                if (doc != null && doc.getUserId().equals(userId)) {
                    // 删除物理文件
                    if (doc.getFilePath() != null) {
                        File file = new File(doc.getFilePath());
                        if (file.exists()) {
                            file.delete();
                        }
                    }
                    // 更新知识库文档数量
                    Long kbId = doc.getKbId();
                    documentMapper.deleteById(docId);
                    if (kbId != null) {
                        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
                        if (kb != null) {
                            kb.setDocCount(Math.max(0, (kb.getDocCount() != null ? kb.getDocCount() : 1) - 1));
                            kb.setChunkCount(Math.max(0, (kb.getChunkCount() != null ? kb.getChunkCount() : 0) - (doc.getChunkCount() != null ? doc.getChunkCount() : 0)));
                            kb.setUpdateTime(java.time.LocalDateTime.now());
                            knowledgeBaseMapper.updateById(kb);
                        }
                    }
                }
            } else if (resourceId.startsWith("mindmap-")) {
                Long mindMapId = Long.parseLong(resourceId.substring(8));
                MindMap mindMap = mindMapMapper.selectById(mindMapId);
                if (mindMap != null && mindMap.getUserId().equals(userId)) {
                    mindMapMapper.deleteById(mindMapId);
                }
            }
            return Result.success("删除成功", null);
        } catch (Exception e) {
            return Result.error(500, "删除失败: " + e.getMessage());
        }
    }

    /**
     * 重试失败的资源
     */
    @PostMapping("/{resourceId}/retry")
    public Result<Map<String, Object>> retry(@PathVariable String resourceId) {
        Long userId = UserContext.getUserId();
        
        try {
            if (resourceId.startsWith("doc-")) {
                Long docId = Long.parseLong(resourceId.substring(4));
                Document doc = documentMapper.selectById(docId);
                if (doc != null && doc.getUserId().equals(userId)) {
                    // 清理旧的chunks数据（MySQL和ES）
                    documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getDocId, docId));
                    esService.deleteByDocId("knowledge_chunks", docId);
                    
                    // 如果文档之前已成功处理，需要回退知识库统计（processDocument会重新增加）
                    Integer prevStatus = doc.getStatus();
                    Integer prevChunkCount = doc.getChunkCount();
                    boolean wasReady = prevStatus != null
                            && (prevStatus == 1 || (prevStatus == 2
                            && (doc.getErrorMsg() == null || doc.getErrorMsg().trim().isEmpty())));
                    if (wasReady) {
                        // 文档之前是ready状态，说明docCount已经+1过，需要-1
                        Long kbId = doc.getKbId();
                        if (kbId != null) {
                            KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
                            if (kb != null) {
                                kb.setDocCount(Math.max(0, (kb.getDocCount() != null ? kb.getDocCount() : 0) - 1));
                                kb.setChunkCount(Math.max(0, (kb.getChunkCount() != null ? kb.getChunkCount() : 0) - (prevChunkCount != null ? prevChunkCount : 0)));
                                kb.setUpdateTime(java.time.LocalDateTime.now());
                                knowledgeBaseMapper.updateById(kb);
                            }
                        }
                    }
                    
                    boolean shouldIndex = doc.getKbId() != null
                            && INDEXABLE_EXTENSIONS.contains(getExtension(doc.getFileName()));

                    // 重置状态和计数
                    doc.setStatus(shouldIndex ? 0 : 1);
                    doc.setErrorMsg(null);
                    doc.setChunkCount(0);
                    doc.setUpdateTime(java.time.LocalDateTime.now());
                    documentMapper.updateById(doc);
                    
                    // 触发异步文档重新处理
                    if (shouldIndex) {
                        asyncDocumentService.processDocument(doc);
                    }
                    
                    Map<String, Object> result = buildResourceMap(doc);
                    return Result.success("重试中", result);
                }
            }
            return Result.error(404, "资源不存在");
        } catch (Exception e) {
            return Result.error(500, "重试失败: " + e.getMessage());
        }
    }

    /**
     * 重命名资源
     */
    @PatchMapping("/{resourceId}")
    public Result<Map<String, Object>> rename(
            @PathVariable String resourceId,
            @RequestBody Map<String, String> body) {
        Long userId = UserContext.getUserId();
        String newName = body.get("name");
        
        try {
            if (resourceId.startsWith("doc-")) {
                Long docId = Long.parseLong(resourceId.substring(4));
                Document doc = documentMapper.selectById(docId);
                if (doc != null && doc.getUserId().equals(userId)) {
                    doc.setFileName(newName);
                    documentMapper.updateById(doc);
                    
                    Map<String, Object> result = buildResourceMap(doc);
                    return Result.success("重命名成功", result);
                }
            } else if (resourceId.startsWith("mindmap-")) {
                Long mindMapId = Long.parseLong(resourceId.substring(8));
                MindMap mindMap = mindMapMapper.selectById(mindMapId);
                if (mindMap != null && mindMap.getUserId().equals(userId)) {
                    mindMap.setTitle(newName);
                    mindMapMapper.updateById(mindMap);
                    
                    Map<String, Object> result = buildMindMapResourceMap(mindMap);
                    return Result.success("重命名成功", result);
                }
            }
            return Result.error(404, "资源不存在");
        } catch (Exception e) {
            return Result.error(500, "重命名失败: " + e.getMessage());
        }
    }

    /**
     * 更新资源关联
     */
    @PutMapping("/{resourceId}/associations")
    public Result<Map<String, Object>> updateAssociations(
            @PathVariable String resourceId,
            @RequestBody Map<String, Long> body) {
        Long userId = UserContext.getUserId();
        Long knowledgeBaseId = body.get("knowledgeBaseId");
        Long projectId = body.get("projectId");
        
        try {
            if (resourceId.startsWith("doc-")) {
                Long docId = Long.parseLong(resourceId.substring(4));
                Document doc = documentMapper.selectById(docId);
                if (doc != null && doc.getUserId().equals(userId)) {
                    doc.setKbId(knowledgeBaseId);
                    documentMapper.updateById(doc);
                    
                    Map<String, Object> result = buildResourceMap(doc);
                    return Result.success("更新成功", result);
                }
            } else if (resourceId.startsWith("mindmap-")) {
                Long mindMapId = Long.parseLong(resourceId.substring(8));
                MindMap mindMap = mindMapMapper.selectById(mindMapId);
                if (mindMap != null && mindMap.getUserId().equals(userId)) {
                    mindMap.setKbId(knowledgeBaseId);
                    mindMapMapper.updateById(mindMap);
                    
                    Map<String, Object> result = buildMindMapResourceMap(mindMap);
                    return Result.success("更新成功", result);
                }
            }
            return Result.error(404, "资源不存在");
        } catch (Exception e) {
            return Result.error(500, "更新失败: " + e.getMessage());
        }
    }

    /**
     * 预览资源
     */
    @GetMapping("/{resourceId}/preview")
    public Result<ResourcePreviewVO> preview(@PathVariable String resourceId) {
        Long userId = UserContext.getUserId();
        
        try {
            if (resourceId.startsWith("doc-")) {
                Long docId = Long.parseLong(resourceId.substring(4));
                Document doc = documentMapper.selectById(docId);
                if (doc != null && doc.getUserId().equals(userId)) {
                    LibraryResourceVO resourceVO = buildResourceVO(doc, "resource-library");
                    ResourcePreviewVO vo = new ResourcePreviewVO();
                    vo.setResource(resourceVO);

                    String resourceStatus = resourceVO.getStatus();
                    String previewKind = determinePreviewKind(doc.getFileName(), doc.getFileType());
                    vo.setPreviewKind(previewKind);

                    if ("waiting".equals(resourceStatus) || "processing".equals(resourceStatus)) {
                        vo.setStatus("processing");
                        vo.setErrorMessage("文件仍在处理中，请稍后重试");
                        return Result.success(vo);
                    }
                    if ("failed".equals(resourceStatus)) {
                        vo.setStatus("failed");
                        vo.setErrorMessage(doc.getErrorMsg() != null ? doc.getErrorMsg() : "文件解析失败");
                        return Result.success(vo);
                    }
                    if ("unsupported".equals(previewKind)) {
                        vo.setStatus("unsupported");
                        vo.setErrorMessage("当前格式暂不支持在线预览，可下载后查看");
                        return Result.success(vo);
                    }
                    boolean generatedPresentation = "presentation".equals(previewKind)
                            && doc.getExternalKey() != null
                            && doc.getExternalKey().startsWith("presentation:");
                    if ("presentation".equals(previewKind) && !generatedPresentation) {
                        vo.setStatus("unsupported");
                        vo.setErrorMessage("上传的 PPT 需要服务端转换后才能在线预览，可先下载查看");
                        return Result.success(vo);
                    }
                    if ("word".equals(previewKind) && !doc.getFileName().toLowerCase().endsWith(".docx")) {
                        vo.setStatus("unsupported");
                        vo.setErrorMessage("旧版 Word 文档暂不支持在线预览，可下载后查看");
                        return Result.success(vo);
                    }
                    if (doc.getFileSize() != null && doc.getFileSize() > previewLimitBytes(previewKind)) {
                        vo.setStatus("too_large");
                        vo.setErrorMessage("文件超过该格式的在线预览大小限制");
                        return Result.success(vo);
                    }

                    vo.setStatus("ready");
                    if (generatedPresentation) {
                        vo.setPresentationId(doc.getExternalKey().substring("presentation:".length()));
                    }
                    if (doc.getFilePath() != null) {
                        String previewUrl = "/api/resources/" + resourceId + "/preview-file";
                        vo.setPreviewUrl(previewUrl);
                        
                        if ("text".equals(previewKind)) {
                            File file = new File(doc.getFilePath());
                            if (file.exists()) {
                                String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
                                vo.setTextContent(content);
                            }
                        }
                    }
                    
                    return Result.success(vo);
                }
            } else if (resourceId.startsWith("mindmap-")) {
                Long mindMapId = Long.parseLong(resourceId.substring(8));
                MindMap mindMap = mindMapMapper.selectById(mindMapId);
                if (mindMap != null && mindMap.getUserId().equals(userId)) {
                    LibraryResourceVO resourceVO = buildMindMapResourceVO(mindMap);
                    ResourcePreviewVO vo = new ResourcePreviewVO();
                    vo.setResource(resourceVO);
                    if (resourceVO.getSizeBytes() != null
                            && resourceVO.getSizeBytes() > previewLimitBytes("mindmap")) {
                        vo.setStatus("too_large");
                        vo.setPreviewKind("mindmap");
                        vo.setErrorMessage("思维导图超过在线预览大小限制");
                        return Result.success(vo);
                    }
                    vo.setStatus("ready");
                    vo.setPreviewKind("mindmap");
                    vo.setTextContent(mindMap.getContent());
                    vo.setMindMapId(mindMapId);
                    vo.setPreviewData(buildMindMapPreviewData(mindMap));
                    
                    return Result.success(vo);
                }
            }
            return Result.error(404, "资源不存在");
        } catch (Exception e) {
            return Result.error(500, "预览失败: " + e.getMessage());
        }
    }
    
    /**
     * 将思维导图内容转换为前端需要的树节点格式
     */
    private Map<String, Object> buildMindMapPreviewData(MindMap mindMap) {
        Map<String, Object> previewData = new HashMap<>();
        previewData.put("kind", "mindmap");

        try {
            String content = mindMap.getContent();
            if (content != null && !content.trim().isEmpty() && content.trim().startsWith("{")) {
                com.fasterxml.jackson.databind.ObjectMapper mapper =
                        new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> contentMap = mapper.readValue(content, Map.class);
                previewData.put("mindMap", convertToTreeNode(contentMap, mindMap.getTitle()));
            } else {
                previewData.put("mindMap", fallbackMindMapTree(mindMap.getTitle()));
            }
        } catch (Exception e) {
            previewData.put("mindMap", fallbackMindMapTree(mindMap.getTitle()));
        }

        Map<String, Object> config = new HashMap<>();
        config.put("theme", "classic");
        config.put("layout", "logicalStructure");
        previewData.put("mindMapConfig", config);
        return previewData;
    }

    private Map<String, Object> fallbackMindMapTree(String title) {
        Map<String, Object> tree = new HashMap<>();
        Map<String, Object> data = new HashMap<>();
        data.put("text", title != null && !title.trim().isEmpty() ? title : "思维导图");
        tree.put("data", data);
        tree.put("children", new ArrayList<>());
        return tree;
    }

    private Map<String, Object> convertToTreeNode(Map<String, Object> contentMap, String defaultTitle) {
        Map<String, Object> result = new HashMap<>(contentMap);

        if (contentMap.containsKey("data")) {
            Object dataObj = contentMap.get("data");
            Map<String, Object> data = dataObj instanceof Map
                    ? new HashMap<>((Map<String, Object>) dataObj)
                    : new HashMap<>();
            if (!data.containsKey("text") || String.valueOf(data.get("text")).trim().isEmpty()) {
                data.put("text", defaultTitle != null ? defaultTitle : "思维导图");
            }
            result.put("data", data);

            Object childrenObj = contentMap.get("children");
            java.util.List<Map<String, Object>> children = new java.util.ArrayList<>();
            if (childrenObj instanceof java.util.List) {
                for (Object child : (java.util.List<?>) childrenObj) {
                    if (child instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> childMap = (Map<String, Object>) child;
                        children.add(convertToTreeNode(childMap, null));
                    }
                }
            }
            result.put("children", children);
            return result;
        }

        // 尝试从常见格式中提取数据
        String text = defaultTitle != null ? defaultTitle : "思维导图";
        java.util.List<Map<String, Object>> children = new java.util.ArrayList<>();
        
        // 检查是否是 { title: "...", children: [...] } 格式
        if (contentMap.containsKey("title")) {
            text = String.valueOf(contentMap.get("title"));
        } else if (contentMap.containsKey("text")) {
            text = String.valueOf(contentMap.get("text"));
        } else if (contentMap.containsKey("name")) {
            text = String.valueOf(contentMap.get("name"));
        }
        
        // 处理子节点
        if (contentMap.containsKey("children")) {
            Object childrenObj = contentMap.get("children");
            if (childrenObj instanceof java.util.List) {
                java.util.List<?> childrenList = (java.util.List<?>) childrenObj;
                for (Object child : childrenList) {
                    if (child instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> childMap = (Map<String, Object>) child;
                        children.add(convertToTreeNode(childMap, null));
                    }
                }
            }
        }
        
        // 构建前端需要的格式
        Map<String, Object> data = new HashMap<>();
        data.put("text", text);
        result.put("data", data);
        result.put("children", children);
        
        return result;
    }
    
    /**
     * 根据文件类型确定预览类型
     */
    private String determinePreviewKind(String fileName, String mimeType) {
        if (fileName == null) return "unsupported";
        String lower = fileName.toLowerCase();
        
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                || lower.endsWith(".webp") || lower.endsWith(".heic") || lower.endsWith(".heif")) {
            return "image";
        }
        if (lower.endsWith(".pdf")) {
            return "pdf";
        }
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) {
            return "word";
        }
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) {
            return "presentation";
        }
        if (lower.endsWith(".csv")) {
            return "text";
        }
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) {
            return "spreadsheet";
        }
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a")
                || lower.endsWith(".aac") || lower.endsWith(".ogg") || lower.endsWith(".flac")) {
            return "audio";
        }
        if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".json")
                || lower.endsWith(".java") || lower.endsWith(".py") || lower.endsWith(".js")
                || lower.endsWith(".ts") || lower.endsWith(".css") || lower.endsWith(".html")) {
            return "text";
        }
        
        return "unsupported";
    }

    /**
     * 预览文件内容
     */
    @GetMapping("/{resourceId}/preview-file")
    public ResponseEntity<Resource> previewFile(@PathVariable String resourceId) throws IOException {
        Long userId = UserContext.getUserId();
        
        if (resourceId.startsWith("doc-")) {
            Long docId = Long.parseLong(resourceId.substring(4));
            Document doc = documentMapper.selectById(docId);
            if (doc != null && doc.getUserId().equals(userId) && doc.getFilePath() != null) {
                File file = new File(doc.getFilePath());
                if (!file.exists()) {
                    return ResponseEntity.notFound().build();
                }
                
                // 根据文件类型设置 Content-Type
                String contentType = "application/octet-stream";
                String fileName = doc.getFileName().toLowerCase();
                if (fileName.endsWith(".pdf")) {
                    contentType = "application/pdf";
                } else if (fileName.endsWith(".png")) {
                    contentType = "image/png";
                } else if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                } else if (fileName.endsWith(".gif")) {
                    contentType = "image/gif";
                } else if (fileName.endsWith(".webp")) {
                    contentType = "image/webp";
                } else if (fileName.endsWith(".mp3")) {
                    contentType = "audio/mpeg";
                } else if (fileName.endsWith(".wav")) {
                    contentType = "audio/wav";
                } else if (fileName.endsWith(".m4a")) {
                    contentType = "audio/mp4";
                } else if (fileName.endsWith(".ogg")) {
                    contentType = "audio/ogg";
                } else if (fileName.endsWith(".flac")) {
                    contentType = "audio/flac";
                } else if (fileName.endsWith(".txt") || fileName.endsWith(".md") || fileName.endsWith(".csv")) {
                    contentType = "text/plain;charset=UTF-8";
                } else if (fileName.endsWith(".json")) {
                    contentType = "application/json";
                } else if (fileName.endsWith(".xml")) {
                    contentType = "application/xml";
                }
                
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                        .body(new FileSystemResource(file));
            }
        }
        
        return ResponseEntity.notFound().build();
    }

    /**
     * 下载资源
     */
    @GetMapping("/{resourceId}/download")
    public ResponseEntity<Resource> download(@PathVariable String resourceId) throws IOException {
        Long userId = UserContext.getUserId();
        
        if (resourceId.startsWith("doc-")) {
            Long docId = Long.parseLong(resourceId.substring(4));
            Document doc = documentMapper.selectById(docId);
            if (doc != null && doc.getUserId().equals(userId) && doc.getFilePath() != null) {
                File file = new File(doc.getFilePath());
                if (!file.exists()) {
                    return ResponseEntity.notFound().build();
                }
                
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .contentLength(file.length())
                        .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(doc.getFileName()))
                        .body(new FileSystemResource(file));
            }
        } else if (resourceId.startsWith("mindmap-")) {
            Long mindMapId = Long.parseLong(resourceId.substring(8));
            MindMap mindMap = mindMapMapper.selectById(mindMapId);
            if (mindMap != null && mindMap.getUserId().equals(userId)) {
                // 思维导图以JSON格式下载
                String content = mindMap.getContent() != null ? mindMap.getContent() : "{}";
                String fileName = (mindMap.getTitle() != null ? mindMap.getTitle() : "mindmap") + ".json";
                
                byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .contentLength(bytes.length)
                        .header(HttpHeaders.CONTENT_DISPOSITION, attachmentHeader(fileName))
                        .body(new org.springframework.core.io.ByteArrayResource(bytes));
            }
        }
        
        return ResponseEntity.notFound().build();
    }

    // 辅助方法
    private String attachmentHeader(String fileName) {
        return ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();
    }

    private String getExtension(String fileName) {
        if (fileName == null) return "";
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot < 0 || lastDot == fileName.length() - 1) return "";
        return fileName.substring(lastDot + 1).toLowerCase();
    }

    private String getFormat(String fileName) {
        if (fileName == null) return "未知";
        int lastDot = fileName.lastIndexOf(".");
        if (lastDot > 0) {
            String ext = fileName.substring(lastDot + 1).toUpperCase();
            if (ext.equals("DOC") || ext.equals("DOCX")) return "Word";
            if (ext.equals("CSV")) return "CSV";
            if (ext.equals("XLS") || ext.equals("XLSX")) return "Excel";
            if (ext.equals("PPT") || ext.equals("PPTX")) return "PPT";
            if (ext.equals("PDF")) return "PDF";
            if (ext.equals("TXT")) return "TXT";
            if (ext.equals("MD")) return "Markdown";
            if (Arrays.asList("PNG", "JPG", "JPEG", "GIF", "WEBP", "HEIC", "HEIF").contains(ext)) return "图片";
            if (Arrays.asList("MP3", "WAV", "M4A", "AAC", "OGG", "FLAC").contains(ext)) return "音频";
            return ext;
        }
        return "未知";
    }

    private String getFileType(String fileName, String mimeType) {
        if (fileName == null) return "other";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".gif")
                || lower.endsWith(".webp") || lower.endsWith(".heic") || lower.endsWith(".heif")) return "image";
        if (lower.endsWith(".pdf")) return "pdf";
        if (lower.endsWith(".xls") || lower.endsWith(".xlsx") || lower.endsWith(".csv")) return "spreadsheet";
        if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "presentation";
        if (lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".m4a") || lower.endsWith(".aac")
                || lower.endsWith(".ogg") || lower.endsWith(".flac")) return "audio";
        if (lower.endsWith(".zip") || lower.endsWith(".rar") || lower.endsWith(".7z")) return "archive";
        if (lower.endsWith(".mindmap")) return "mindmap";
        if (lower.endsWith(".doc") || lower.endsWith(".docx") || lower.endsWith(".txt") || lower.endsWith(".md")
                || lower.endsWith(".json") || lower.endsWith(".java") || lower.endsWith(".py")
                || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".css")
                || lower.endsWith(".html")) return "document";
        return "other";
    }

    private long previewLimitBytes(String previewKind) {
        switch (previewKind) {
            case "text":
            case "mindmap":
                return 10L * 1024 * 1024;
            case "image":
                return 20L * 1024 * 1024;
            case "pdf":
            case "word":
            case "presentation":
            case "spreadsheet":
            case "audio":
                return 30L * 1024 * 1024;
            default:
                return 0L;
        }
    }

    private boolean hasKnowledgeBaseAccess(Long userId, Long knowledgeBaseId) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(knowledgeBaseId);
        return kb != null
                && kb.getStatus() != null
                && kb.getStatus() == 0
                && userId.equals(kb.getUserId());
    }

    private String formatUpdatedAt(java.time.LocalDateTime updateTime) {
        return updateTime != null ? updateTime.format(DATE_TIME_FORMATTER) : "";
    }

    private LibraryResourceVO buildResourceVO(Document doc, String origin) {
        LibraryResourceVO vo = new LibraryResourceVO();
        vo.setResourceId("doc-" + doc.getId());
        vo.setName(doc.getFileName());
        vo.setFormat(getFormat(doc.getFileName()));
        vo.setFileType(getFileType(doc.getFileName(), doc.getFileType()));
        vo.setMimeType(doc.getFileType());
        vo.setSizeBytes(doc.getFileSize());
        vo.setStatus(convertDocStatus(doc));
        vo.setErrorMessage(doc.getErrorMsg());
        vo.setUpdatedAt(formatUpdatedAt(doc.getUpdateTime()));
        vo.setSourceType("uploaded");
        vo.setOrigin(origin);
        vo.setProjectId(null);
        vo.setKnowledgeBaseId(doc.getKbId());
        vo.setExternalKey(doc.getExternalKey() != null && !doc.getExternalKey().isBlank()
                ? doc.getExternalKey()
                : "document:" + doc.getId());
        return vo;
    }

    private LibraryResourceVO buildMindMapResourceVO(MindMap mindMap) {
        LibraryResourceVO vo = new LibraryResourceVO();
        vo.setResourceId("mindmap-" + mindMap.getId());
        vo.setName(mindMap.getTitle() != null ? mindMap.getTitle() : "思维导图");
        vo.setFormat("思维导图");
        vo.setFileType("mindmap");
        vo.setMimeType("application/json");
        vo.setSizeBytes(mindMap.getContent() != null ? (long) mindMap.getContent().getBytes(StandardCharsets.UTF_8).length : 0L);
        vo.setStatus("ready");
        vo.setUpdatedAt(formatUpdatedAt(mindMap.getUpdateTime()));
        vo.setSourceType("generated");
        vo.setOrigin("mindmap");
        vo.setProjectId(null);
        vo.setKnowledgeBaseId(mindMap.getKbId());
        vo.setExternalKey("mindmap:" + mindMap.getId());
        return vo;
    }

    private Map<String, Object> buildResourceMap(Document doc) {
        return resourceVOToMap(buildResourceVO(doc, "resource-library"));
    }

    private Map<String, Object> buildMindMapResourceMap(MindMap mindMap) {
        return resourceVOToMap(buildMindMapResourceVO(mindMap));
    }

    private Map<String, Object> resourceVOToMap(LibraryResourceVO vo) {
        Map<String, Object> result = new HashMap<>();
        result.put("resourceId", vo.getResourceId());
        result.put("name", vo.getName());
        result.put("format", vo.getFormat());
        result.put("fileType", vo.getFileType());
        result.put("mimeType", vo.getMimeType());
        result.put("sizeBytes", vo.getSizeBytes());
        result.put("status", vo.getStatus());
        result.put("errorMessage", vo.getErrorMessage());
        result.put("updatedAt", vo.getUpdatedAt());
        result.put("sourceType", vo.getSourceType());
        result.put("origin", vo.getOrigin());
        result.put("projectId", vo.getProjectId());
        result.put("knowledgeBaseId", vo.getKnowledgeBaseId());
        result.put("externalKey", vo.getExternalKey());
        return result;
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
