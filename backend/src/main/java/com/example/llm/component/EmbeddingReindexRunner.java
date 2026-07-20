package com.example.llm.component;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.entity.Document;
import com.example.llm.entity.DocumentChunk;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.mapper.DocumentChunkMapper;
import com.example.llm.mapper.DocumentMapper;
import com.example.llm.mapper.KnowledgeBaseMapper;
import com.example.llm.service.AsyncDocumentService;
import com.example.llm.service.EmbeddingService;
import com.example.llm.service.EsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Slf4j
@Component
@ConditionalOnProperty(name = "embedding.reindex-on-startup", havingValue = "true")
public class EmbeddingReindexRunner implements ApplicationRunner {

    private static final String INDEX_NAME = "knowledge_chunks";
    private static final Set<String> INDEXABLE_EXTENSIONS = Set.of("pdf", "doc", "docx", "txt", "md");

    private final EmbeddingService embeddingService;
    private final EsService esService;
    private final AsyncDocumentService asyncDocumentService;
    private final DocumentMapper documentMapper;
    private final DocumentChunkMapper documentChunkMapper;
    private final KnowledgeBaseMapper knowledgeBaseMapper;

    public EmbeddingReindexRunner(
            EmbeddingService embeddingService,
            EsService esService,
            AsyncDocumentService asyncDocumentService,
            DocumentMapper documentMapper,
            DocumentChunkMapper documentChunkMapper,
            KnowledgeBaseMapper knowledgeBaseMapper) {
        this.embeddingService = embeddingService;
        this.esService = esService;
        this.asyncDocumentService = asyncDocumentService;
        this.documentMapper = documentMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.knowledgeBaseMapper = knowledgeBaseMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("开始检查讯飞向量服务，成功后将重建知识库向量索引");
        try {
            List<Double> probe = embeddingService.getQueryEmbedding("向量服务连接测试");
            if (probe.size() != 2048) {
                log.error("讯飞向量服务返回维度不正确，跳过重建: {}", probe.size());
                return;
            }
        } catch (Exception e) {
            log.error("讯飞向量服务检查失败，保留现有索引，不执行重建: {}", e.getMessage());
            return;
        }

        List<Document> documents = documentMapper.selectList(new LambdaQueryWrapper<Document>()
                .isNotNull(Document::getKbId)
                .orderByAsc(Document::getId));
        List<Document> indexable = documents.stream()
                .filter(this::isIndexable)
                .toList();

        esService.deleteIndex(INDEX_NAME);
        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>()
                .isNotNull(DocumentChunk::getId));

        List<KnowledgeBase> knowledgeBases = knowledgeBaseMapper.selectList(null);
        for (KnowledgeBase knowledgeBase : knowledgeBases) {
            knowledgeBase.setDocCount(0);
            knowledgeBase.setChunkCount(0);
            knowledgeBase.setUpdateTime(LocalDateTime.now());
            knowledgeBaseMapper.updateById(knowledgeBase);
        }

        log.info("讯飞向量服务检查成功，开始顺序重建 {} 个知识库文档", indexable.size());
        for (Document document : indexable) {
            document.setStatus(0);
            document.setChunkCount(0);
            document.setErrorMsg(null);
            document.setUpdateTime(LocalDateTime.now());
            documentMapper.updateById(document);
            asyncDocumentService.processDocumentSynchronously(document);
        }
        log.info("知识库向量索引重建任务完成");
    }

    private boolean isIndexable(Document document) {
        String fileName = document.getFileName();
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot < 0 || !INDEXABLE_EXTENSIONS.contains(
                fileName.substring(dot + 1).toLowerCase(Locale.ROOT))) return false;
        String path = document.getFilePath();
        return path != null && new File(path).isFile();
    }
}
