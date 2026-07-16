package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.dto.KbCreateReq;
import com.example.llm.dto.KbUpdateReq;
import com.example.llm.dto.KnowledgeBaseDto;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.mapper.KnowledgeBaseMapper;
import com.example.llm.service.KnowledgeBaseService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.llm.service.EsService;
import com.example.llm.mapper.DocumentMapper;
import com.example.llm.mapper.DocumentChunkMapper;
import com.example.llm.mapper.MindMapMapper;
import com.example.llm.entity.Document;
import com.example.llm.entity.DocumentChunk;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

    @Autowired
    private EsService esService;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentChunkMapper documentChunkMapper;

    @Autowired
    private MindMapMapper mindMapMapper;

    @Override
    public KnowledgeBaseDto createKnowledgeBase(Long userId, KbCreateReq req) {
        KnowledgeBase kb = new KnowledgeBase();
        kb.setUserId(userId);
        kb.setName(req.getName());
        kb.setDescription(req.getDescription());
        kb.setAvatar(req.getAvatar());
        kb.setColor(req.getColor());
        kb.setDocCount(0);
        kb.setChunkCount(0);
        kb.setMindMapCount(0);
        kb.setExamAnalysisId(req.getExamAnalysisId());
        kb.setStatus(0);
        kb.setCreateTime(java.time.LocalDateTime.now());
        kb.setUpdateTime(java.time.LocalDateTime.now());
        this.save(kb);
        return convertToDto(kb);
    }

    @Override
    public List<KnowledgeBaseDto> getKnowledgeBaseList(Long userId) {
        List<KnowledgeBase> kbList = this.list(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId)
                .eq(KnowledgeBase::getStatus, 0)
                .orderByDesc(KnowledgeBase::getUpdateTime));
        return kbList.stream().map(this::convertToDto).collect(Collectors.toList());
    }

    @Override
    public KnowledgeBaseDto getKnowledgeBaseDetail(Long userId, Long kbId) {
        KnowledgeBase kb = this.getById(kbId);
        if (kb == null || kb.getStatus() == 1) {
            throw new IllegalArgumentException("知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该知识库");
        }
        return convertToDto(kb);
    }

    @Override
    public KnowledgeBaseDto updateKnowledgeBase(Long userId, Long kbId, KbUpdateReq req) {
        if (req == null) {
            throw new IllegalArgumentException("请求参数不能为空");
        }
        // 校验权限并获取详情
        getKnowledgeBaseDetail(userId, kbId);
        
        KnowledgeBase updateKb = new KnowledgeBase();
        updateKb.setId(kbId);
        boolean hasUpdate = false;

        // 仅当传了 name 且不为空字符串时才更新
        if (org.springframework.util.StringUtils.hasText(req.getName())) {
            // 在业务层处理长度限制，如果传入了名称但过长则报错
            if (req.getName().length() > 100) {
                throw new IllegalArgumentException("知识库名称长度必须在1-100之间");
            }
            updateKb.setName(req.getName());
            hasUpdate = true;
        }
        // 仅当传了 description 且不为空时才更新，若什么都不填则保持不变
        if (org.springframework.util.StringUtils.hasText(req.getDescription())) {
            updateKb.setDescription(req.getDescription());
            hasUpdate = true;
        }
        // 更新 avatar 和 color
        if (req.getAvatar() != null) {
            updateKb.setAvatar(req.getAvatar());
            hasUpdate = true;
        }
        if (req.getColor() != null) {
            updateKb.setColor(req.getColor());
            hasUpdate = true;
        }
        
        if (hasUpdate) {
            updateKb.setUpdateTime(java.time.LocalDateTime.now());
            this.updateById(updateKb);
        }
        KnowledgeBase updated = this.getById(kbId);
        return convertToDto(updated);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(Long userId, Long kbId) {
        // 直接获取实体进行权限检查
        KnowledgeBase kb = this.getById(kbId);
        if (kb == null || kb.getStatus() == 1) {
            throw new IllegalArgumentException("知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该知识库");
        }
        
        // 1. 删除 MySQL 中的 DocumentChunk
        documentChunkMapper.delete(new LambdaQueryWrapper<DocumentChunk>().eq(DocumentChunk::getKbId, kbId));
        
        // 2. 删除 MySQL 中的 Document
        documentMapper.delete(new LambdaQueryWrapper<Document>().eq(Document::getKbId, kbId));
        
        // 3. 删除 ES 中的向量数据 (调用 ES 接口)
        esService.deleteByKbId("knowledge_chunks", kbId);
        
        // 4. 逻辑删除知识库 (使用 removeById 让 @TableLogic 自动处理)
        this.removeById(kbId);
    }

    @Override
    public void checkOwnership(Long userId, Long kbId) {
        KnowledgeBase kb = this.getById(kbId);
        if (kb == null || kb.getStatus() == 1) {
            throw new IllegalArgumentException("知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该知识库");
        }
    }

    @Override
    public KnowledgeBaseDto getByExamAnalysisId(Long userId, Long examAnalysisId) {
        KnowledgeBase kb = this.getOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId)
                .eq(KnowledgeBase::getExamAnalysisId, examAnalysisId)
                .eq(KnowledgeBase::getStatus, 0)
                .orderByDesc(KnowledgeBase::getUpdateTime)
                .last("LIMIT 1"));
        return kb != null ? convertToDto(kb) : null;
    }

    /**
     * 将KnowledgeBase实体转换为DTO
     * 不暴露userId和status字段，添加availableForAi字段
     * 动态计算docCount、chunkCount、mindMapCount以确保数据准确性
     */
    private KnowledgeBaseDto convertToDto(KnowledgeBase kb) {
        KnowledgeBaseDto dto = new KnowledgeBaseDto();
        dto.setId(kb.getId());
        dto.setName(kb.getName());
        dto.setDescription(kb.getDescription());
        dto.setAvatar(kb.getAvatar());
        dto.setColor(kb.getColor());
        dto.setExamAnalysisId(kb.getExamAnalysisId());
        dto.setCreateTime(kb.getCreateTime());
        dto.setUpdateTime(kb.getUpdateTime());

        // 动态计算文档数量
        Long docCount = documentMapper.selectCount(
            new LambdaQueryWrapper<Document>()
                .eq(Document::getKbId, kb.getId())
        );
        dto.setDocCount(docCount != null ? docCount.intValue() : 0);

        // 动态计算分块数量
        Long chunkCount = documentChunkMapper.selectCount(
            new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getKbId, kb.getId())
        );
        dto.setChunkCount(chunkCount != null ? chunkCount.intValue() : 0);

        // 动态计算思维导图数量
        Long mindMapCount = mindMapMapper.selectCount(
            new LambdaQueryWrapper<com.example.llm.entity.MindMap>()
                .eq(com.example.llm.entity.MindMap::getKbId, kb.getId())
        );
        dto.setMindMapCount(mindMapCount != null ? mindMapCount.intValue() : 0);

        // 计算availableForAi：至少有一个status=1的文档，且至少有一个成功向量化分块
        dto.setAvailableForAi(checkAvailableForAi(kb.getId()));

        return dto;
    }

    /**
     * 检查知识库是否可用于AI
     * 条件：至少有一个status=1的文档，且至少有一个成功向量化分块
     */
    private boolean checkAvailableForAi(Long kbId) {
        // 检查是否有status=1的文档
        long readyDocCount = documentMapper.selectCount(
            new LambdaQueryWrapper<Document>()
                .eq(Document::getKbId, kbId)
                .eq(Document::getStatus, 1)
        );
        
        if (readyDocCount == 0) {
            return false;
        }
        
        // 检查是否有成功向量化的分块
        long vectorizedChunkCount = documentChunkMapper.selectCount(
            new LambdaQueryWrapper<DocumentChunk>()
                .eq(DocumentChunk::getKbId, kbId)
                .eq(DocumentChunk::getEmbeddingStatus, 1) // 1表示向量化成功
        );
        
        return vectorizedChunkCount > 0;
    }
}