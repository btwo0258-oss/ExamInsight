package com.example.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LearningWorkflowControllerTest {

    private final LearningWorkflowController controller = new LearningWorkflowController(
            null, null, null, null, null, null, null, null, new ObjectMapper());

    @Test
    void gradesFullSingleChoiceTextAgainstAnswerKey() {
        Map<String, Object> exercise = Map.of(
                "type", "单选题",
                "options", List.of("A. 直接测量", "B. 多次测量", "C. 改进仪器", "D. 以上均可"));

        assertTrue(controller.isCorrectAnswer(exercise, "D. 以上均可", "D"));
        assertFalse(controller.isCorrectAnswer(exercise, "B. 多次测量", "D"));
    }

    @Test
    void gradesFullMultipleChoiceTextWithoutDependingOnOrder() {
        Map<String, Object> exercise = Map.of(
                "type", "多选题",
                "options", List.of("A. 大小相等", "B. 方向相反", "C. 同一直线", "D. 不同物体"));

        assertTrue(controller.isCorrectAnswer(
                exercise,
                "C. 同一直线||A. 大小相等||B. 方向相反",
                "ABC"));
        assertFalse(controller.isCorrectAnswer(exercise, "A. 大小相等||B. 方向相反", "ABC"));
    }

    @Test
    void gradesJudgmentOptionTextAgainstAnswerKey() {
        Map<String, Object> exercise = Map.of(
                "type", "判断题",
                "options", List.of("A. 对", "B. 错"));

        assertTrue(controller.isCorrectAnswer(exercise, "A. 对", "A"));
    }
}
