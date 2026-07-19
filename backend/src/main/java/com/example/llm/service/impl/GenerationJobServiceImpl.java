package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.entity.GenerationJob;
import com.example.llm.mapper.GenerationJobMapper;
import com.example.llm.service.GenerationJobService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class GenerationJobServiceImpl extends ServiceImpl<GenerationJobMapper, GenerationJob> implements GenerationJobService {
    
    @Override
    public GenerationJob createJob(Long userId, Long projectId, String type) {
        GenerationJob job = new GenerationJob();
        job.setJobId(UUID.randomUUID().toString());
        job.setUserId(userId);
        job.setProjectId(projectId);
        job.setType(type);
        job.setStatus("pending");
        job.setProgress(0);
        job.setCreateTime(LocalDateTime.now());
        job.setUpdateTime(LocalDateTime.now());
        this.save(job);
        return job;
    }
    
    @Override
    public GenerationJob getJobByJobId(String jobId) {
        LambdaQueryWrapper<GenerationJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenerationJob::getJobId, jobId);
        return this.getOne(wrapper);
    }
    
    @Override
    public List<GenerationJob> getJobsByProjectId(Long projectId) {
        LambdaQueryWrapper<GenerationJob> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(GenerationJob::getProjectId, projectId)
               .orderByDesc(GenerationJob::getCreateTime);
        return this.list(wrapper);
    }
    
    @Override
    public void updateJobStatus(String jobId, String status, Integer progress, String result, String errorCode, String errorMessage) {
        GenerationJob job = getJobByJobId(jobId);
        if (job == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        if (status != null) {
            job.setStatus(status);
        }
        if (progress != null) {
            job.setProgress(progress);
        }
        if (result != null) {
            job.setResult(result);
        }
        if (errorCode != null) {
            job.setErrorCode(errorCode);
        }
        if (errorMessage != null) {
            job.setErrorMessage(errorMessage);
        }
        job.setUpdateTime(LocalDateTime.now());
        this.updateById(job);
    }
    
    @Override
    public void deleteJob(String jobId) {
        GenerationJob job = getJobByJobId(jobId);
        if (job == null) {
            throw new IllegalArgumentException("任务不存在");
        }
        this.removeById(job.getId());
    }
}
