package com.example.llm.service.impl;

import com.example.llm.component.DocumentParser;
import com.example.llm.component.TextSplitter;
import com.example.llm.entity.Document;
import com.example.llm.entity.DocumentChunk;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.mapper.DocumentChunkMapper;
import com.example.llm.mapper.DocumentMapper;
import com.example.llm.mapper.KnowledgeBaseMapper;
import com.example.llm.service.AsyncDocumentService;
import com.example.llm.service.EmbeddingService;
import com.example.llm.service.EsService;
import com.example.llm.service.SystemConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
public class AsyncDocumentServiceImpl implements AsyncDocumentService {

    @Autowired
    private DocumentParser documentParser;
    @Autowired
    private TextSplitter textSplitter;
    @Autowired
    private EmbeddingService embeddingService;
    @Autowired
    private EsService esService;
    @Autowired
    private DocumentMapper documentMapper;
    @Autowired
    private DocumentChunkMapper documentChunkMapper;
    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private SystemConfigService systemConfigService;

    private static final String INDEX_NAME = "knowledge_chunks";

    @Async
    @Override
    public void processDocument(Document doc) {
        processDocumentSynchronously(doc);
    }

    @Override
    public void processDocumentSynchronously(Document doc) {
        // 在处理前捕获原始状态，用于后续正确更新知识库统计
        Integer previousStatus = doc.getStatus();
        Integer previousChunkCount = doc.getChunkCount();

        try {
            // 先设置状态为处理中（1），避免前端一直显示 waiting
            doc.setStatus(1);
            doc.setUpdateTime(java.time.LocalDateTime.now());
            documentMapper.updateById(doc);

            esService.createIndexIfNotExists(INDEX_NAME);

            File file = new File(doc.getFilePath());
            String text = documentParser.parse(file);
            doc.setCharCount(text.length());
            documentMapper.updateById(doc);

            int chunkSize = systemConfigService.getIntConfig("rag.chunk_size", 500);
            int chunkOverlap = systemConfigService.getIntConfig("rag.chunk_overlap", 100);

            List<String> chunks = textSplitter.splitText(text, chunkSize, chunkOverlap);
            doc.setChunkCount(chunks.size());
            documentMapper.updateById(doc);

            int successCount = 0;
            for (int i = 0; i < chunks.size(); i++) {
                String chunkText = chunks.get(i);
                DocumentChunk chunk = new DocumentChunk();
                chunk.setDocId(doc.getId());
                chunk.setKbId(doc.getKbId());
                chunk.setChunkIndex(i);
                chunk.setContent(chunkText);
                chunk.setCharCount(chunkText.length());
                chunk.setTokenCount(chunkText.length());
                chunk.setCreateTime(java.time.LocalDateTime.now());
                
                String esId = UUID.randomUUID().toString();
                chunk.setEsId(esId);
                
                try {
                    List<Double> embedding = embeddingService.getDocumentEmbedding(chunkText);
                    esService.saveChunk(INDEX_NAME, esId, doc.getKbId(), doc.getId(), i, chunkText, embedding);
                    chunk.setEmbeddingStatus(1);
                    successCount++;
                } catch (Exception e) {
                    log.error("Chunk {} embedding failed: {}", i, e.getMessage());
                    chunk.setEmbeddingStatus(2);
                }
                
                documentChunkMapper.insert(chunk);
            }

            doc.setStatus(1); // 1 = ready
            doc.setUpdateTime(java.time.LocalDateTime.now());
            documentMapper.updateById(doc);

            KnowledgeBase kb = knowledgeBaseMapper.selectById(doc.getKbId());
            if (kb != null) {
                // 只有首次成功处理时才增加文档计数
                if (previousStatus == null || previousStatus != 1) {
                    kb.setDocCount((kb.getDocCount() != null ? kb.getDocCount() : 0) + 1);
                }
                // 更新知识片段数量：用新的 successCount 减去旧的 previousChunkCount
                int oldChunks = previousChunkCount != null ? previousChunkCount : 0;
                int chunkDiff = successCount - oldChunks;
                kb.setChunkCount((kb.getChunkCount() != null ? kb.getChunkCount() : 0) + chunkDiff);
                kb.setUpdateTime(java.time.LocalDateTime.now());
                knowledgeBaseMapper.updateById(kb);
            }

        } catch (Exception e) {
            log.error("Document {} processing failed", doc.getId(), e);
            doc.setStatus(2); // 2 = failed
            doc.setErrorMsg(e.getMessage());
            doc.setUpdateTime(java.time.LocalDateTime.now());
            documentMapper.updateById(doc);
        }
    }
}
