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
import org.springframework.beans.factory.annotation.Value;
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
        try {
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
                    List<Double> embedding = embeddingService.getEmbedding(chunkText);
                    esService.saveChunk(INDEX_NAME, esId, doc.getKbId(), doc.getId(), i, chunkText, embedding);
                    chunk.setEmbeddingStatus(1);
                    successCount++;
                } catch (Exception e) {
                    log.error("Chunk {} embedding failed: {}", i, e.getMessage());
                    chunk.setEmbeddingStatus(2);
                }
                
                documentChunkMapper.insert(chunk);
            }

            doc.setStatus(1);
            doc.setUpdateTime(java.time.LocalDateTime.now());
            documentMapper.updateById(doc);

            KnowledgeBase kb = knowledgeBaseMapper.selectById(doc.getKbId());
            if (kb != null) {
                kb.setDocCount(kb.getDocCount() + 1);
                kb.setChunkCount(kb.getChunkCount() + successCount);
                kb.setUpdateTime(java.time.LocalDateTime.now());
                knowledgeBaseMapper.updateById(kb);
            }

        } catch (Exception e) {
            log.error("Document {} processing failed", doc.getId(), e);
            doc.setStatus(2);
            doc.setErrorMsg(e.getMessage());
            doc.setUpdateTime(java.time.LocalDateTime.now());
            documentMapper.updateById(doc);
        }
    }
}
