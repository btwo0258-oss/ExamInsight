package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.entity.Document;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.mapper.DocumentMapper;
import com.example.llm.service.AsyncDocumentService;
import com.example.llm.service.DocumentService;
import com.example.llm.service.EsService;
import com.example.llm.service.KnowledgeBaseService;
import com.example.llm.vo.DocStatusVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import jakarta.servlet.http.HttpServletResponse;

@Service
public class DocumentServiceImpl extends ServiceImpl<DocumentMapper, Document> implements DocumentService {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @Value("${upload.path}")
    private String uploadPath;

    @Autowired
    private AsyncDocumentService asyncDocumentService;

    @Autowired
    private EsService esService;

    @Autowired
    private com.example.llm.mapper.DocumentChunkMapper documentChunkMapper;

    private static final List<String> ALLOWED_TYPES = Arrays.asList("pdf", "docx", "md", "txt");

    /**
     * 解析文件路径，支持相对路径和绝对路径
     */
    private File resolveFile(String filePath) {
        File file = new File(filePath);
        if (file.isAbsolute()) {
            return file;
        }
        // 相对路径，基于应用根目录解析
        String basePath = System.getProperty("user.dir");
        return new File(basePath, filePath);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Document uploadDocument(Long userId, Long kbId, MultipartFile file) {
        // 1. 校验知识库权限
        knowledgeBaseService.checkOwnership(userId, kbId);

        // 2. 校验文件
        if (file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            throw new IllegalArgumentException("文件名无效");
        }

        String ext = originalFilename.substring(originalFilename.lastIndexOf(".") + 1).toLowerCase();
        if (!ALLOWED_TYPES.contains(ext)) {
            throw new IllegalArgumentException("不支持的文件类型，仅支持: " + String.join(", ", ALLOWED_TYPES));
        }

        // 3. 保存文件到本地
        String fileName = UUID.randomUUID().toString().replace("-", "") + "." + ext;
        String kbPath = uploadPath + File.separator + "kb_" + kbId;
        File dir = new File(kbPath);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new RuntimeException("创建上传目录失败");
        }

        String fullPath = kbPath + File.separator + fileName;
        try {
            file.transferTo(new File(fullPath));
        } catch (IOException e) {
            throw new RuntimeException("文件保存失败", e);
        }

        // 4. 保存文档记录到数据库
        Document doc = new Document();
        doc.setKbId(kbId);
        doc.setUserId(userId);
        doc.setFileName(originalFilename);
        doc.setFileType(ext);
        doc.setFileSize(file.getSize());
        doc.setFilePath(fullPath);
        doc.setCharCount(0);
        doc.setChunkCount(0);
        doc.setStatus(0); // 0: 处理中
        doc.setCreateTime(java.time.LocalDateTime.now());
        doc.setUpdateTime(java.time.LocalDateTime.now());
        this.save(doc);

        // 5. 异步触发解析流程 (Apache Tika + 分块)
        asyncDocumentService.processDocument(doc);

        return doc;
    }

    @Override
    public List<Document> getDocumentList(Long userId, Long kbId) {
        knowledgeBaseService.checkOwnership(userId, kbId);
        return this.list(new LambdaQueryWrapper<Document>()
                .eq(Document::getKbId, kbId)
                .orderByDesc(Document::getCreateTime));
    }

