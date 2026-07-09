package com.example.llm.service;

import com.example.llm.dto.ChatReq;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface ChatService {
    SseEmitter chat(Long userId, ChatReq req);
    String generateTitle(String text);
}
