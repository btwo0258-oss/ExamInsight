package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.dto.ExamAnalysisCreateReq;
import com.example.llm.dto.ExamAnalysisUpdateReq;
import com.example.llm.entity.ExamAnalysis;
import com.example.llm.service.ExamAnalysisService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/exam-analysis")
public class ExamAnalysisController {

    @Autowired
    private ExamAnalysisService examAnalysisService;

    @GetMapping("/list")
    public Result<List<ExamAnalysis>> listExamAnalyses() {
        Long userId = UserContext.getUserId();
        List<ExamAnalysis> list = examAnalysisService.listExamAnalyses(userId);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<ExamAnalysis> getExamAnalysisDetail(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        ExamAnalysis analysis = examAnalysisService.getExamAnalysisDetail(id, userId);
        return Result.success(analysis);
    }

    @PostMapping("/create")
    public Result<Long> createExamAnalysis(
            @RequestParam("title") String title,
            @RequestParam("examType") String examType,
            @RequestParam("fileNames") String fileNames,
            @RequestParam("files") List<MultipartFile> files) {
        Long userId = UserContext.getUserId();
        Long id = examAnalysisService.createExamAnalysis(title, examType, fileNames, files, userId);
        return Result.success(id);
    }

    @PutMapping("/{id}")
    public Result<Void> updateExamAnalysis(@PathVariable Long id, @RequestBody ExamAnalysisUpdateReq req) {
        Long userId = UserContext.getUserId();
        req.setId(id);
        examAnalysisService.updateExamAnalysis(req, userId);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteExamAnalysis(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        examAnalysisService.deleteExamAnalysis(id, userId);
        return Result.success(null);
    }

    @PostMapping("/{id}/analyze")
    public Result<ExamAnalysis> analyzeExam(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        ExamAnalysis analysis = examAnalysisService.analyzeExam(id, userId);
        return Result.success(analysis);
    }

    @PostMapping("/{id}/suggestions")
    public Result<ExamAnalysis> generateSuggestions(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        ExamAnalysis analysis = examAnalysisService.generateSuggestions(id, userId);
        return Result.success(analysis);
    }
}
