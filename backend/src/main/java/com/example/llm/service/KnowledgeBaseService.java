package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.dto.KbCreateReq;
import com.example.llm.dto.KbUpdateReq;
import com.example.llm.entity.KnowledgeBase;
import com.example.llm.vo.KnowledgeBaseVO;

import java.util.List;

public interface KnowledgeBaseService extends IService<KnowledgeBase> {
    
    KnowledgeBaseVO createKnowledgeBase(Long userId, KbCreateReq req);
    List<KnowledgeBaseVO> getKnowledgeBaseList(Long userId);
    KnowledgeBaseVO getKnowledgeBaseDetail(Long userId, Long kbId);
    KnowledgeBaseVO updateKnowledgeBase(Long userId, Long kbId, KbUpdateReq req);
    void deleteKnowledgeBase(Long userId, Long kbId);
    KnowledgeBaseVO getByExamAnalysisId(Long userId, Long examAnalysisId);

    void checkOwnership(Long userId, Long kbId);
}