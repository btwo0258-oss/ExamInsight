package com.example.llm.learning.controller;

import com.example.llm.common.UserContext;
import com.example.llm.learning.api.SmartLearningDtos;
import com.example.llm.learning.service.SmartLearningApplicationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

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

    @PatchMapping("/projects/{projectId}/pin")
    public SmartLearningDtos.ProjectDetail pinProject(
            @PathVariable String projectId,
            @RequestBody SmartLearningDtos.PinRequest request) {
        return learning.pin(UserContext.requireSession().userId(), projectId, request);
    }

    @GetMapping("/sidebar")
    public List<SmartLearningDtos.SidebarProjectView> getSidebarProjects() {
        return learning.sidebar(UserContext.requireSession().userId());
    }

    @DeleteMapping("/projects/{projectId}")
    public void archiveProject(@PathVariable String projectId) {
        learning.archive(UserContext.requireSession().userId(), projectId);
    }

    @DeleteMapping("/projects/{projectId}/permanent")
    public void deleteProjectPermanently(@PathVariable String projectId) {
        learning.deletePermanently(UserContext.requireSession().userId(), projectId);
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

    @PostMapping("/projects/{projectId}/resources/prepare")
    public SmartLearningDtos.JobAccepted prepareResources(@PathVariable String projectId) {
        return learning.prepareResources(UserContext.requireSession().userId(), projectId);
    }

    @PostMapping("/projects/{projectId}/resources/{resourceId}/retry")
    public SmartLearningDtos.JobAccepted retryResource(
            @PathVariable String projectId,
            @PathVariable String resourceId) {
        return learning.retryResource(UserContext.requireSession().userId(), projectId, resourceId);
    }

    @GetMapping("/projects/{projectId}/workspace")
    public SmartLearningDtos.Workspace getWorkspace(@PathVariable String projectId) {
        return learning.workspace(UserContext.requireSession().userId(), projectId);
    }

    @GetMapping("/projects/{projectId}/wrong-items")
    public List<SmartLearningDtos.WrongItemView> getWrongItems(@PathVariable String projectId) {
        return learning.wrongItems(UserContext.requireSession().userId(), projectId);
    }

    @PostMapping("/projects/{projectId}/wrong-items/{wrongItemId}/review")
    public SmartLearningDtos.WrongItemView reviewWrongItem(
            @PathVariable String projectId,
            @PathVariable String wrongItemId,
            @RequestBody SmartLearningDtos.ReviewWrongItemRequest request) {
        return learning.reviewWrongItem(
                UserContext.requireSession().userId(), projectId, wrongItemId, request);
    }

    @PostMapping("/projects/{projectId}/tutor-thread")
    public SmartLearningDtos.TutorThreadView getOrCreateTutorThread(
            @PathVariable String projectId,
            @RequestBody(required = false) Map<String, Object> body) {
        Object taskId = body == null ? null : body.get("taskId");
        return learning.tutorThread(
                UserContext.requireSession().userId(), projectId,
                taskId == null ? null : String.valueOf(taskId));
    }

    @GetMapping("/projects/{projectId}/tasks/{taskId}")
    public SmartLearningDtos.TaskView getTask(
            @PathVariable String projectId,
            @PathVariable String taskId) {
        return learning.task(UserContext.requireSession().userId(), projectId, taskId);
    }

    @PostMapping("/projects/{projectId}/tasks/{taskId}/executions")
    public SmartLearningDtos.ExecutionView startExecution(
            @PathVariable String projectId,
            @PathVariable String taskId,
            @RequestBody(required = false) SmartLearningDtos.StartExecutionRequest ignored) {
        return learning.startExecution(UserContext.requireSession().userId(), projectId, taskId);
    }

    @PostMapping("/executions/{executionId}/pause")
    public SmartLearningDtos.ExecutionView pauseExecution(@PathVariable String executionId) {
        return learning.updateExecutionStatus(UserContext.requireSession().userId(), executionId, "PAUSED");
    }

    @PostMapping("/executions/{executionId}/resume")
    public SmartLearningDtos.ExecutionView resumeExecution(@PathVariable String executionId) {
        return learning.updateExecutionStatus(UserContext.requireSession().userId(), executionId, "IN_PROGRESS");
    }

    @PostMapping("/executions/{executionId}/complete")
    public SmartLearningDtos.ExecutionView completeExecution(@PathVariable String executionId) {
        return learning.submitExecution(UserContext.requireSession().userId(), executionId);
    }

    @PostMapping("/executions/{executionId}/skip")
    public SmartLearningDtos.ExecutionView skipExecution(@PathVariable String executionId) {
        return learning.updateExecutionStatus(UserContext.requireSession().userId(), executionId, "SKIPPED");
    }

    @PatchMapping("/executions/{executionId}/progress")
    public SmartLearningDtos.ExecutionView updateExecutionProgress(
            @PathVariable String executionId,
            @RequestBody SmartLearningDtos.ExecutionProgressRequest request) {
        return learning.saveExecutionProgress(UserContext.requireSession().userId(), executionId,
                request.progress(), request.secondsDelta());
    }

    @PutMapping("/executions/{executionId}/position")
    public SmartLearningDtos.ExecutionView saveExecutionPosition(
            @PathVariable String executionId,
            @RequestBody Map<String, Object> position) {
        return learning.saveExecutionPosition(UserContext.requireSession().userId(), executionId, position);
    }

    @PutMapping("/executions/{executionId}/answers")
    public SmartLearningDtos.ExecutionView saveExecutionAnswers(
            @PathVariable String executionId,
            @RequestBody Map<String, Object> answers) {
        return learning.saveExecutionAnswers(UserContext.requireSession().userId(), executionId, answers);
    }

    @PostMapping("/executions/{executionId}/heartbeat")
    public SmartLearningDtos.ExecutionView heartbeat(
            @PathVariable String executionId,
            @RequestBody SmartLearningDtos.ExecutionHeartbeatRequest request) {
        return learning.heartbeat(UserContext.requireSession().userId(), executionId,
                request.sequence(), request.secondsDelta());
    }

    @GetMapping("/jobs/{jobId}")
    public SmartLearningDtos.JobView getJob(@PathVariable String jobId) {
        return learning.job(UserContext.requireSession().userId(), jobId);
    }

    @GetMapping(value = "/jobs/{jobId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter getJobEvents(
            @PathVariable String jobId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        return learning.jobEvents(UserContext.requireSession().userId(), jobId, lastEventId);
    }
}
