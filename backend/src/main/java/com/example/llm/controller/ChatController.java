package com.example.llm.controller;

import com.example.llm.common.UserContext;
import com.example.llm.dto.ChatReq;
import com.example.llm.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    @Autowired
    private ChatService chatService;

    @PostMapping(value = "/stream", produces = "text/event-stream")
    public SseEmitter chatStream(@Validated @RequestBody ChatReq req) {
        return chatService.chat(UserContext.getUserId(), req);
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
