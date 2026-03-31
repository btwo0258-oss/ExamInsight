package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.dto.KbCreateReq;
import com.example.llm.dto.KbUpdateReq;
import com.example.llm.entity.KnowledgeBase;

import java.util.List;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {
    
    KnowledgeBase createKnowledgeBase(Long userId, KbCreateReq req);
    List<KnowledgeBase> getKnowledgeBaseList(Long userId);
    KnowledgeBase getKnowledgeBaseDetail(Long userId, Long kbId);
    KnowledgeBase updateKnowledgeBase(Long userId, Long kbId, KbUpdateReq req);
    void deleteKnowledgeBase(Long userId, Long kbId);

    void checkOwnership(Long userId, Long kbId);
}