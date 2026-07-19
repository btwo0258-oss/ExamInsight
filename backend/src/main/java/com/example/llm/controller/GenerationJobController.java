package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.entity.GenerationJob;
import com.example.llm.service.GenerationJobService;
import com.example.llm.vo.GenerationJobVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/generation/jobs")
public class GenerationJobController {
    
    @Autowired
    private GenerationJobService generationJobService;
    
    @PostMapping
    public Result<GenerationJobVO> createJob(@RequestBody Map<String, Object> request) {
        Long userId = UserContext.getUserId();
        Long projectId = request.get("projectId") != null ? Long.valueOf(request.get("projectId").toString()) : null;
        String type = (String) request.get("type");
        GenerationJob job = generationJobService.createJob(userId, projectId, type);
        return Result.success(convertToVO(job));
    }
    
    @GetMapping("/{jobId}")
    public Result<GenerationJobVO> getJobByJobId(@PathVariable String jobId) {
        GenerationJob job = generationJobService.getJobByJobId(jobId);
        return Result.success(convertToVO(job));
    }
    
    @GetMapping("/project/{projectId}")
    public Result<List<GenerationJobVO>> getJobsByProjectId(@PathVariable Long projectId) {
        List<GenerationJob> jobs = generationJobService.getJobsByProjectId(projectId);
        List<GenerationJobVO> voList = jobs.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }
    
    @PutMapping("/{jobId}")
    public Result<Void> updateJobStatus(@PathVariable String jobId, @RequestBody Map<String, Object> request) {
        String status = (String) request.get("status");
        Integer progress = request.get("progress") != null ? Integer.valueOf(request.get("progress").toString()) : null;
        String result = (String) request.get("result");
        String errorCode = (String) request.get("errorCode");
        String errorMessage = (String) request.get("errorMessage");
        generationJobService.updateJobStatus(jobId, status, progress, result, errorCode, errorMessage);
        return Result.success(null);
    }
    
    @DeleteMapping("/{jobId}")
    public Result<Void> deleteJob(@PathVariable String jobId) {
        generationJobService.deleteJob(jobId);
        return Result.success(null);
    }
    
    private GenerationJobVO convertToVO(GenerationJob job) {
        GenerationJobVO vo = new GenerationJobVO();
        BeanUtils.copyProperties(job, vo);
        return vo;
    }
}
