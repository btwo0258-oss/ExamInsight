package com.example.llm.controller;

import com.example.llm.common.UserContext;
import com.example.llm.dto.ChatReq;
import com.example.llm.service.ChatService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping(value = "/stream")
    public SseEmitter chatStream(@RequestBody ChatReq req) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event().name("error").data("{\"code\":401,\"message\":\"未登录，请先登录\"}"));
                errorEmitter.complete();
            } catch (IOException e) {
                log.error("Failed to send SSE error", e);
            }
            return errorEmitter;
        }
        try {
            return chatService.chat(userId, req);
        } catch (IllegalArgumentException e) {
            log.warn("Chat parameter error: {}", e.getMessage());
            SseEmitter errorEmitter = new SseEmitter();
            try {
                errorEmitter.send(SseEmitter.event().name("error").data("{\"code\":400,\"message\":\"" + e.getMessage() + "\"}"));
                errorEmitter.complete();
            } catch (IOException ex) {
                log.error("Failed to send SSE error", ex);
            }
            return errorEmitter;
        }
    }

    @PostMapping(value = "/generate-title")
    public String generateTitle(@RequestBody java.util.Map<String, String> req) {
        String text = req.get("text");
        if (text == null || text.trim().isEmpty()) {
            return "新对话";
        }
        return chatService.generateTitle(text);
    }
}
