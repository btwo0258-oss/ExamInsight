package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.dto.MindMapCreateReq;
import com.example.llm.dto.MindMapUpdateReq;
import com.example.llm.entity.MindMap;

import java.util.List;

public interface MindMapService extends IService<MindMap> {
    Long createMindMap(MindMapCreateReq req, Long userId);
    void updateMindMap(MindMapUpdateReq req, Long userId);
    void deleteMindMap(Long id, Long userId);
    List<MindMap> listMindMaps(Long kbId, Long userId);
    MindMap getMindMapDetail(Long id, Long userId);
}
