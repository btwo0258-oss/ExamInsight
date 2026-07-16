package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.dto.KbCreateReq;
import com.example.llm.dto.KbUpdateReq;
import com.example.llm.dto.KnowledgeBaseDto;
import com.example.llm.entity.KnowledgeBase;

import java.util.List;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {
    
    KnowledgeBaseDto createKnowledgeBase(Long userId, KbCreateReq req);
    List<KnowledgeBaseDto> getKnowledgeBaseList(Long userId);
    KnowledgeBaseDto getKnowledgeBaseDetail(Long userId, Long kbId);
    KnowledgeBaseDto updateKnowledgeBase(Long userId, Long kbId, KbUpdateReq req);
    void deleteKnowledgeBase(Long userId, Long kbId);
    KnowledgeBaseDto getByExamAnalysisId(Long userId, Long examAnalysisId);

    void checkOwnership(Long userId, Long kbId);
}