    @Override
    public Document getDocumentDetail(Long userId, Long docId) {
        Document doc = this.getById(docId);
        if (doc == null) {
            throw new IllegalArgumentException("文档不存在");
        }
        // 通过知识库校验权限
        knowledgeBaseService.checkOwnership(userId, doc.getKbId());
        return doc;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteDocument(Long userId, Long docId) {
        Document doc = getDocumentDetail(userId, docId);
        
        // 1. 删除 ES 中的分块向量数据（容错处理，ES 可能未启动）
        try {
            esService.deleteByDocId("knowledge_chunks", docId);
        } catch (Exception e) {
            // ES 删除失败不影响整体删除流程，仅记录日志
            System.err.println("删除 ES 数据失败: " + e.getMessage());
        }
        
        // 2. 删除 MySQL 中的 DocumentChunk
        try {
            documentChunkMapper.delete(new LambdaQueryWrapper<com.example.llm.entity.DocumentChunk>().eq(com.example.llm.entity.DocumentChunk::getDocId, docId));
        } catch (Exception e) {
            System.err.println("删除 DocumentChunk 失败: " + e.getMessage());
        }
        
        // 3. 删除本地文件
        File file = resolveFile(doc.getFilePath());
        if (file.exists()) {
            file.delete();
        }

        // 4. 删除数据库记录
        this.removeById(docId);
        
        // 5. 同步更新知识库统计
        KnowledgeBase kb = knowledgeBaseService.getById(doc.getKbId());
        if (kb != null) {
            kb.setDocCount(Math.max(0, kb.getDocCount() - 1));
            kb.setChunkCount(Math.max(0, kb.getChunkCount() - doc.getChunkCount()));
            kb.setUpdateTime(java.time.LocalDateTime.now());
            knowledgeBaseService.updateById(kb);
        }
    }

    @Override
    public DocStatusVO getDocumentStatus(Long userId, Long docId) {
        Document doc = getDocumentDetail(userId, docId);
        DocStatusVO vo = new DocStatusVO();
        BeanUtils.copyProperties(doc, vo);
        return vo;
    }

    @Override
    public void downloadDocument(Long userId, Long docId, HttpServletResponse response) {
        Document doc = getDocumentDetail(userId, docId);
        File file = resolveFile(doc.getFilePath());
        
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在于服务器");
        }

        try (InputStream inputStream = new FileInputStream(file);
             OutputStream outputStream = response.getOutputStream()) {

            String contentType = "application/octet-stream";
            String ext = doc.getFileType().toLowerCase();
            if ("pdf".equals(ext)) {
                contentType = "application/pdf";
            } else if ("txt".equals(ext) || "md".equals(ext)) {
                contentType = "text/plain;charset=UTF-8";
            }
            
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "attachment; filename=\"" + URLEncoder.encode(doc.getFileName(), "UTF-8") + "\"");
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败", e);
        }
    }

    @Override
    public void previewDocument(Long userId, Long docId, HttpServletResponse response) {
        Document doc = getDocumentDetail(userId, docId);
        File file = resolveFile(doc.getFilePath());
        
        if (!file.exists()) {
            throw new IllegalArgumentException("文件不存在于服务器");
        }

        try (InputStream inputStream = new FileInputStream(file);
             OutputStream outputStream = response.getOutputStream()) {

            String contentType = "application/octet-stream";
            String ext = doc.getFileType().toLowerCase();
            if ("pdf".equals(ext)) {
                contentType = "application/pdf";
            } else if ("txt".equals(ext) || "md".equals(ext)) {
                contentType = "text/plain;charset=UTF-8";
            } else if ("docx".equals(ext)) {
                contentType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
            
            response.setContentType(contentType);
            response.setHeader("Content-Disposition", "inline; filename=\"" + URLEncoder.encode(doc.getFileName(), "UTF-8") + "\"");
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, len);
            }
            outputStream.flush();
        } catch (IOException e) {
            throw new RuntimeException("文件读取失败", e);
        }
    }

    @Override
    public List<Document> getUserDocuments(Long userId) {
        return this.list(new LambdaQueryWrapper<Document>()
                .eq(Document::getUserId, userId)
                .orderByDesc(Document::getCreateTime));
    }

    @Override
    public Long getOrCreateUncategorizedLibrary(Long userId) {
        // 查找用户是否已有"未分类"知识库
        KnowledgeBase uncategorized = knowledgeBaseService.getOne(
            new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId)
                .eq(KnowledgeBase::getName, "未分类")
                .eq(KnowledgeBase::getStatus, 0)
        );
        
        if (uncategorized != null) {
            return uncategorized.getId();
        }
        
        // 创建新的"未分类"知识库
        KnowledgeBase newKb = new KnowledgeBase();
        newKb.setUserId(userId);
        newKb.setName("未分类");
        newKb.setDescription("默认未分类知识库");
        newKb.setDocCount(0);
        newKb.setChunkCount(0);
        newKb.setMindMapCount(0);
        newKb.setStatus(0);
        newKb.setCreateTime(java.time.LocalDateTime.now());
        newKb.setUpdateTime(java.time.LocalDateTime.now());
        knowledgeBaseService.save(newKb);
        
        return newKb.getId();
    }

    @Override
    public void updateDocument(Document document) {
        document.setUpdateTime(java.time.LocalDateTime.now());
        this.updateById(document);
    }

    @Override
    public void retryDocument(Long userId, Long docId) {
        Document doc = getDocumentDetail(userId, docId);
        
        // 只有失败的文档才能重试
        if (doc.getStatus() != 4) {
            throw new IllegalArgumentException("只有解析失败的文档才能重试");
        }
        
        // 重置状态为处理中
        doc.setStatus(0);
        doc.setErrorMsg(null);
        doc.setUpdateTime(java.time.LocalDateTime.now());
        this.updateById(doc);
        
        // 重新触发解析流程
        asyncDocumentService.processDocument(doc);
    }
}