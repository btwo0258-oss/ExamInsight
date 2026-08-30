package com.example.llm.learning.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Small, explicit schemas shared by provider constraints and local validation. */
public final class LearningOutputSchemas {
    private LearningOutputSchemas() { }

    public record Contract(String name, Map<String, Object> schema) { }

    public static Contract scope() {
        return new Contract("learning_scope", object("nodes", array(object(
                "id", string(), "title", string(), "parentId", nullableString(),
                "priority", string(), "reason", string(),
                "evidence", array(object("assetId", string(), "locator", string()))))));
    }

    public static Contract diagnosis() {
        return new Contract("learning_diagnosis", object("questions", array(object(
                "id", string(), "conceptId", string(), "type", enumeration("single_choice"),
                "stem", string(), "options", array(string()), "answer", string(), "explanation", string()))));
    }

    public static Contract plan() {
        return new Contract("learning_plan", object("tasks", array(object(
                "id", string(), "type", enumeration("READING", "EXPLANATION", "EXERCISE", "REVIEW"),
                "title", string(), "conceptIds", array(string()), "reason", string(),
                "durationMinutes", Map.of("type", "integer"), "completionCriteria", string(),
                "date", nullableString(), "dependencies", array(string())))));
    }

    public static Contract exercises() {
        return new Contract("learning_exercises", object("items", array(object(
                "id", string(), "stem", string(), "options", array(string()), "answer", string(),
                "explanation", string(), "knowledgeKey", string()))));
    }

    private static Map<String, Object> string() { return Map.of("type", "string"); }
    private static Map<String, Object> nullableString() { return Map.of("type", List.of("string", "null")); }
    private static Map<String, Object> array(Map<String, Object> items) { return Map.of("type", "array", "items", items); }
    private static Map<String, Object> enumeration(String... values) { return Map.of("type", "string", "enum", List.of(values)); }

    private static Map<String, Object> object(Object... fields) {
        Map<String, Object> properties = new LinkedHashMap<>();
        for (int i = 0; i < fields.length; i += 2) properties.put((String) fields[i], fields[i + 1]);
        return Map.of("type", "object", "properties", properties,
                "required", List.copyOf(properties.keySet()), "additionalProperties", false);
    }
}
