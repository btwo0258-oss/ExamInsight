package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.entity.GenerationJob;

import java.util.List;

public interface GenerationJobService extends IService<GenerationJob> {
    GenerationJob createJob(Long userId, Long projectId, String type);
    GenerationJob getJobByJobId(String jobId);
    List<GenerationJob> getJobsByProjectId(Long projectId);
    void updateJobStatus(String jobId, String status, Integer progress, String result, String errorCode, String errorMessage);
    void deleteJob(String jobId);
}
