package com.example.llm.learning;

import com.example.llm.auth.security.AuthCrypto;
import com.example.llm.integration.ai.AiCapabilityRouter;
import com.example.llm.learning.api.SmartLearningDtos;
import com.example.llm.learning.repository.SmartLearningRepository;
import com.example.llm.learning.repository.SmartLearningRepository.ProjectRecord;
import com.example.llm.learning.service.SmartLearningApplicationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SmartLearningDraftTest {
    SmartLearningRepository repository;
    SmartLearningApplicationService service;
    @BeforeEach void setup() {
        repository = mock(SmartLearningRepository.class);
        when(repository.writeJson(any())).thenAnswer(invocation -> new ObjectMapper().writeValueAsString(invocation.getArgument(0)));
        service = new SmartLearningApplicationService(repository, mock(AiCapabilityRouter.class), new ObjectMapper(),
                mock(AuthCrypto.class), mock(com.example.llm.chatv2.repository.ChatV2Repository.class));
        when(repository.findLatestJob(1L, "project")).thenReturn(Optional.empty());
    }
    @AfterEach void close() { service.shutdown(); }
    void project(String stage, Map<String, Object> scope, Map<String, Object> plan, Map<String, Object> sources) {
        when(repository.findProject(1L, "project")).thenReturn(Optional.of(new ProjectRecord(
            "project", 1L, "学习项目", "notebook", "#667085", "kb", stage,
            1, 1, 1, 0, 0, 0, null, LocalDateTime.now(),
            Map.of(), Map.of(), sources, sources, Map.of(), scope,
            Map.of(), Map.of(), Map.of(), Map.of(), plan, Map.of(), Map.of())));
    }
    @Test void incompleteScopeCanBeSavedButNotConfirmed() {
        var draft = Map.<String,Object>of("nodes", List.of(Map.of("id", "n1", "title", "")));
        project("SCOPE_REQUIRED", draft, Map.of(), Map.of());
        assertDoesNotThrow(() -> service.saveScopeCandidate(1L, "project", draft));
        verify(repository).saveScopeCandidate(eq(1L), eq("project"), anyString());
        assertThrows(IllegalArgumentException.class, () -> service.confirmScope(1L, "project"));
        verify(repository, never()).confirmScope(anyLong(), anyString());
    }
    @Test void incompletePlanCanBeSavedButNotConfirmed() {
        var draft = Map.<String,Object>of("tasks", List.of(Map.of("id", "t1", "title", "")));
        project("PLAN_REQUIRED", Map.of(), draft, Map.of());
        assertDoesNotThrow(() -> service.savePlanCandidate(1L, "project", draft));
        assertThrows(IllegalArgumentException.class, () -> service.confirmPlan(1L, "project"));
        verify(repository, never()).confirmPlan(anyLong(), anyString());
    }
    @Test void emptyScopeDraftSurvivesDeletingLastNode() {
        project("SCOPE_REQUIRED", Map.of(), Map.of(), Map.of());
        assertDoesNotThrow(() -> service.saveScopeCandidate(1L, "project", Map.of("nodes", List.of())));
    }
    @Test void incompleteResourcesAreSavedWithoutAdvancingStage() {
        project("RESOURCE_CONFIG_REQUIRED", Map.of(), Map.of(), Map.of());
        assertDoesNotThrow(() -> service.saveResourceConfig(1L, "project", Map.of("questionCount", "")));
        verify(repository).saveResourceConfigDraft(eq(1L), eq("project"), anyString());
        verify(repository, never()).confirmResourceConfig(anyLong(), anyString());
    }
    @Test void skipReasonCanBeDraftedBeforeQuestionsWithoutSkipping() {
        project("DIAGNOSTIC_REQUIRED", Map.of(), Map.of(), Map.of());
        assertDoesNotThrow(() -> service.saveDiagnosisAnswers(1L, "project", Map.of("answers", List.of(), "skipReason", "已有基础", "skipRequested", true)));
        verify(repository).saveDiagnosisAnswersDraft(eq(1L), eq("project"), contains("已有基础"));
        verify(repository, never()).skipDiagnosis(anyLong(), anyString(), anyString());
    }
    @Test void knowledgeBaseAssociationAloneDoesNotCountAsSelectedFiles() {
        project("SOURCES_REQUIRED", Map.of(), Map.of(), Map.of("assets", List.of(), "knowledgeBaseId", "kb"));
        assertThrows(IllegalArgumentException.class, () -> service.confirmSources(1L, "project"));
    }
    @Test void editingAppearanceDoesNotChangeWorkflow() {
        project("SCOPE_REQUIRED", Map.of(), Map.of(), Map.of());
        service.rename(1L, "project", new SmartLearningDtos.RenameRequest("新名称", "flask", "#123456"));
        verify(repository).updateProjectAppearance(1L, "project", "新名称", "flask", "#123456");
        verify(repository, never()).confirmTarget(anyLong(), anyString());
        assertThrows(IllegalArgumentException.class, () -> service.rename(1L, "project", new SmartLearningDtos.RenameRequest("新名称", "flask", "url(x)")));
    }
}
