package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.dto.ConversationCreateReq;
import com.example.llm.dto.ConversationUpdateReq;
import com.example.llm.service.ConversationService;
import com.example.llm.vo.ConversationDto;
import com.example.llm.vo.ConversationListVO;
import com.example.llm.vo.MessageVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/conversation")
public class ConversationController {

    @Autowired
    private ConversationService conversationService;

    @PostMapping("/create")
    public Result<ConversationDto> create(@RequestBody ConversationCreateReq req) {
        return Result.success("创建成功", conversationService.createConversation(UserContext.getUserId(), req));
    }

    @GetMapping("/list")
    public Result<List<ConversationListVO>> list() {
        return Result.success(conversationService.getConversationList(UserContext.getUserId()));
    }

    @GetMapping("/{id}/messages")
    public Result<List<MessageVO>> getMessages(@PathVariable("id") Long id) {
        return Result.success(conversationService.getConversationMessages(UserContext.getUserId(), id));
    }

    @PutMapping("/{id}")
    public Result<ConversationDto> update(@PathVariable("id") Long id, @RequestBody ConversationUpdateReq req) {
        return Result.success("更新成功", conversationService.updateConversation(UserContext.getUserId(), id, req));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        conversationService.deleteConversation(UserContext.getUserId(), id);
        return Result.success("删除成功", null);
    }
}
