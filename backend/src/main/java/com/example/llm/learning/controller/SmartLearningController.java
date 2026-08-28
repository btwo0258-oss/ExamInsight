package com.example.llm.learning.controller;

import com.example.llm.common.UserContext;
import com.example.llm.learning.api.SmartLearningDtos;
import com.example.llm.learning.service.SmartLearningApplicationService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v2/learning")
public class SmartLearningController {
    private final SmartLearningApplicationService learning;

    public SmartLearningController(SmartLearningApplicationService learning) {
        this.learning = learning;
    }

    @GetMapping("/projects")
    public List<SmartLearningDtos.ProjectSummary> listProjects() {
        return learning.list(UserContext.requireSession().userId());
    }

    @PostMapping("/projects")
    public SmartLearningDtos.ProjectDetail createProject(
            @RequestBody SmartLearningDtos.CreateProjectRequest request) {
        return learning.create(UserContext.requireSession().userId(), request);
    }

    @GetMapping("/projects/{projectId}")
    public SmartLearningDtos.ProjectDetail getProject(@PathVariable String projectId) {
        return learning.detail(UserContext.requireSession().userId(), projectId);
    }

    @PatchMapping("/projects/{projectId}")
    public SmartLearningDtos.ProjectDetail renameProject(
            @PathVariable String projectId,
            @RequestBody SmartLearningDtos.RenameRequest request) {
        return learning.rename(UserContext.requireSession().userId(), projectId, request);
    }

    @DeleteMapping("/projects/{projectId}")
    public void archiveProject(@PathVariable String projectId) {
        learning.archive(UserContext.requireSession().userId(), projectId);
    }

    @PostMapping("/projects/{projectId}/restore")
    public SmartLearningDtos.ProjectDetail restoreProject(@PathVariable String projectId) {
        return learning.restore(UserContext.requireSession().userId(), projectId);
    }

    @PatchMapping("/projects/{projectId}/target")
    public SmartLearningDtos.ProjectDetail saveTarget(
            @PathVariable String projectId,
            @RequestBody Map<String, Object> target) {
        return learning.saveTarget(UserContext.requireSession().userId(), projectId, target);
    }

    @PostMapping("/projects/{projectId}/target/confirm")
    public SmartLearningDtos.ProjectDetail confirmTarget(@PathVariable String projectId) {
        return learning.confirmTarget(UserContext.requireSession().userId(), projectId);
    }

    @PutMapping("/projects/{projectId}/sources")
    public SmartLearningDtos.ProjectDetail saveSources(
            @PathVariable String projectId,
            @RequestBody Map<String, Object> sources) {
        Object knowledgeBaseId = sources == null ? null : sources.get("knowledgeBaseId");
        return learning.saveSources(UserContext.requireSession().userId(), projectId,
                knowledgeBaseId == null ? null : String.valueOf(knowledgeBaseId),
                sources == null ? Map.of() : sources);
    }

    @PostMapping("/projects/{projectId}/sources/confirm")
    public SmartLearningDtos.ProjectDetail confirmSources(@PathVariable String projectId) {
        return learning.confirmSources(UserContext.requireSession().userId(), projectId);
    }

    @PostMapping("/projects/{projectId}/scope/generate")
    public SmartLearningDtos.JobAccepted generateScope(@PathVariable String projectId) {
        return learning.generateScope(UserContext.requireSession().userId(), projectId);
    }

    @PatchMapping("/projects/{projectId}/scope/candidate")
    public SmartLearningDtos.ProjectDetail saveScopeCandidate(
            @PathVariable String projectId,
            @RequestBody Map<String, Object> scope) {
        return learning.saveScopeCandidate(UserContext.requireSession().userId(), projectId, scope);
    }

    @PostMapping("/projects/{projectId}/scope/confirm")
    public SmartLearningDtos.ProjectDetail confirmScope(@PathVariable String projectId) {
        return learning.confirmScope(UserContext.requireSession().userId(), projectId);
    }

    @PostMapping("/projects/{projectId}/diagnosis/generate")
    public SmartLearningDtos.JobAccepted generateDiagnosis(@PathVariable String projectId) {
        return learning.generateDiagnosis(UserContext.requireSession().userId(), projectId);
    }

    @PostMapping("/projects/{projectId}/diagnosis/submit")
    public SmartLearningDtos.ProjectDetail submitDiagnosis(
            @PathVariable String projectId,
            @RequestBody Map<String, Object> body) {
        return learning.submitDiagnosis(UserContext.requireSession().userId(), projectId, body);
    }

    @PatchMapping("/projects/{projectId}/diagnosis/answers")
    public SmartLearningDtos.ProjectDetail saveDiagnosisAnswers(
            @PathVariable String projectId,
            @RequestBody Map<String, Object> body) {
        return learning.saveDiagnosisAnswers(UserContext.requireSession().userId(), projectId, body);
    }

    @PostMapping("/projects/{projectId}/diagnosis/skip")
    public SmartLearningDtos.ProjectDetail skipDiagnosis(
            @PathVariable String projectId,
            @RequestBody(required = false) Map<String, Object> body) {
        return learning.skipDiagnosis(UserContext.requireSession().userId(), projectId,
                body == null ? Map.of() : body);
    }

    @PostMapping("/projects/{projectId}/plan/generate")
    public SmartLearningDtos.JobAccepted generatePlan(@PathVariable String projectId) {
        return learning.generatePlan(UserContext.requireSession().userId(), projectId);
    }

    @PatchMapping("/projects/{projectId}/plan/candidate")
    public SmartLearningDtos.ProjectDetail savePlanCandidate(
            @PathVariable String projectId,
            @RequestBody Map<String, Object> plan) {
        return learning.savePlanCandidate(UserContext.requireSession().userId(), projectId, plan);
    }

    @PostMapping("/projects/{projectId}/plan/confirm")
    public SmartLearningDtos.ProjectDetail confirmPlan(@PathVariable String projectId) {
        return learning.confirmPlan(UserContext.requireSession().userId(), projectId);
    }

    @PutMapping("/projects/{projectId}/resources/config")
    public SmartLearningDtos.ProjectDetail saveResourceConfig(
            @PathVariable String projectId,
            @RequestBody Map<String, Object> config) {
        return learning.saveResourceConfig(UserContext.requireSession().userId(), projectId, config);
    }

    @PostMapping("/projects/{projectId}/resources/confirm")
    public SmartLearningDtos.ProjectDetail confirmResourceConfig(@PathVariable String projectId) {
        return learning.confirmResourceConfig(UserContext.requireSession().userId(), projectId);
    }

    @GetMapping("/jobs/{jobId}")
    public SmartLearningDtos.JobView getJob(@PathVariable String jobId) {
        return learning.job(UserContext.requireSession().userId(), jobId);
    }
}
