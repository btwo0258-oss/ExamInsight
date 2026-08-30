package com.example.llm.learning.repository;

import com.example.llm.auth.security.AuthCrypto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistence for the V2 smart-learning preparation workflow.
 *
 * The old learning repository uses numeric legacy identifiers and a mutable
 * generated-plan JSON document. This repository deliberately keeps the V2
 * external identifiers and confirmed/draft columns separate.
 */
@Repository
public class SmartLearningRepository {
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final AuthCrypto crypto;

    public SmartLearningRepository(
            @Qualifier("v2JdbcTemplate") JdbcTemplate jdbc,
            ObjectMapper objectMapper,
            AuthCrypto crypto) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.crypto = crypto;
    }

    public String createProject(
            long userId,
            String name,
            String icon,
            String iconColor,
            String knowledgeBaseExternalId) {
        String externalId = crypto.newExternalId();
        jdbc.update("""
                INSERT INTO smart_learning_project (
                    external_id, user_id, name, icon, icon_color,
                    knowledge_base_external_id, stage
                ) VALUES (?, ?, ?, ?, ?, ?, 'TARGET_REQUIRED')
                """, externalId, userId, name, icon, iconColor, knowledgeBaseExternalId);
        return externalId;
    }

    public List<ProjectRecord> findProjects(long userId) {
        return jdbc.query("""
                SELECT external_id, user_id, name, icon, icon_color,
                       knowledge_base_external_id, stage,
                       target_version, source_version, scope_version,
                       diagnosis_version, plan_version, resource_config_version,
                       pinned_at, updated_at
                  FROM smart_learning_project
                 WHERE user_id = ? AND stage <> 'ARCHIVED'
                 ORDER BY updated_at DESC, id DESC
                """, (rs, rowNum) -> new ProjectRecord(
                rs.getString("external_id"),
                rs.getLong("user_id"),
                rs.getString("name"),
                rs.getString("icon"),
                rs.getString("icon_color"),
                rs.getString("knowledge_base_external_id"),
                rs.getString("stage"),
                rs.getInt("target_version"),
                rs.getInt("source_version"),
                rs.getInt("scope_version"),
                rs.getInt("diagnosis_version"),
                rs.getInt("plan_version"),
                rs.getInt("resource_config_version"),
                rs.getObject("pinned_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class),
                Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()), userId);
    }

    public Optional<ProjectRecord> findProject(long userId, String projectExternalId) {
        List<ProjectRecord> rows = jdbc.query("""
                SELECT external_id, user_id, name, icon, icon_color,
                       knowledge_base_external_id, stage,
                       target_version, target_json, target_draft_json,
                       source_version, sources_json, sources_draft_json,
                       scope_version, scope_json, scope_candidate_json,
                       diagnosis_version, diagnosis_json, diagnosis_candidate_json, diagnosis_answers_draft_json,
                       plan_version, plan_json, plan_candidate_json,
                       resource_config_version, resource_config_json, resource_config_draft_json,
                       pinned_at, updated_at
                  FROM smart_learning_project
                 WHERE user_id = ? AND external_id = ? AND stage <> 'ARCHIVED'
                """, this::mapProject, userId, projectExternalId);
        return rows.stream().findFirst();
    }

    public void renameProject(long userId, String projectExternalId, String name) {
        updateProjectAppearance(userId, projectExternalId, name, null, null);
    }

    public void updateProjectAppearance(long userId, String projectExternalId, String name, String icon, String iconColor) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET name = ?, icon = COALESCE(?, icon), icon_color = COALESCE(?, icon_color), row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ? AND stage <> 'ARCHIVED'
                """, name, icon, iconColor, userId, projectExternalId);
    }

    public void setProjectPinned(long userId, String projectExternalId, boolean pinned) {
        int updated = jdbc.update("""
                UPDATE smart_learning_project
                   SET pinned_at = CASE WHEN ? THEN COALESCE(pinned_at, CURRENT_TIMESTAMP(3)) ELSE NULL END,
                       row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ? AND stage <> 'ARCHIVED'
                """, pinned, userId, projectExternalId);
        if (updated == 0) throw new IllegalArgumentException("学习项目不存在或无法置顶。");
    }

    public List<String> findTutorConversationIds(long userId, String projectExternalId) {
        return jdbc.queryForList("""
                SELECT conversation_external_id
                  FROM smart_learning_tutor_thread
                 WHERE user_id = ? AND project_external_id = ?
                """, String.class, userId, projectExternalId);
    }

    public void deleteProjectPermanently(long userId, String projectExternalId) {
        int deleted = jdbc.update("""
                DELETE FROM smart_learning_project
                 WHERE user_id = ? AND external_id = ?
                """, userId, projectExternalId);
        if (deleted == 0) throw new IllegalArgumentException("学习项目不存在或已被删除。");
    }

    public List<TutorSidebarRecord> findTutorConversations(long userId) {
        return jdbc.query("""
                SELECT tutor.project_external_id, tutor.conversation_external_id,
                       c.title, tutor.task_external_id, task.title AS task_title,
                       CASE WHEN tutor.task_external_id IS NULL THEN 'PROJECT' ELSE 'TASK' END AS context_type,
                       c.updated_at
                  FROM smart_learning_tutor_thread tutor
                  JOIN smart_learning_project project
                    ON project.external_id = tutor.project_external_id
                   AND project.user_id = tutor.user_id
                  JOIN conversation c
                    ON c.external_id = tutor.conversation_external_id
                   AND c.user_id = tutor.user_id
                  LEFT JOIN smart_learning_task task
                    ON task.external_id = tutor.task_external_id
                 WHERE tutor.user_id = ? AND tutor.status = 'ACTIVE'
                   AND project.stage <> 'ARCHIVED' AND c.status = 'ACTIVE'
                 ORDER BY c.updated_at DESC, c.id DESC
                """, (rs, rowNum) -> new TutorSidebarRecord(
                rs.getString("project_external_id"), rs.getString("conversation_external_id"),
                rs.getString("title"), rs.getString("task_external_id"), rs.getString("task_title"),
                rs.getString("context_type"), rs.getObject("updated_at", LocalDateTime.class)), userId);
    }

    public void archiveProject(long userId, String projectExternalId) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET archived_previous_stage = stage,
                       stage = 'ARCHIVED', row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ? AND stage <> 'ARCHIVED'
                """, userId, projectExternalId);
        jdbc.update("""
                UPDATE conversation c
                  JOIN smart_learning_tutor_thread tutor ON tutor.conversation_external_id = c.external_id
                   SET c.status = 'ARCHIVED', c.archived_at = CURRENT_TIMESTAMP(3),
                       c.row_version = c.row_version + 1, tutor.status = 'ARCHIVED'
                 WHERE tutor.user_id = ? AND tutor.project_external_id = ? AND c.status = 'ACTIVE'
                """, userId, projectExternalId);
    }

    public void restoreProject(long userId, String projectExternalId) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET stage = COALESCE(archived_previous_stage, 'TARGET_REQUIRED'),
                       archived_previous_stage = NULL, row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ? AND stage = 'ARCHIVED'
                """, userId, projectExternalId);
        jdbc.update("""
                UPDATE conversation c
                  JOIN smart_learning_tutor_thread tutor ON tutor.conversation_external_id = c.external_id
                   SET c.status = 'ACTIVE', c.archived_at = NULL,
                       c.row_version = c.row_version + 1, tutor.status = 'ACTIVE'
                 WHERE tutor.user_id = ? AND tutor.project_external_id = ? AND c.status = 'ARCHIVED'
                """, userId, projectExternalId);
    }

    public Optional<ProjectRecord> findArchivedProject(long userId, String projectExternalId) {
        List<ProjectRecord> rows = jdbc.query("""
                SELECT external_id, user_id, name, icon, icon_color,
                       knowledge_base_external_id, stage,
                       target_version, target_json, target_draft_json,
                       source_version, sources_json, sources_draft_json,
                       scope_version, scope_json, scope_candidate_json,
                       diagnosis_version, diagnosis_json, diagnosis_candidate_json, diagnosis_answers_draft_json,
                       plan_version, plan_json, plan_candidate_json,
                       resource_config_version, resource_config_json, resource_config_draft_json,
                       pinned_at, updated_at
                  FROM smart_learning_project
                 WHERE user_id = ? AND external_id = ? AND stage = 'ARCHIVED'
                """, this::mapProject, userId, projectExternalId);
        return rows.stream().findFirst();
    }

    public boolean ownsActiveKnowledgeBase(long userId, String knowledgeBaseExternalId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM knowledge_base
                 WHERE user_id = ? AND external_id = ? AND status = 'ACTIVE'
                """, Integer.class, userId, knowledgeBaseExternalId);
        return count != null && count > 0;
    }

    public boolean ownsActiveAsset(long userId, String assetExternalId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM asset
                 WHERE user_id = ? AND external_id = ? AND status = 'ACTIVE'
                """, Integer.class, userId, assetExternalId);
        return count != null && count > 0;
    }

    public boolean ownsReadyVersion(long userId, String assetExternalId, String versionExternalId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*)
                  FROM asset_version av
                  JOIN asset a ON a.id = av.asset_id
                 WHERE a.user_id = ? AND a.external_id = ?
                   AND av.external_id = ? AND av.status = 'READY'
                """, Integer.class, userId, assetExternalId, versionExternalId);
        return count != null && count > 0;
    }

    public void saveTargetDraft(long userId, String projectId, String json) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET target_draft_json = CAST(? AS JSON), row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ? AND stage <> 'ARCHIVED'
                """, json, userId, projectId);
    }

    public void confirmTarget(long userId, String projectId) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET target_json = target_draft_json,
                       target_version = target_version + 1,
                       sources_json = NULL,
                       sources_draft_json = NULL,
                       scope_json = NULL,
                       scope_candidate_json = NULL,
                       diagnosis_json = NULL,
                       diagnosis_candidate_json = NULL,
                       diagnosis_answers_draft_json = NULL,
                       plan_json = NULL,
                       plan_candidate_json = NULL,
                       resource_config_json = NULL,
                       resource_config_draft_json = NULL,
                       stage = 'SOURCES_REQUIRED',
                       row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ?
                   AND target_draft_json IS NOT NULL
                   AND stage <> 'ARCHIVED'
                """, userId, projectId);
    }

    public void saveSourcesDraft(
            long userId,
            String projectId,
            String knowledgeBaseExternalId,
            String json) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET knowledge_base_external_id = ?,
                       sources_draft_json = CAST(? AS JSON),
                       row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ? AND stage <> 'ARCHIVED'
                """, knowledgeBaseExternalId, json, userId, projectId);
    }

    public void confirmSources(long userId, String projectId) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET sources_json = sources_draft_json,
                       source_version = source_version + 1,
                       scope_json = NULL,
                       scope_candidate_json = NULL,
                       diagnosis_json = NULL,
                       diagnosis_candidate_json = NULL,
                       diagnosis_answers_draft_json = NULL,
                       plan_json = NULL,
                       plan_candidate_json = NULL,
                       resource_config_json = NULL,
                       resource_config_draft_json = NULL,
                       stage = 'SCOPE_REQUIRED',
                       row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ?
                   AND sources_draft_json IS NOT NULL
                   AND stage <> 'ARCHIVED'
                """, userId, projectId);
    }

    public void saveScopeCandidate(long userId, String projectId, String json) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET scope_candidate_json = CAST(? AS JSON), row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ? AND stage <> 'ARCHIVED'
                """, json, userId, projectId);
    }

    public void confirmScope(long userId, String projectId) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET scope_json = scope_candidate_json,
                       scope_version = scope_version + 1,
                       diagnosis_json = NULL,
                       diagnosis_candidate_json = NULL,
                       diagnosis_answers_draft_json = NULL,
                       plan_json = NULL,
                       plan_candidate_json = NULL,
                       resource_config_json = NULL,
                       resource_config_draft_json = NULL,
                       stage = 'DIAGNOSTIC_REQUIRED',
                       row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ?
                   AND scope_candidate_json IS NOT NULL
                   AND stage <> 'ARCHIVED'
                """, userId, projectId);
    }

    public void saveDiagnosisCandidate(long userId, String projectId, String json) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET diagnosis_candidate_json = CAST(? AS JSON),
                       diagnosis_answers_draft_json = NULL,
                       row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ? AND stage IN ('DIAGNOSTIC_REQUIRED', 'PLAN_REQUIRED', 'RESOURCE_CONFIG_REQUIRED', 'READY')
                """, json, userId, projectId);
    }

    public void saveDiagnosisAnswersDraft(long userId, String projectId, String json) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET diagnosis_answers_draft_json = CAST(? AS JSON), row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ?
                   AND stage IN ('DIAGNOSTIC_REQUIRED', 'PLAN_REQUIRED', 'RESOURCE_CONFIG_REQUIRED', 'READY')
                """, json, userId, projectId);
    }

    public void confirmDiagnosis(long userId, String projectId, String json) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET diagnosis_json = CAST(? AS JSON),
                       diagnosis_version = diagnosis_version + 1,
                       diagnosis_answers_draft_json = NULL,
                       plan_json = NULL,
                       plan_candidate_json = NULL,
                       resource_config_json = NULL,
                       resource_config_draft_json = NULL,
                       stage = 'PLAN_REQUIRED',
                       row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ? AND stage IN ('DIAGNOSTIC_REQUIRED', 'PLAN_REQUIRED', 'RESOURCE_CONFIG_REQUIRED', 'READY')
                """, json, userId, projectId);
    }

    public void skipDiagnosis(long userId, String projectId, String json) {
        confirmDiagnosis(userId, projectId, json);
    }

    public void savePlanCandidate(long userId, String projectId, String json) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET plan_candidate_json = CAST(? AS JSON), row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ? AND stage IN ('PLAN_REQUIRED', 'RESOURCE_CONFIG_REQUIRED', 'READY')
                """, json, userId, projectId);
    }

    public void confirmPlan(long userId, String projectId) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET plan_json = plan_candidate_json,
                       plan_version = plan_version + 1,
                       resource_config_draft_json = COALESCE(resource_config_draft_json, JSON_OBJECT()),
                       stage = 'RESOURCE_CONFIG_REQUIRED',
                       row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ? AND stage IN ('PLAN_REQUIRED', 'RESOURCE_CONFIG_REQUIRED', 'READY')
                   AND plan_candidate_json IS NOT NULL
                """, userId, projectId);
    }

    public void saveResourceConfigDraft(long userId, String projectId, String json) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET resource_config_draft_json = CAST(? AS JSON), row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ? AND stage IN ('RESOURCE_CONFIG_REQUIRED', 'READY')
                """, json, userId, projectId);
    }

    public void confirmResourceConfig(long userId, String projectId) {
        jdbc.update("""
                UPDATE smart_learning_project
                   SET resource_config_json = resource_config_draft_json,
                       resource_config_version = resource_config_version + 1,
                       stage = 'READY', row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ?
                   AND stage IN ('RESOURCE_CONFIG_REQUIRED', 'READY')
                   AND resource_config_draft_json IS NOT NULL
                """, userId, projectId);
    }

    /** Create the executable task/resource rows for a confirmed plan.  The
     * unique project/version/source key makes this safe to call repeatedly
     * after refreshes or a retried confirmation. */
    public void provisionTasks(long userId, String projectId, int planVersion,
                               List<Map<String, Object>> planTasks) {
        int order = 0;
        for (Map<String, Object> planTask : planTasks) {
            String sourceTaskId = String.valueOf(planTask.getOrDefault("id", "task-" + order));
            String taskExternalId = crypto.newExternalId();
            String type = inferredTaskType(planTask, order);
            String title = textValue(planTask.getOrDefault("title", "学习任务"));
            String description = textValue(planTask.getOrDefault("reason", "按确认的学习计划完成本任务。"));
            String completionCriteria = textValue(planTask.getOrDefault("completionCriteria", "完成本任务并提交结果。"));
            String scheduled = textValue(planTask.get("date"));
            Integer duration = intValue(planTask.get("durationMinutes"), 30);
            String initialStatus = scheduled.isBlank() || !LocalDate.parse(scheduled).isAfter(LocalDate.now())
                    ? "AVAILABLE" : "PLANNED";
            TaskCarryRecord carry = findTaskCarry(userId, projectId, planVersion, sourceTaskId).orElse(null);
            boolean equivalent = carry != null
                    && type.equals(carry.taskType())
                    && completionCriteria.equals(carry.completionCriteria());
            String effectiveStatus = equivalent ? carry.status() : initialStatus;
            jdbc.update("""
                    INSERT INTO smart_learning_task (
                        external_id, project_external_id, user_id, plan_version, source_task_id,
                        title, task_type, description, completion_criteria, scheduled_date,
                        duration_minutes, status, sort_order, payload_json
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS JSON))
                    ON DUPLICATE KEY UPDATE
                        title = VALUES(title), task_type = VALUES(task_type),
                        description = VALUES(description), completion_criteria = VALUES(completion_criteria),
                        scheduled_date = VALUES(scheduled_date), duration_minutes = VALUES(duration_minutes),
                        payload_json = VALUES(payload_json), updated_at = CURRENT_TIMESTAMP(3)
                    """, taskExternalId, projectId, userId, planVersion, sourceTaskId,
                    title, type, description, completionCriteria,
                    scheduled.isBlank() ? null : LocalDate.parse(scheduled),
                    Math.max(15, Math.min(180, duration)), effectiveStatus, order,
                    writeJson(planTask));
            String actualTaskId = jdbc.queryForObject("""
                    SELECT external_id FROM smart_learning_task
                     WHERE project_external_id = ? AND plan_version = ? AND source_task_id = ?
                    """, String.class, projectId, planVersion, sourceTaskId);
            String resourceKind = "EXERCISE".equals(type) ? "EXERCISE_SET" : "READING";
            jdbc.update("""
                    INSERT IGNORE INTO smart_learning_resource (
                        external_id, project_external_id, task_external_id, user_id, kind, title, status
                    ) VALUES (?, ?, ?, ?, ?, ?, 'QUEUED')
                    """, crypto.newExternalId(), projectId, actualTaskId, userId, resourceKind,
                    title);
            if (equivalent) {
                carryExecution(userId, projectId, carry.taskExternalId(), actualTaskId);
                carryResource(userId, carry.taskExternalId(), actualTaskId, resourceKind);
            }
            order++;
        }
    }

    private Optional<TaskCarryRecord> findTaskCarry(
            long userId, String projectId, int planVersion, String sourceTaskId) {
        return jdbc.query("""
                SELECT external_id, task_type, completion_criteria, status
                  FROM smart_learning_task
                 WHERE user_id = ? AND project_external_id = ?
                   AND plan_version < ? AND source_task_id = ? AND status <> 'CANCELLED'
                 ORDER BY plan_version DESC, id DESC
                 LIMIT 1
                """, (rs, rowNum) -> new TaskCarryRecord(
                rs.getString("external_id"), rs.getString("task_type"),
                rs.getString("completion_criteria"), rs.getString("status")),
                userId, projectId, planVersion, sourceTaskId).stream().findFirst();
    }

    private void carryExecution(long userId, String projectId, String previousTaskId, String taskId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM smart_learning_execution
                 WHERE user_id = ? AND task_external_id = ?
                """, Integer.class, userId, taskId);
        if (count != null && count > 0) return;
        jdbc.update("""
                INSERT INTO smart_learning_execution (
                    external_id, project_external_id, task_external_id, user_id, status,
                    progress, accumulated_seconds, position_json, answers_json, question_snapshot_json,
                    score, grading_json, last_heartbeat_seq, started_at, paused_at, completed_at
                )
                SELECT ?, ?, ?, user_id, status, progress, accumulated_seconds,
                       position_json, answers_json, question_snapshot_json, score, grading_json, last_heartbeat_seq,
                       started_at, paused_at, completed_at
                  FROM smart_learning_execution
                 WHERE user_id = ? AND task_external_id = ?
                 ORDER BY updated_at DESC, id DESC
                 LIMIT 1
                """, crypto.newExternalId(), projectId, taskId, userId, previousTaskId);
    }

    private void carryResource(long userId, String previousTaskId, String taskId, String kind) {
        jdbc.update("""
                UPDATE smart_learning_resource current_resource
                  JOIN smart_learning_resource previous_resource
                    ON previous_resource.user_id = current_resource.user_id
                   AND previous_resource.task_external_id = ?
                   AND previous_resource.kind = current_resource.kind
                   SET current_resource.status = previous_resource.status,
                       current_resource.content_json = previous_resource.content_json,
                       current_resource.error_message = previous_resource.error_message,
                       current_resource.updated_at = CURRENT_TIMESTAMP(3)
                 WHERE current_resource.user_id = ? AND current_resource.task_external_id = ?
                   AND current_resource.kind = ? AND previous_resource.status = 'READY'
                """, previousTaskId, userId, taskId, kind);
    }

    public Optional<TutorThreadRecord> findTutorThread(
            long userId, String projectId, String contextKey) {
        return jdbc.query("""
                SELECT external_id, conversation_external_id, project_external_id,
                       task_external_id, context_key
                  FROM smart_learning_tutor_thread
                 WHERE user_id = ? AND project_external_id = ? AND context_key = ?
                   AND status = 'ACTIVE'
                """, (rs, rowNum) -> new TutorThreadRecord(
                rs.getString("external_id"), rs.getString("conversation_external_id"),
                rs.getString("project_external_id"), rs.getString("task_external_id"),
                rs.getString("context_key")), userId, projectId, contextKey).stream().findFirst();
    }

    public TutorThreadRecord linkTutorThread(
            long userId, String projectId, String taskId, String contextKey,
            String conversationId) {
        jdbc.update("""
                INSERT INTO smart_learning_tutor_thread (
                    external_id, project_external_id, task_external_id, user_id,
                    context_key, conversation_external_id, status
                ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE')
                ON DUPLICATE KEY UPDATE status = 'ACTIVE', updated_at = CURRENT_TIMESTAMP(3)
                """, crypto.newExternalId(), projectId, taskId, userId, contextKey, conversationId);
        return findTutorThread(userId, projectId, contextKey).orElseThrow();
    }

    public void upsertWrongItem(
            long userId, String projectId, String taskId, String executionId,
            String questionId, String stem, String userAnswer, String correctAnswer,
            String explanation, String knowledgeKey) {
        int updated = jdbc.update("""
                UPDATE smart_learning_wrong_item
                   SET execution_external_id = ?, stem = ?, user_answer = ?,
                       correct_answer = ?, explanation = ?, knowledge_key = ?,
                       status = 'TO_REVIEW', updated_at = CURRENT_TIMESTAMP(3)
                 WHERE user_id = ? AND project_external_id = ?
                   AND task_external_id = ? AND question_id = ?
                """, executionId, stem, userAnswer, correctAnswer, explanation,
                knowledgeKey, userId, projectId, taskId, questionId);
        if (updated > 0) return;
        jdbc.update("""
                INSERT INTO smart_learning_wrong_item (
                    external_id, project_external_id, task_external_id,
                    execution_external_id, user_id, question_id, stem,
                    user_answer, correct_answer, explanation, knowledge_key, status
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'TO_REVIEW')
                ON DUPLICATE KEY UPDATE
                    stem = VALUES(stem), user_answer = VALUES(user_answer),
                    correct_answer = VALUES(correct_answer), explanation = VALUES(explanation),
                    knowledge_key = VALUES(knowledge_key), status = 'TO_REVIEW',
                    updated_at = CURRENT_TIMESTAMP(3)
                """, crypto.newExternalId(), projectId, taskId, executionId, userId,
                questionId, stem, userAnswer, correctAnswer, explanation, knowledgeKey);
    }

    public void markWrongItemMastered(
            long userId, String projectId, String taskId, String questionId) {
        jdbc.update("""
                UPDATE smart_learning_wrong_item
                   SET status = 'MASTERED', updated_at = CURRENT_TIMESTAMP(3)
                 WHERE user_id = ? AND project_external_id = ?
                   AND task_external_id = ? AND question_id = ?
                """, userId, projectId, taskId, questionId);
    }

    public List<WrongItemRecord> findWrongItems(long userId, String projectId) {
        return jdbc.query("""
                SELECT external_id, project_external_id, task_external_id, question_id,
                       stem, user_answer, correct_answer, explanation, knowledge_key,
                       status, updated_at
                  FROM smart_learning_wrong_item
                 WHERE user_id = ? AND project_external_id = ?
                 ORDER BY (status = 'TO_REVIEW') DESC, updated_at DESC, id DESC
                """, (rs, rowNum) -> new WrongItemRecord(
                rs.getString("external_id"), rs.getString("project_external_id"),
                rs.getString("task_external_id"), rs.getString("question_id"),
                rs.getString("stem"), rs.getString("user_answer"),
                rs.getString("correct_answer"), rs.getString("explanation"),
                rs.getString("knowledge_key"), rs.getString("status"),
                rs.getObject("updated_at", LocalDateTime.class)), userId, projectId);
    }

    public Optional<WrongItemRecord> findWrongItem(long userId, String projectId, String wrongItemId) {
        return jdbc.query("""
                SELECT external_id, project_external_id, task_external_id, question_id,
                       stem, user_answer, correct_answer, explanation, knowledge_key,
                       status, updated_at
                  FROM smart_learning_wrong_item
                 WHERE user_id = ? AND project_external_id = ? AND external_id = ?
                """, (rs, rowNum) -> new WrongItemRecord(
                rs.getString("external_id"), rs.getString("project_external_id"),
                rs.getString("task_external_id"), rs.getString("question_id"),
                rs.getString("stem"), rs.getString("user_answer"),
                rs.getString("correct_answer"), rs.getString("explanation"),
                rs.getString("knowledge_key"), rs.getString("status"),
                rs.getObject("updated_at", LocalDateTime.class)), userId, projectId, wrongItemId)
                .stream().findFirst();
    }

    public void reviewWrongItem(
            long userId, String projectId, String wrongItemId, String answer, boolean correct) {
        int updated = jdbc.update("""
                UPDATE smart_learning_wrong_item
                   SET user_answer = ?, status = ?, updated_at = CURRENT_TIMESTAMP(3)
                 WHERE user_id = ? AND project_external_id = ? AND external_id = ?
                """, answer, correct ? "MASTERED" : "TO_REVIEW", userId, projectId, wrongItemId);
        if (updated == 0) throw new IllegalArgumentException("错题不存在或无权访问。");
    }

    public List<TaskRecord> findTasks(long userId, String projectId) {
        return jdbc.query("""
                SELECT task.external_id, task.project_external_id, task.plan_version, task.source_task_id, task.title,
                       task.task_type, task.description, task.completion_criteria, task.scheduled_date,
                       task.duration_minutes, task.status, task.sort_order, task.payload_json, task.updated_at
                  FROM smart_learning_task task
                  JOIN smart_learning_project project
                    ON project.external_id = task.project_external_id
                   AND project.user_id = task.user_id
                 WHERE task.user_id = ? AND task.project_external_id = ?
                   AND task.plan_version = project.plan_version AND task.status <> 'CANCELLED'
                 ORDER BY task.sort_order ASC, task.id ASC
                """, this::mapTask, userId, projectId);
    }

    public Optional<TaskRecord> findTask(long userId, String projectId, String taskId) {
        return jdbc.query("""
                SELECT task.external_id, task.project_external_id, task.plan_version, task.source_task_id, task.title,
                       task.task_type, task.description, task.completion_criteria, task.scheduled_date,
                       task.duration_minutes, task.status, task.sort_order, task.payload_json, task.updated_at
                  FROM smart_learning_task task
                  JOIN smart_learning_project project
                    ON project.external_id = task.project_external_id
                   AND project.user_id = task.user_id
                 WHERE task.user_id = ? AND task.project_external_id = ? AND task.external_id = ?
                   AND task.plan_version = project.plan_version
                """, this::mapTask, userId, projectId, taskId).stream().findFirst();
    }

    public List<ResourceRecord> findResources(long userId, String projectId) {
        return jdbc.query("""
                SELECT resource.external_id, resource.task_external_id, resource.kind, resource.title,
                       resource.status, resource.generation_stage, resource.generation_progress,
                       resource.content_json, resource.error_message, resource.updated_at
                  FROM smart_learning_resource resource
                  JOIN smart_learning_task task ON task.external_id = resource.task_external_id
                  JOIN smart_learning_project project
                    ON project.external_id = task.project_external_id
                   AND project.user_id = task.user_id
                 WHERE resource.user_id = ? AND resource.project_external_id = ?
                   AND task.plan_version = project.plan_version
                 ORDER BY resource.id ASC
                """, this::mapResource, userId, projectId);
    }

    public List<ResourceRecord> findResourcesForTask(long userId, String projectId, String taskId) {
        return jdbc.query("""
                SELECT resource.external_id, resource.task_external_id, resource.kind, resource.title,
                       resource.status, resource.generation_stage, resource.generation_progress,
                       resource.content_json, resource.error_message, resource.updated_at
                  FROM smart_learning_resource resource
                  JOIN smart_learning_task task ON task.external_id = resource.task_external_id
                  JOIN smart_learning_project project
                    ON project.external_id = task.project_external_id
                   AND project.user_id = task.user_id
                 WHERE resource.user_id = ? AND resource.project_external_id = ?
                   AND resource.task_external_id = ? AND task.plan_version = project.plan_version
                 ORDER BY resource.id ASC
                """, this::mapResource, userId, projectId, taskId);
    }

    public void markResourceGenerating(long userId, String resourceId) {
        jdbc.update("""
                UPDATE smart_learning_resource SET status = 'GENERATING', generation_stage = 'PREPARING_SOURCE',
                       generation_progress = 0, error_message = NULL
                       WHERE user_id = ? AND external_id = ?""", userId, resourceId);
    }

    public void markResourceReady(long userId, String resourceId, String contentJson) {
        jdbc.update("""
                UPDATE smart_learning_resource SET status = 'READY', generation_stage = 'READY',
                       generation_progress = 100, content_json = CAST(? AS JSON), error_message = NULL
                       WHERE user_id = ? AND external_id = ?""", contentJson, userId, resourceId);
    }

    public void markResourceFailed(long userId, String resourceId, String message) {
        jdbc.update("""
                UPDATE smart_learning_resource SET status = 'FAILED', generation_stage = 'FAILED', error_message = ?
                       WHERE user_id = ? AND external_id = ?""", trimError(message), userId, resourceId);
    }

    public List<ResourceRecord> findQueuedResources(long userId, String projectId) {
        return jdbc.query("""
                SELECT resource.external_id, resource.task_external_id, resource.kind, resource.title,
                       resource.status, resource.generation_stage, resource.generation_progress,
                       resource.content_json, resource.error_message, resource.updated_at
                  FROM smart_learning_resource resource
                  JOIN smart_learning_task task ON task.external_id = resource.task_external_id
                  JOIN smart_learning_project project
                    ON project.external_id = task.project_external_id
                   AND project.user_id = task.user_id
                 WHERE resource.user_id = ? AND resource.project_external_id = ?
                   AND task.plan_version = project.plan_version
                   AND resource.status = 'QUEUED'
                 ORDER BY resource.id ASC
                """, this::mapResource, userId, projectId);
    }

    public Optional<ResourceRecord> findResource(long userId, String projectId, String resourceId) {
        return jdbc.query("""
                SELECT resource.external_id, resource.task_external_id, resource.kind, resource.title,
                       resource.status, resource.generation_stage, resource.generation_progress,
                       resource.content_json, resource.error_message, resource.updated_at
                  FROM smart_learning_resource resource
                  JOIN smart_learning_task task ON task.external_id = resource.task_external_id
                  JOIN smart_learning_project project
                    ON project.external_id = task.project_external_id
                   AND project.user_id = task.user_id
                 WHERE resource.user_id = ? AND resource.project_external_id = ?
                   AND resource.external_id = ? AND task.plan_version = project.plan_version
                """, this::mapResource, userId, projectId, resourceId).stream().findFirst();
    }

    public boolean markResourceGenerating(long userId, String projectId, String resourceId, String expectedStatus) {
        return jdbc.update("""
                UPDATE smart_learning_resource
                   SET status = 'GENERATING', generation_stage = 'PREPARING_SOURCE',
                       generation_progress = 0, error_message = NULL
                 WHERE user_id = ? AND project_external_id = ? AND external_id = ? AND status = ?
                """, userId, projectId, resourceId, expectedStatus) > 0;
    }

    public void updateResourceGenerationProgress(
            long userId, String projectId, String resourceId, String stage, int progress) {
        jdbc.update("""
                UPDATE smart_learning_resource
                   SET generation_stage = ?, generation_progress = ?
                 WHERE user_id = ? AND project_external_id = ? AND external_id = ?
                   AND status = 'GENERATING'
                """, stage, Math.max(0, Math.min(99, progress)), userId, projectId, resourceId);
    }

    public Optional<ExecutionRecord> findActiveExecution(long userId, String projectId, String taskId) {
        return jdbc.query("""
                SELECT external_id, project_external_id, task_external_id, status, progress,
                       accumulated_seconds, position_json, answers_json, question_snapshot_json, score, grading_json,
                       last_heartbeat_seq, started_at, paused_at, completed_at, updated_at
                  FROM smart_learning_execution
                 WHERE user_id = ? AND project_external_id = ? AND task_external_id = ?
                   AND status IN ('IN_PROGRESS', 'PAUSED', 'COMPLETION_PENDING')
                 ORDER BY id DESC LIMIT 1
                """, this::mapExecution, userId, projectId, taskId).stream().findFirst();
    }

    public Optional<ExecutionRecord> findLatestExecution(long userId, String projectId, String taskId) {
        return jdbc.query("""
                SELECT external_id, project_external_id, task_external_id, status, progress,
                       accumulated_seconds, position_json, answers_json, question_snapshot_json, score, grading_json,
                       last_heartbeat_seq, started_at, paused_at, completed_at, updated_at
                  FROM smart_learning_execution
                 WHERE user_id = ? AND project_external_id = ? AND task_external_id = ?
                 ORDER BY updated_at DESC, id DESC LIMIT 1
                """, this::mapExecution, userId, projectId, taskId).stream().findFirst();
    }

    public Optional<ExecutionRecord> findLatestCompletedExecution(long userId, String projectId, String taskId) {
        return jdbc.query("""
                SELECT external_id, project_external_id, task_external_id, status, progress,
                       accumulated_seconds, position_json, answers_json, question_snapshot_json, score, grading_json,
                       last_heartbeat_seq, started_at, paused_at, completed_at, updated_at
                  FROM smart_learning_execution
                 WHERE user_id = ? AND project_external_id = ? AND task_external_id = ?
                   AND status = 'COMPLETED'
                 ORDER BY completed_at DESC, id DESC LIMIT 1
                """, this::mapExecution, userId, projectId, taskId).stream().findFirst();
    }

    public Optional<ExecutionRecord> findExecution(long userId, String executionId) {
        return jdbc.query("""
                SELECT external_id, project_external_id, task_external_id, status, progress,
                       accumulated_seconds, position_json, answers_json, question_snapshot_json, score, grading_json,
                       last_heartbeat_seq, started_at, paused_at, completed_at, updated_at
                  FROM smart_learning_execution
                 WHERE user_id = ? AND external_id = ?
                """, this::mapExecution, userId, executionId).stream().findFirst();
    }

    public String createExecution(long userId, String projectId, String taskId, String questionSnapshotJson) {
        String id = crypto.newExternalId();
        jdbc.update("""
                INSERT INTO smart_learning_execution (
                    external_id, project_external_id, task_external_id, user_id, status, question_snapshot_json
                ) VALUES (?, ?, ?, ?, 'IN_PROGRESS', CAST(? AS JSON))
                """, id, projectId, taskId, userId, questionSnapshotJson);
        jdbc.update("""
                UPDATE smart_learning_task SET status = 'IN_PROGRESS'
                       WHERE user_id = ? AND project_external_id = ? AND external_id = ?
                         AND status IN ('PLANNED', 'AVAILABLE', 'PAUSED', 'IN_PROGRESS')""", userId, projectId, taskId);
        return id;
    }

    public String createExecution(long userId, String projectId, String taskId) {
        return createExecution(userId, projectId, taskId, null);
    }

    public void updateExecutionStatus(long userId, String executionId, String status) {
        jdbc.update("""
                UPDATE smart_learning_execution SET status = ?,
                       paused_at = CASE WHEN ? = 'PAUSED' THEN CURRENT_TIMESTAMP(3) ELSE paused_at END,
                       completed_at = CASE WHEN ? IN ('COMPLETED','SKIPPED') THEN CURRENT_TIMESTAMP(3) ELSE completed_at END,
                       row_version = row_version + 1 WHERE user_id = ? AND external_id = ?""",
                status, status, status, userId, executionId);
        jdbc.update("""
                UPDATE smart_learning_task t JOIN smart_learning_execution e ON e.task_external_id = t.external_id
                       SET t.status = ? WHERE e.user_id = ? AND e.external_id = ?""", status, userId, executionId);
    }

    public void updateExecutionProgress(long userId, String executionId, double progress, int secondsDelta) {
        jdbc.update("""
                UPDATE smart_learning_execution SET progress = ?, accumulated_seconds = accumulated_seconds + ?,
                       row_version = row_version + 1 WHERE user_id = ? AND external_id = ?""",
                Math.max(0, Math.min(100, progress)), Math.max(0, Math.min(300, secondsDelta)), userId, executionId);
    }

    public void updateExecutionScore(long userId, String executionId, double score) {
        jdbc.update("""
                UPDATE smart_learning_execution SET score = ?, progress = 100, row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ?
                """, Math.max(0, Math.min(100, score)), userId, executionId);
    }

    public void saveExecutionPosition(long userId, String executionId, String json) {
        jdbc.update("""
                UPDATE smart_learning_execution SET position_json = CAST(? AS JSON), row_version = row_version + 1
                       WHERE user_id = ? AND external_id = ?""", json, userId, executionId);
    }

    public void saveExecutionAnswers(long userId, String executionId, String json) {
        jdbc.update("""
                UPDATE smart_learning_execution SET answers_json = CAST(? AS JSON), row_version = row_version + 1
                       WHERE user_id = ? AND external_id = ?""", json, userId, executionId);
    }

    public boolean heartbeat(long userId, String executionId, long sequence, int secondsDelta) {
        return jdbc.update("""
                UPDATE smart_learning_execution
                       SET last_heartbeat_seq = ?, accumulated_seconds = accumulated_seconds + ?, updated_at = CURRENT_TIMESTAMP(3)
                       WHERE user_id = ? AND external_id = ? AND status = 'IN_PROGRESS' AND last_heartbeat_seq < ?""",
                sequence, Math.max(0, Math.min(60, secondsDelta)), userId, executionId, sequence) > 0;
    }

    public void updateJobGenerationStage(String jobId, String stage) {
        jdbc.update("""
                UPDATE smart_learning_job SET result_json = JSON_OBJECT('generationStage', ?),
                       updated_at = CURRENT_TIMESTAMP(3)
                 WHERE external_id = ? AND status = 'RUNNING'""", stage, jobId);
    }

    public void updateJobProgress(String jobId, int current, int total) {
        jdbc.update("""
                UPDATE smart_learning_job SET progress_current = GREATEST(progress_current, ?),
                       progress_total = GREATEST(progress_total, ?), updated_at = CURRENT_TIMESTAMP(3)
                       WHERE external_id = ? AND status = 'RUNNING'""", Math.max(0, current), Math.max(1, total), jobId);
    }

    public Optional<JobRecord> findReusableJob(
            long userId,
            String projectId,
            String kind,
            String fingerprint) {
        List<JobRecord> rows = jdbc.query("""
                SELECT external_id, project_external_id, kind, input_fingerprint,
                       status, progress_current, progress_total, result_json,
                       safe_error_message, started_at, finished_at, created_at, updated_at
                  FROM smart_learning_job
                 WHERE user_id = ? AND project_external_id = ? AND kind = ?
                   AND input_fingerprint = ?
                   AND status IN ('QUEUED', 'RUNNING', 'SUCCEEDED')
                 ORDER BY created_at DESC, id DESC
                 LIMIT 1
                """, this::mapJob, userId, projectId, kind, fingerprint);
        return rows.stream().findFirst();
    }

    public String createJob(long userId, String projectId, String kind, String fingerprint) {
        String externalId = crypto.newExternalId();
        jdbc.update("""
                INSERT INTO smart_learning_job (
                    external_id, project_external_id, user_id, kind, input_fingerprint,
                    status, progress_current, progress_total
                ) VALUES (?, ?, ?, ?, ?, 'QUEUED', 0, 1)
                """, externalId, projectId, userId, kind, fingerprint);
        return externalId;
    }

    public Optional<JobRecord> findJob(long userId, String jobId) {
        List<JobRecord> rows = jdbc.query("""
                SELECT external_id, project_external_id, kind, input_fingerprint,
                       status, progress_current, progress_total, result_json,
                       safe_error_message, started_at, finished_at, created_at, updated_at
                  FROM smart_learning_job
                 WHERE user_id = ? AND external_id = ?
                """, this::mapJob, userId, jobId);
        return rows.stream().findFirst();
    }

    public Optional<JobRecord> findLatestJob(long userId, String projectId) {
        List<JobRecord> rows = jdbc.query("""
                SELECT external_id, project_external_id, kind, input_fingerprint,
                       status, progress_current, progress_total, result_json,
                       safe_error_message, started_at, finished_at, created_at, updated_at
                  FROM smart_learning_job
                 WHERE user_id = ? AND project_external_id = ?
                 ORDER BY created_at DESC, id DESC
                 LIMIT 1
                """, this::mapJob, userId, projectId);
        return rows.stream().findFirst();
    }

    public void markJobRunning(String jobId, LocalDateTime now) {
        jdbc.update("""
                UPDATE smart_learning_job
                   SET status = 'RUNNING', progress_current = 0, started_at = ?, updated_at = ?
                 WHERE external_id = ? AND status = 'QUEUED'
                """, now, now, jobId);
    }

    public void markJobSucceeded(String jobId, String resultJson, LocalDateTime now) {
        jdbc.update("""
                UPDATE smart_learning_job
                   SET status = 'SUCCEEDED', progress_current = progress_total,
                       result_json = CAST(? AS JSON), finished_at = ?, updated_at = ?
                 WHERE external_id = ? AND status = 'RUNNING'
                """, resultJson, now, now, jobId);
    }

    public void markJobFailed(String jobId, String message, LocalDateTime now) {
        jdbc.update("""
                UPDATE smart_learning_job
                   SET status = 'FAILED', safe_error_message = ?, finished_at = ?, updated_at = ?
                 WHERE external_id = ? AND status IN ('QUEUED', 'RUNNING')
                """, trimError(message), now, now, jobId);
    }

    public List<ChunkRecord> findSourceChunks(long userId, Map<String, Object> sources) {
        List<String> assetIds = sourceAssetIds(sources);
        if (assetIds.isEmpty()) return List.of();
        List<String> versionIds = sourceVersionIds(sources);
        String assetPlaceholders = String.join(",", Collections.nCopies(assetIds.size(), "?"));
        String versionFilter = versionIds.isEmpty() ? "" : " AND av.external_id IN (" + String.join(",", Collections.nCopies(versionIds.size(), "?")) + ")";
        String versionJoin = versionIds.isEmpty()
                ? "JOIN asset_version av ON av.id = COALESCE(a.current_version_id, (SELECT candidate.id FROM asset_version candidate WHERE candidate.asset_id = a.id AND candidate.status = 'READY' ORDER BY candidate.version_no DESC LIMIT 1))"
                : "JOIN asset_version av ON av.asset_id = a.id";
        String sql = """
                SELECT a.external_id AS asset_external_id, a.name AS asset_name,
                       av.external_id AS version_external_id,
                       c.external_id AS chunk_external_id, c.content,
                       c.page_from, c.page_to, c.heading_path
                  FROM asset a
                  %s
                  JOIN asset_parse_result pr
                    ON pr.id = av.active_parse_result_id AND pr.status = 'READY'
                  JOIN document_chunk c ON c.parse_result_id = pr.id
                 WHERE a.user_id = ? AND a.status = 'ACTIVE'
                   AND av.status = 'READY' AND a.external_id IN (%s)%s
                 ORDER BY a.id ASC, c.sequence_no ASC
                 LIMIT 801
                """.formatted(versionJoin, assetPlaceholders, versionFilter);
        List<Object> args = new ArrayList<>();
        args.add(userId);
        args.addAll(assetIds);
        args.addAll(versionIds);
        List<ChunkRecord> chunks = jdbc.query(sql, (rs, rowNum) -> new ChunkRecord(
                rs.getString("asset_external_id"),
                rs.getString("asset_name"),
                rs.getString("version_external_id"),
                rs.getString("chunk_external_id"),
                rs.getString("content"),
                rs.getObject("page_from", Integer.class),
                rs.getObject("page_to", Integer.class),
                rs.getString("heading_path")), args.toArray());
        if (chunks.size() > 800) {
            throw new IllegalStateException("所选资料内容过大，请拆分资料后再分析学习范围。");
        }
        return chunks;
    }

    private List<String> sourceAssetIds(Map<String, Object> sources) {
        Object raw = sources == null ? null : sources.get("assets");
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object value : list) {
            if (value instanceof String text && !text.isBlank()) result.add(text.trim());
            if (value instanceof Map<?, ?> map && map.get("assetId") != null) {
                result.add(String.valueOf(map.get("assetId")));
            }
        }
        return result.stream().distinct().limit(40).toList();
    }

    private List<String> sourceVersionIds(Map<String, Object> sources) {
        Object raw = sources == null ? null : sources.get("assets");
        if (!(raw instanceof List<?> list)) return List.of();
        List<String> result = new ArrayList<>();
        for (Object value : list) {
            if (value instanceof Map<?, ?> map && map.get("versionId") != null) {
                String version = String.valueOf(map.get("versionId"));
                if (!version.isBlank()) result.add(version.trim());
            }
        }
        return result.stream().distinct().limit(40).toList();
    }

    private ProjectRecord mapProject(ResultSet rs, int rowNum) throws SQLException {
        return new ProjectRecord(
                rs.getString("external_id"), rs.getLong("user_id"), rs.getString("name"),
                rs.getString("icon"), rs.getString("icon_color"),
                rs.getString("knowledge_base_external_id"), rs.getString("stage"),
                rs.getInt("target_version"), rs.getInt("source_version"), rs.getInt("scope_version"),
                rs.getInt("diagnosis_version"), rs.getInt("plan_version"),
                rs.getInt("resource_config_version"), rs.getObject("pinned_at", LocalDateTime.class),
                rs.getObject("updated_at", LocalDateTime.class),
                readMap(rs.getString("target_json")), readMap(rs.getString("target_draft_json")),
                readMap(rs.getString("sources_json")), readMap(rs.getString("sources_draft_json")),
                readMap(rs.getString("scope_json")), readMap(rs.getString("scope_candidate_json")),
                readMap(rs.getString("diagnosis_json")), readMap(rs.getString("diagnosis_candidate_json")),
                readMap(rs.getString("diagnosis_answers_draft_json")),
                readMap(rs.getString("plan_json")), readMap(rs.getString("plan_candidate_json")),
                readMap(rs.getString("resource_config_json")), readMap(rs.getString("resource_config_draft_json")));
    }

    private TaskRecord mapTask(ResultSet rs, int rowNum) throws SQLException {
        return new TaskRecord(
                rs.getString("external_id"), rs.getString("project_external_id"),
                rs.getInt("plan_version"), rs.getString("source_task_id"), rs.getString("title"),
                rs.getString("task_type"), rs.getString("description"), rs.getString("completion_criteria"),
                rs.getObject("scheduled_date", LocalDate.class), rs.getInt("duration_minutes"),
                rs.getString("status"), rs.getInt("sort_order"), readMap(rs.getString("payload_json")),
                rs.getObject("updated_at", LocalDateTime.class));
    }

    private ResourceRecord mapResource(ResultSet rs, int rowNum) throws SQLException {
        return new ResourceRecord(
                rs.getString("external_id"), rs.getString("task_external_id"), rs.getString("kind"),
                rs.getString("title"), rs.getString("status"), rs.getString("generation_stage"),
                rs.getInt("generation_progress"), readMap(rs.getString("content_json")),
                rs.getString("error_message"), rs.getObject("updated_at", LocalDateTime.class));
    }

    private ExecutionRecord mapExecution(ResultSet rs, int rowNum) throws SQLException {
        return new ExecutionRecord(
                rs.getString("external_id"), rs.getString("project_external_id"), rs.getString("task_external_id"),
                rs.getString("status"), rs.getDouble("progress"), rs.getInt("accumulated_seconds"),
                readMap(rs.getString("position_json")), readMap(rs.getString("answers_json")),
                readMap(rs.getString("question_snapshot_json")), rs.getObject("score", Double.class),
                readMap(rs.getString("grading_json")), rs.getLong("last_heartbeat_seq"),
                rs.getObject("started_at", LocalDateTime.class), rs.getObject("paused_at", LocalDateTime.class),
                rs.getObject("completed_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class));
    }

    private String taskType(String raw) {
        return switch (raw == null ? "" : raw.trim().toLowerCase()) {
            case "练习", "exercise", "quiz", "测验" -> "EXERCISE";
            case "复盘", "review" -> "REVIEW";
            case "讲解", "explanation" -> "EXPLANATION";
            default -> "READING";
        };
    }

    public void saveExecutionQuestionSnapshot(long userId, String executionId, String json) {
        jdbc.update("""
                UPDATE smart_learning_execution
                 SET question_snapshot_json = CAST(? AS JSON), row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ?
                   AND (question_snapshot_json IS NULL OR JSON_LENGTH(question_snapshot_json) = 0)
                """, json, userId, executionId);
    }

    public void saveExecutionGrading(long userId, String executionId, double score, String gradingJson) {
        jdbc.update("""
                UPDATE smart_learning_execution
                   SET score = ?, progress = 100, grading_json = CAST(? AS JSON), row_version = row_version + 1
                 WHERE user_id = ? AND external_id = ?
                """, Math.max(0, Math.min(100, score)), gradingJson, userId, executionId);
    }

    private String inferredTaskType(Map<String, Object> task, int order) {
        String explicit = textValue(task.get("type"));
        if (!explicit.isBlank()) return taskType(explicit);
        String searchable = (textValue(task.get("title")) + " "
                + textValue(task.get("reason")) + " "
                + textValue(task.get("completionCriteria"))).toLowerCase();
        if (searchable.matches(".*(练习|测验|习题|quiz|exercise|作答).*")) return "EXERCISE";
        if (searchable.matches(".*(复盘|复习|回顾|review|错题).*")) return "REVIEW";
        if (searchable.matches(".*(讲解|理解|解析|explanation|原理).*")) return "EXPLANATION";
        // Old confirmed plans did not have a type field. A stable pattern gives
        // those projects a useful mix without asking the user to redo setup.
        return switch (Math.floorMod(order, 4)) {
            case 1 -> "EXPLANATION";
            case 2 -> "EXERCISE";
            case 3 -> "REVIEW";
            default -> "READING";
        };
    }

    private String textValue(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try { return Integer.parseInt(textValue(value)); } catch (Exception ignored) { return fallback; }
    }

    private JobRecord mapJob(ResultSet rs, int rowNum) throws SQLException {
        return new JobRecord(
                rs.getString("external_id"), rs.getString("project_external_id"), rs.getString("kind"),
                rs.getString("input_fingerprint"), rs.getString("status"),
                rs.getInt("progress_current"), rs.getInt("progress_total"),
                readMap(rs.getString("result_json")), rs.getString("safe_error_message"),
                rs.getObject("started_at", LocalDateTime.class), rs.getObject("finished_at", LocalDateTime.class),
                rs.getObject("created_at", LocalDateTime.class), rs.getObject("updated_at", LocalDateTime.class));
    }

    private Map<String, Object> readMap(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception exception) {
            return Map.of("raw", json);
        }
    }

    private String trimError(String message) {
        if (message == null || message.isBlank()) return "任务失败，请稍后重试。";
        String normalized = message.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    public String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new IllegalStateException("无法保存学习状态", exception);
        }
    }

    public record ProjectRecord(
            String externalId, long userId, String name, String icon, String iconColor,
            String knowledgeBaseExternalId, String stage,
            int targetVersion, int sourceVersion, int scopeVersion, int diagnosisVersion,
            int planVersion, int resourceConfigVersion, LocalDateTime pinnedAt, LocalDateTime updatedAt,
            Map<String, Object> target, Map<String, Object> targetDraft,
            Map<String, Object> sources, Map<String, Object> sourcesDraft,
            Map<String, Object> scope, Map<String, Object> scopeCandidate,
            Map<String, Object> diagnosis, Map<String, Object> diagnosisCandidate,
            Map<String, Object> diagnosisAnswersDraft,
            Map<String, Object> plan, Map<String, Object> planCandidate,
            Map<String, Object> resourceConfig, Map<String, Object> resourceConfigDraft) {
    }

    public record JobRecord(
            String externalId, String projectExternalId, String kind, String inputFingerprint,
            String status, int progressCurrent, int progressTotal, Map<String, Object> result,
            String errorMessage, LocalDateTime startedAt, LocalDateTime finishedAt,
            LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record ChunkRecord(
            String assetExternalId, String assetName, String versionExternalId,
            String chunkExternalId, String content, Integer pageFrom, Integer pageTo,
            String headingPath) {
    }

    public record TaskRecord(
            String externalId, String projectExternalId, int planVersion, String sourceTaskId,
            String title, String taskType, String description, String completionCriteria,
            LocalDate scheduledDate, int durationMinutes, String status, int sortOrder,
            Map<String, Object> payload, LocalDateTime updatedAt) {
    }

    private record TaskCarryRecord(
            String taskExternalId, String taskType, String completionCriteria, String status) {
    }

    public record ResourceRecord(
            String externalId, String taskExternalId, String kind, String title, String status,
            String generationStage, int generationProgress, Map<String, Object> content,
            String errorMessage, LocalDateTime updatedAt) {

        public ResourceRecord(
                String externalId, String taskExternalId, String kind, String title, String status,
                Map<String, Object> content, String errorMessage, LocalDateTime updatedAt) {
            this(externalId, taskExternalId, kind, title, status,
                    "READY".equals(status) ? "READY" : "FAILED".equals(status) ? "FAILED" : status,
                    "READY".equals(status) ? 100 : 0, content, errorMessage, updatedAt);
        }
    }

    public record ExecutionRecord(
            String externalId, String projectExternalId, String taskExternalId, String status,
            double progress, int accumulatedSeconds, Map<String, Object> position,
            Map<String, Object> answers, Map<String, Object> questionSnapshot,
            Double score, Map<String, Object> grading, long lastHeartbeatSeq,
            LocalDateTime startedAt, LocalDateTime pausedAt, LocalDateTime completedAt,
            LocalDateTime updatedAt) {

        public ExecutionRecord(
                String externalId, String projectExternalId, String taskExternalId, String status,
                double progress, int accumulatedSeconds, Map<String, Object> position,
                Map<String, Object> answers, Double score, long lastHeartbeatSeq,
                LocalDateTime startedAt, LocalDateTime pausedAt, LocalDateTime completedAt,
                LocalDateTime updatedAt) {
            this(externalId, projectExternalId, taskExternalId, status, progress, accumulatedSeconds,
                    position, answers, Map.of(), score, Map.of(), lastHeartbeatSeq,
                    startedAt, pausedAt, completedAt, updatedAt);
        }
    }

    public record TutorThreadRecord(
            String externalId, String conversationExternalId, String projectExternalId,
            String taskExternalId, String contextKey) {
    }

    public record TutorSidebarRecord(
            String projectExternalId, String conversationExternalId, String title,
            String taskExternalId, String taskTitle, String contextType,
            LocalDateTime updatedAt) {
    }

    public record WrongItemRecord(
            String externalId, String projectExternalId, String taskExternalId,
            String questionId, String stem, String userAnswer, String correctAnswer,
            String explanation, String knowledgeKey, String status,
            LocalDateTime updatedAt) {
    }
}
