package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.dto.MindMapCreateReq;
import com.example.llm.dto.MindMapUpdateReq;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.entity.MindMap;
import com.example.llm.mapper.KnowledgeBaseMapper;
import com.example.llm.mapper.MindMapMapper;
import com.example.llm.service.MindMapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MindMapServiceImpl extends ServiceImpl<MindMapMapper, MindMap> implements MindMapService {

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Override
    @Transactional
    public Long createMindMap(MindMapCreateReq req, Long userId) {
        MindMap mindMap = new MindMap();
        mindMap.setUserId(userId);
        mindMap.setTitle(req.getTitle() != null ? req.getTitle() : "未命名思维导图");
        mindMap.setKbId(req.getKbId());
        mindMap.setContent(req.getContent());
        mindMap.setCreateTime(LocalDateTime.now());
        mindMap.setUpdateTime(LocalDateTime.now());
        this.save(mindMap);

        if (req.getKbId() != null) {
            updateKbMindMapCount(req.getKbId(), 1);
        }

        return mindMap.getId();
    }

    @Override
    @Transactional
    public void updateMindMap(MindMapUpdateReq req, Long userId) {
        MindMap old = this.getById(req.getId());
        if (old == null || !old.getUserId().equals(userId)) {
            throw new RuntimeException("MindMap not found or permission denied");
        }

        Long oldKbId = old.getKbId();
        Long newKbId = req.getKbId();

        MindMap mindMap = new MindMap();
        mindMap.setId(req.getId());
        if (req.getTitle() != null) mindMap.setTitle(req.getTitle());
        mindMap.setKbId(req.getKbId());
        if (req.getContent() != null) mindMap.setContent(req.getContent());
        mindMap.setUpdateTime(LocalDateTime.now());
        this.updateById(mindMap);

        // Adjust counts if kbId changed
        if (oldKbId != null && !oldKbId.equals(newKbId)) {
            updateKbMindMapCount(oldKbId, -1);
        }
        if (newKbId != null && !newKbId.equals(oldKbId)) {
            updateKbMindMapCount(newKbId, 1);
        }
    }

    @Override
    @Transactional
    public void deleteMindMap(Long id, Long userId) {
        MindMap old = this.getById(id);
        if (old == null || !old.getUserId().equals(userId)) {
            throw new RuntimeException("MindMap not found or permission denied");
        }
        this.removeById(id);
        if (old.getKbId() != null) {
            updateKbMindMapCount(old.getKbId(), -1);
        }
    }

    @Override
    public List<MindMap> listMindMaps(Long kbId, Long userId) {
        LambdaQueryWrapper<MindMap> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(MindMap::getUserId, userId);
        if (kbId != null) {
            wrapper.eq(MindMap::getKbId, kbId);
        }
        wrapper.orderByDesc(MindMap::getUpdateTime);
        return this.list(wrapper);
    }

    @Override
    public MindMap getMindMapDetail(Long id, Long userId) {
        MindMap mindMap = this.getById(id);
        if (mindMap == null || !mindMap.getUserId().equals(userId)) {
            throw new RuntimeException("MindMap not found or permission denied");
        }
        return mindMap;
    }

    private void updateKbMindMapCount(Long kbId, int delta) {
        KnowledgeBase kb = knowledgeBaseMapper.selectById(kbId);
        if (kb != null) {
            int newCount = (kb.getMindMapCount() == null ? 0 : kb.getMindMapCount()) + delta;
            kb.setMindMapCount(Math.max(0, newCount));
            knowledgeBaseMapper.updateById(kb);
        }
    }
}
