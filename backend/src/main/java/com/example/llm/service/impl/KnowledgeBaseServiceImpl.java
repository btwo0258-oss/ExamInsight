package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.dto.KbCreateReq;
import com.example.llm.dto.KbUpdateReq;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.mapper.KnowledgeBaseMapper;
import com.example.llm.service.KnowledgeBaseService;
import com.example.llm.vo.KnowledgeBaseVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.llm.service.EsService;
import com.example.llm.mapper.DocumentMapper;
import com.example.llm.mapper.DocumentChunkMapper;
import com.example.llm.entity.Document;
import com.example.llm.entity.DocumentChunk;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseServiceImpl extends ServiceImpl<KnowledgeBaseMapper, KnowledgeBase> implements KnowledgeBaseService {

    private static final Pattern KNOWLEDGE_POINT_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:#{1,6}\\s+|第[一二三四五六七八九十百0-9]+[章节]\\s*|"
                    + "(?:[0-9]+(?:\\.[0-9]+)*|[一二三四五六七八九十百]+)[、.．)]\\s*|"
                    + "【?考点\\s*[0-9一二三四五六七八九十]+】?\\s*)([^\\n\\r]{2,80})$"
    );

    @Autowired
    private EsService esService;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private DocumentChunkMapper documentChunkMapper;

    private KnowledgeBaseVO convertToVO(KnowledgeBase kb) {
        KnowledgeBaseVO vo = new KnowledgeBaseVO();
        BeanUtils.copyProperties(kb, vo);
        vo.setKnowledgePoints(extractKnowledgePoints(kb));
        return vo;
    }

    private List<String> extractKnowledgePoints(KnowledgeBase kb) {
        Set<String> points = new LinkedHashSet<>();
        List<DocumentChunk> chunks = documentChunkMapper.selectList(
                new LambdaQueryWrapper<DocumentChunk>()
                        .eq(DocumentChunk::getKbId, kb.getId())
                        .eq(DocumentChunk::getEmbeddingStatus, 1)
                        .orderByAsc(DocumentChunk::getDocId)
                        .orderByAsc(DocumentChunk::getChunkIndex)
                        .last("LIMIT 40")
        );
        for (DocumentChunk chunk : chunks) {
            String content = chunk.getContent();
            if (content == null || content.isBlank()) continue;
            Matcher matcher = KNOWLEDGE_POINT_PATTERN.matcher(content);
            while (matcher.find() && points.size() < 8) {
                String value = matcher.group(1)
                        .replaceAll("\\[([^]]+)]\\([^)]*\\)", "$1")
                        .replaceAll("https?://\\S+", "")
                        .replaceAll("<[^>]+>", "")
                        .replaceAll("[`*_~]", "")
                        .replaceAll("^[\\-#>\\s]+|[\\s：:，,。；;]+$", "")
                        .trim();
                if (value.length() > 40) value = value.substring(0, 40).trim();
                if (value.length() >= 2 && !value.matches("^[0-9\\W]+$")) points.add(value);
            }
            if (points.size() >= 8) break;
        }
        if (points.isEmpty()) {
            List<Document> documents = documentMapper.selectList(
                    new LambdaQueryWrapper<Document>()
                            .eq(Document::getKbId, kb.getId())
                            .eq(Document::getStatus, 1)
                            .orderByDesc(Document::getUpdateTime)
                            .last("LIMIT 5")
            );
            for (Document document : documents) {
                String name = document.getFileName();
                if (name == null || name.isBlank()) continue;
                points.add(name.replaceFirst("\\.[^.]+$", ""));
            }
        }
        return new ArrayList<>(points);
    }

    @Override
    public KnowledgeBaseVO createKnowledgeBase(Long userId, KbCreateReq req) {
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
        return convertToVO(kb);
    }

    @Override
    public List<KnowledgeBaseVO> getKnowledgeBaseList(Long userId) {
        List<KnowledgeBase> list = this.list(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId)
                .eq(KnowledgeBase::getStatus, 0)
                .orderByDesc(KnowledgeBase::getUpdateTime));
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    @Override
    public KnowledgeBaseVO getKnowledgeBaseDetail(Long userId, Long kbId) {
        KnowledgeBase kb = this.getById(kbId);
        if (kb == null || kb.getStatus() == 1) {
            throw new IllegalArgumentException("知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权访问该知识库");
        }
        return convertToVO(kb);
    }

    @Override
    public KnowledgeBaseVO updateKnowledgeBase(Long userId, Long kbId, KbUpdateReq req) {
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
        return convertToVO(this.getById(kbId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteKnowledgeBase(Long userId, Long kbId) {
        KnowledgeBase kb = this.getById(kbId);
        if (kb == null || kb.getStatus() == 1) {
            throw new IllegalArgumentException("知识库不存在");
        }
        if (!kb.getUserId().equals(userId)) {
            throw new IllegalArgumentException("无权操作该知识库");
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
    public KnowledgeBaseVO getByExamAnalysisId(Long userId, Long examAnalysisId) {
        KnowledgeBase kb = this.getOne(new LambdaQueryWrapper<KnowledgeBase>()
                .eq(KnowledgeBase::getUserId, userId)
                .eq(KnowledgeBase::getExamAnalysisId, examAnalysisId)
                .eq(KnowledgeBase::getStatus, 0)
                .orderByDesc(KnowledgeBase::getUpdateTime)
                .last("LIMIT 1"));
        return kb != null ? convertToVO(kb) : null;
    }
}
