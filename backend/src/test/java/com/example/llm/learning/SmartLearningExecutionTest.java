package com.example.llm.learning;

import com.example.llm.auth.security.AuthCrypto;
import com.example.llm.chatv2.repository.ChatV2Repository;
import com.example.llm.chatv2.stream.AiRunEventBus;
import com.example.llm.learning.service.LearningGenerationService;
import com.example.llm.learning.api.SmartLearningDtos;
import com.example.llm.learning.repository.SmartLearningRepository;
import com.example.llm.learning.repository.SmartLearningRepository.ExecutionRecord;
import com.example.llm.learning.repository.SmartLearningRepository.ProjectRecord;
import com.example.llm.learning.repository.SmartLearningRepository.ResourceRecord;
import com.example.llm.learning.repository.SmartLearningRepository.TaskRecord;
import com.example.llm.learning.repository.SmartLearningRepository.WrongItemRecord;
import com.example.llm.learning.service.SmartLearningApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmartLearningExecutionTest {
    private static final long USER_ID = 1L;
    private static final String PROJECT_ID = "project";
    private static final String TASK_ID = "task";
    private static final String EXECUTION_ID = "execution";

    private SmartLearningRepository repository;
    private SmartLearningApplicationService service;

    @BeforeEach
    void setup() {
        repository = mock(SmartLearningRepository.class);
        service = new SmartLearningApplicationService(
                repository, mock(LearningGenerationService.class), new ObjectMapper(),
                mock(AuthCrypto.class), mock(ChatV2Repository.class), mock(AiRunEventBus.class));
        when(repository.findProject(USER_ID, PROJECT_ID)).thenReturn(Optional.of(project()));
    }

    @AfterEach
    void close() {
        service.shutdown();
    }

    @Test
    void workspaceReportsLearningProgressAndWrongBookCounts() {
        TaskRecord completed = task("completed", "READING", "COMPLETED", Map.of());
        TaskRecord pending = task("pending", "EXERCISE", "READY", Map.of("questionCount", 2));
        when(repository.findTasks(USER_ID, PROJECT_ID)).thenReturn(List.of(completed, pending));
        when(repository.findResources(USER_ID, PROJECT_ID)).thenReturn(List.of());
        when(repository.findWrongItems(USER_ID, PROJECT_ID)).thenReturn(List.of(
                wrongItem("wrong-1", "TO_REVIEW"), wrongItem("wrong-2", "MASTERED")));
        when(repository.findLatestExecution(USER_ID, PROJECT_ID, "completed")).thenReturn(Optional.empty());
        when(repository.findLatestExecution(USER_ID, PROJECT_ID, "pending")).thenReturn(Optional.empty());
        when(repository.findActiveExecution(USER_ID, PROJECT_ID, "completed")).thenReturn(Optional.empty());
        when(repository.findActiveExecution(USER_ID, PROJECT_ID, "pending")).thenReturn(Optional.empty());

        SmartLearningDtos.Workspace workspace = service.workspace(USER_ID, PROJECT_ID);

        assertEquals(50, workspace.progress());
        assertEquals(1, workspace.completedTaskCount());
        assertEquals(2, workspace.totalTaskCount());
        assertEquals(2, workspace.wrongItemCount());
        assertEquals(1, workspace.pendingWrongItemCount());
    }

    @Test
    void enteringAPausedTaskResumesItsExistingExecution() {
        TaskRecord task = task(TASK_ID, "READING", "IN_PROGRESS", Map.of());
        ExecutionRecord paused = execution("PAUSED", Map.of(), null);
        ExecutionRecord resumed = execution("IN_PROGRESS", Map.of(), null);
        when(repository.findTask(USER_ID, PROJECT_ID, TASK_ID)).thenReturn(Optional.of(task));
        when(repository.findActiveExecution(USER_ID, PROJECT_ID, TASK_ID)).thenReturn(Optional.of(paused));
        when(repository.findExecution(USER_ID, EXECUTION_ID)).thenReturn(Optional.of(resumed));

        SmartLearningDtos.ExecutionView result = service.startExecution(USER_ID, PROJECT_ID, TASK_ID);

        assertEquals("IN_PROGRESS", result.status());
        verify(repository).updateExecutionStatus(USER_ID, EXECUTION_ID, "IN_PROGRESS");
        verify(repository, never()).createExecution(USER_ID, PROJECT_ID, TASK_ID);
    }

    @Test
    void exerciseIsGradedOnceOnSubmissionAndWrongAnswersEnterWrongBook() {
        TaskRecord task = task(TASK_ID, "EXERCISE", "IN_PROGRESS", Map.of("questionCount", 2));
        ResourceRecord exercise = exerciseResource();
        ExecutionRecord active = execution("IN_PROGRESS", Map.of("q1__0", "A", "q2__1", "C"), null);
        ExecutionRecord completed = execution("COMPLETED", active.answers(), 50d);
        when(repository.findExecution(USER_ID, EXECUTION_ID))
                .thenReturn(Optional.of(active), Optional.of(completed));
        when(repository.findTask(USER_ID, PROJECT_ID, TASK_ID)).thenReturn(Optional.of(task));
        when(repository.findResourcesForTask(USER_ID, PROJECT_ID, TASK_ID)).thenReturn(List.of(exercise));

        SmartLearningDtos.ExecutionView result = service.submitExecution(USER_ID, EXECUTION_ID);

        assertEquals("COMPLETED", result.status());
        assertEquals(2, result.grading().total());
        assertEquals(1, result.grading().correct());
        assertEquals(50d, result.grading().accuracy());
        verify(repository).markWrongItemMastered(USER_ID, PROJECT_ID, TASK_ID, "q1");
        verify(repository).upsertWrongItem(
                USER_ID, PROJECT_ID, TASK_ID, EXECUTION_ID, "q2",
                "第二题", "C", "B", "第二题解析", "知识点二");
        verify(repository).updateExecutionStatus(USER_ID, EXECUTION_ID, "COMPLETED");
    }

    @Test
    void exerciseCannotBeSubmittedUntilEveryQuestionHasAnAnswer() {
        TaskRecord task = task(TASK_ID, "EXERCISE", "IN_PROGRESS", Map.of("questionCount", 2));
        when(repository.findExecution(USER_ID, EXECUTION_ID))
                .thenReturn(Optional.of(execution("IN_PROGRESS", Map.of("q1__0", "A"), null)));
        when(repository.findTask(USER_ID, PROJECT_ID, TASK_ID)).thenReturn(Optional.of(task));
        when(repository.findResourcesForTask(USER_ID, PROJECT_ID, TASK_ID)).thenReturn(List.of(exerciseResource()));

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.submitExecution(USER_ID, EXECUTION_ID));

        assertEquals("还有 1 道题未作答，请完成后再提交判卷。", error.getMessage().trim());
        verify(repository, never()).updateExecutionStatus(USER_ID, EXECUTION_ID, "COMPLETED");
        verify(repository, never()).upsertWrongItem(
                USER_ID, PROJECT_ID, TASK_ID, EXECUTION_ID,
                "q2", "第二题", "", "B", "第二题解析", "知识点二");
    }

    @Test
    void correctWrongBookReviewMarksTheItemAsMastered() {
        WrongItemRecord pending = wrongItem("wrong-1", "TO_REVIEW");
        WrongItemRecord mastered = new WrongItemRecord(
                pending.externalId(), pending.projectExternalId(), pending.taskExternalId(),
                pending.questionId(), pending.stem(), "B", pending.correctAnswer(),
                pending.explanation(), pending.knowledgeKey(), "MASTERED", LocalDateTime.now());
        when(repository.findWrongItem(USER_ID, PROJECT_ID, "wrong-1"))
                .thenReturn(Optional.of(pending), Optional.of(mastered));

        SmartLearningDtos.WrongItemView result = service.reviewWrongItem(
                USER_ID, PROJECT_ID, "wrong-1", new SmartLearningDtos.ReviewWrongItemRequest("B"));

        assertEquals("MASTERED", result.status());
        verify(repository).reviewWrongItem(USER_ID, PROJECT_ID, "wrong-1", "B", true);
    }

    private ProjectRecord project() {
        return new ProjectRecord(
                PROJECT_ID, USER_ID, "学习项目", "notebook", "#667085", null, "READY",
                1, 1, 1, 1, 1, 1, null, LocalDateTime.now(),
                Map.of("examName", "前端学习"), Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
                Map.of("tasks", List.of()), Map.of(), Map.of("questionCount", 2), Map.of());
    }

    private TaskRecord task(String id, String type, String status, Map<String, Object> payload) {
        return new TaskRecord(
                id, PROJECT_ID, 1, id, id + "标题", type, "任务说明",
                "EXERCISE".equals(type) ? "完成 2 道练习题并提交判卷。" : "完成阅读",
                LocalDate.now(), 30, status, 0, payload, LocalDateTime.now());
    }

    private ExecutionRecord execution(String status, Map<String, Object> answers, Double score) {
        LocalDateTime now = LocalDateTime.now();
        return new ExecutionRecord(
                EXECUTION_ID, PROJECT_ID, TASK_ID, status, "COMPLETED".equals(status) ? 100 : 50,
                120, Map.of(), answers, score, 2, now,
                "PAUSED".equals(status) ? now : null,
                "COMPLETED".equals(status) ? now : null, now);
    }

    private ResourceRecord exerciseResource() {
        return new ResourceRecord(
                "resource", TASK_ID, "EXERCISE_SET", "练习题", "READY",
                Map.of("items", List.of(
                        Map.of("id", "q1", "stem", "第一题", "options", List.of("A", "B"),
                                "answer", "A", "explanation", "第一题解析", "knowledgeKey", "知识点一"),
                        Map.of("id", "q2", "stem", "第二题", "options", List.of("A", "B", "C"),
                                "answer", "B", "explanation", "第二题解析", "knowledgeKey", "知识点二")),
                        "questionCount", 2),
                null, LocalDateTime.now());
    }

    private WrongItemRecord wrongItem(String id, String status) {
        return new WrongItemRecord(
                id, PROJECT_ID, TASK_ID, "q2", "第二题", "C", "B",
                "第二题解析", "知识点二", status, LocalDateTime.now());
    }
}
