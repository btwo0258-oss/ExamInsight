package com.example.llm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LearningProjectControllerTest {

    @Test
    void normalizesLegacyTaskModesAndLinksExercises() {
        LearningProjectController controller = new LearningProjectController(null, null, new ObjectMapper());
        Map<String, Object> lecture = task(1, "讲解", "content");
        Map<String, Object> practice = task(2, "练习", "quiz");
        Map<String, Object> material = task(3, "资料", "content");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("stages", List.of(Map.of("tasks", new ArrayList<>(List.of(lecture, practice, material)))));
        payload.put("exercises", List.of(Map.of("id", 11), Map.of("id", 12)));
        payload.put("resources", List.of(Map.of("id", 7, "group", "学习方案")));

        controller.normalizeTaskContracts(payload);

        assertEquals("exercise", practice.get("completionMode"));
        assertEquals(List.of(11L, 12L), practice.get("exerciseIds"));
        assertEquals("resource", material.get("completionMode"));
        assertEquals(7L, material.get("learningResourceId"));
    }

    private Map<String, Object> task(long id, String type, String completionMode) {
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("id", id);
        task.put("type", type);
        task.put("completionMode", completionMode);
        return task;
    }
}
