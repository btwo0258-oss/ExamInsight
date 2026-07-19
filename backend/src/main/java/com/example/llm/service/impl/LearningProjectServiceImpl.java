package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.entity.LearningProject;
import com.example.llm.mapper.LearningProjectMapper;
import com.example.llm.service.LearningProjectService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class LearningProjectServiceImpl extends ServiceImpl<LearningProjectMapper, LearningProject> implements LearningProjectService {
    
    @Override
    public List<LearningProject> getUserProjects(Long userId) {
        LambdaQueryWrapper<LearningProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningProject::getUserId, userId)
               .ne(LearningProject::getStatus, "deleted")
               .orderByDesc(LearningProject::getUpdateTime);
        return this.list(wrapper);
    }
    
    @Override
    public LearningProject getProjectDetail(Long userId, Long projectId) {
        LambdaQueryWrapper<LearningProject> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LearningProject::getId, projectId)
               .eq(LearningProject::getUserId, userId);
        LearningProject project = this.getOne(wrapper);
        if (project == null) {
            throw new IllegalArgumentException("项目不存在或无权限");
        }
        return project;
    }
    
    @Override
    public LearningProject createProject(Long userId, String title, String goal, Long libraryId) {
        LearningProject project = new LearningProject();
        project.setUserId(userId);
        project.setTitle(title);
        project.setGoal(goal);
        project.setLibraryId(libraryId);
        project.setStatus("active");
        project.setTotalTasks(0);
        project.setCompletedTasks(0);
        project.setProgress(0);
        project.setCreateTime(LocalDateTime.now());
        project.setUpdateTime(LocalDateTime.now());
        this.save(project);
        return project;
    }
    
    @Override
    public void updateProject(Long userId, Long projectId, String title, String goal) {
        LearningProject project = getProjectDetail(userId, projectId);
        if (title != null) {
            project.setTitle(title);
        }
        if (goal != null) {
            project.setGoal(goal);
        }
        project.setUpdateTime(LocalDateTime.now());
        this.updateById(project);
    }
    
    @Override
    public void deleteProject(Long userId, Long projectId) {
        LearningProject project = getProjectDetail(userId, projectId);
        project.setStatus("deleted");
        project.setUpdateTime(LocalDateTime.now());
        this.updateById(project);
    }
}
