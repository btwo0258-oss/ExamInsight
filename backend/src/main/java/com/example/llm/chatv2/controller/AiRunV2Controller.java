package com.example.llm.chatv2.controller;

import com.example.llm.chatv2.api.ChatV2Dtos.AiRunView;
import com.example.llm.chatv2.service.ChatV2ApplicationService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v2/ai-runs")
public class AiRunV2Controller {
    private final ChatV2ApplicationService service;

    public AiRunV2Controller(ChatV2ApplicationService service) {
        this.service = service;
    }

    @GetMapping("/{runId}")
    public AiRunView status(@PathVariable String runId) {
        return service.getRun(runId);
    }

    @PostMapping("/{runId}/cancel")
    public AiRunView cancel(@PathVariable String runId) {
        return service.cancel(runId);
    }

    @GetMapping(value = "/{runId}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(
            @PathVariable String runId,
            @RequestHeader(name = "Last-Event-ID", required = false) String lastEventId) {
        return service.events(runId, lastEventId);
    }
}
