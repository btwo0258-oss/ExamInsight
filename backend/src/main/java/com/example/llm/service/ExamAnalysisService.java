package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.dto.ExamAnalysisUpdateReq;
import com.example.llm.entity.ExamAnalysis;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ExamAnalysisService extends IService<ExamAnalysis> {
    List<ExamAnalysis> listExamAnalyses(Long userId);
    ExamAnalysis getExamAnalysisDetail(Long id, Long userId);
    Long createExamAnalysis(String title, String examType, String fileNames, List<MultipartFile> files, Long userId);
    void updateExamAnalysis(ExamAnalysisUpdateReq req, Long userId);
    void deleteExamAnalysis(Long id, Long userId);
    ExamAnalysis analyzeExam(Long id, Long userId);
    ExamAnalysis generateSuggestions(Long id, Long userId);
}
