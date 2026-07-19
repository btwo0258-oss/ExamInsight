package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.entity.LearningProject;

import java.util.List;

public interface LearningProjectService extends IService<LearningProject> {
    List<LearningProject> getUserProjects(Long userId);
    LearningProject getProjectDetail(Long userId, Long projectId);
    LearningProject createProject(Long userId, String title, String goal, Long libraryId);
    void updateProject(Long userId, Long projectId, String title, String goal);
    void deleteProject(Long userId, Long projectId);
}
