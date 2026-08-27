package com.example.llm.chatv2.controller;

import com.example.llm.chatv2.api.ChatV2Dtos.ConversationDetail;
import com.example.llm.chatv2.api.ChatV2Dtos.ConversationPage;
import com.example.llm.chatv2.api.ChatV2Dtos.ConversationMessagesPage;
import com.example.llm.chatv2.api.ChatV2Dtos.ConversationSummary;
import com.example.llm.chatv2.api.ChatV2Dtos.CreateConversationRequest;
import com.example.llm.chatv2.api.ChatV2Dtos.EditMessageRequest;
import com.example.llm.chatv2.api.ChatV2Dtos.SendMessageAccepted;
import com.example.llm.chatv2.api.ChatV2Dtos.SendMessageRequest;
import com.example.llm.chatv2.api.ChatV2Dtos.UpdateConversationRequest;
import com.example.llm.chatv2.service.ChatV2ApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PutMapping;

@RestController
@RequestMapping("/api/v2/conversations")
public class ConversationV2Controller {
    private final ChatV2ApplicationService service;

    public ConversationV2Controller(ChatV2ApplicationService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ConversationSummary> create(
            @Valid @RequestBody(required = false) CreateConversationRequest request) {
        CreateConversationRequest normalized = request == null
                ? new CreateConversationRequest(null, null, null)
                : request;
        return ResponseEntity.status(HttpStatus.CREATED).body(service.create(normalized));
    }

    @GetMapping
    public ConversationPage list(
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "30") int limit) {
        return service.list(cursor, limit);
    }

    @GetMapping("/{conversationId}")
    public ConversationDetail detail(@PathVariable String conversationId) {
        return service.get(conversationId);
    }

    @GetMapping("/{conversationId}/summary")
    public ConversationSummary summary(@PathVariable String conversationId) {
        return service.summary(conversationId);
    }

    @GetMapping("/{conversationId}/messages")
    public ConversationMessagesPage messages(
            @PathVariable String conversationId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String targetMessageId,
            @RequestParam(defaultValue = "40") int limit) {
        return service.messages(conversationId, cursor, targetMessageId, limit);
    }

    @PatchMapping("/{conversationId}")
    public ConversationSummary update(
            @PathVariable String conversationId,
            @Valid @RequestBody UpdateConversationRequest request) {
        return service.update(conversationId, request);
    }

    @DeleteMapping("/{conversationId}")
    public ResponseEntity<Void> trash(@PathVariable String conversationId) {
        service.trash(conversationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{conversationId}/messages")
    public ResponseEntity<SendMessageAccepted> send(
            @PathVariable String conversationId,
            @Valid @RequestBody SendMessageRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.accepted().body(service.send(conversationId, request, idempotencyKey));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/edit")
    public ResponseEntity<SendMessageAccepted> edit(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            @Valid @RequestBody EditMessageRequest request,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.accepted().body(
                service.edit(conversationId, messageId, request, idempotencyKey));
    }

    @PostMapping("/{conversationId}/messages/{messageId}/regenerate")
    public ResponseEntity<SendMessageAccepted> regenerate(
            @PathVariable String conversationId,
            @PathVariable String messageId,
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.accepted().body(
                service.regenerate(conversationId, messageId, idempotencyKey));
    }

    @PutMapping("/{conversationId}/branches/{branchId}/active")
    public ConversationDetail activateBranch(
            @PathVariable String conversationId,
            @PathVariable String branchId) {
        return service.activateBranch(conversationId, branchId);
    }
}
