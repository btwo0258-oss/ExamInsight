package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.dto.KbCreateReq;
import com.example.llm.dto.KbUpdateReq;
import com.example.llm.dto.KnowledgeBaseDto;
import com.example.llm.service.KnowledgeBaseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/kb")
public class KnowledgeBaseController {

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @PostMapping("/create")
    public Result<KnowledgeBaseDto> create(@Validated @RequestBody KbCreateReq req) {
        return Result.success("创建成功", knowledgeBaseService.createKnowledgeBase(UserContext.getUserId(), req));
    }

    @GetMapping("/list")
    public Result<List<KnowledgeBaseDto>> list() {
        return Result.success(knowledgeBaseService.getKnowledgeBaseList(UserContext.getUserId()));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeBaseDto> detail(@PathVariable("id") Long id) {
        return Result.success(knowledgeBaseService.getKnowledgeBaseDetail(UserContext.getUserId(), id));
    }

    @PutMapping("/{id}")
    public Result<KnowledgeBaseDto> update(@PathVariable("id") Long id, @Validated @RequestBody KbUpdateReq req) {
        return Result.success("更新成功", knowledgeBaseService.updateKnowledgeBase(UserContext.getUserId(), id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        knowledgeBaseService.deleteKnowledgeBase(UserContext.getUserId(), id);
        return Result.success("删除成功", null);
    }

    @GetMapping("/by-exam-analysis/{examAnalysisId}")
    public Result<KnowledgeBaseDto> getByExamAnalysisId(@PathVariable("examAnalysisId") Long examAnalysisId) {
        KnowledgeBaseDto kb = knowledgeBaseService.getByExamAnalysisId(UserContext.getUserId(), examAnalysisId);
        return Result.success(kb);
    }
}
