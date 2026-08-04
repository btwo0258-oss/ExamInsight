package com.example.llm.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.llm.common.Result;
import com.example.llm.entity.User;
import com.example.llm.entity.UserSettings;
import com.example.llm.mapper.ConversationMapper;
import com.example.llm.mapper.DocumentMapper;
import com.example.llm.mapper.KnowledgeBaseMapper;
import com.example.llm.mapper.MindMapMapper;
import com.example.llm.mapper.UserMapper;
import com.example.llm.mapper.UserSettingsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ConversationMapper conversationMapper;

    @Autowired
    private KnowledgeBaseMapper knowledgeBaseMapper;

    @Autowired
    private MindMapMapper mindMapMapper;

    @Autowired
    private DocumentMapper documentMapper;

    @Autowired
    private UserSettingsMapper userSettingsMapper;

    @GetMapping
    public Result<List<Map<String, Object>>> getAllUsers() {
        QueryWrapper<User> userQueryWrapper = new QueryWrapper<>();
        userQueryWrapper.eq("role", "user");
        List<User> users = userMapper.selectList(userQueryWrapper);
        List<Map<String, Object>> result = users.stream().map(user -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            map.put("nickname", user.getNickname());
            map.put("avatar", user.getAvatar());
            map.put("status", user.getStatus() != null && user.getStatus() == 0 ? "normal" : "banned");
            map.put("registerTime", user.getCreateTime());
            map.put("lastLogin", user.getLastLoginTime());

            // stats
            Map<String, Long> stats = new HashMap<>();
            stats.put("convCount", conversationMapper.selectCount(new QueryWrapper<com.example.llm.entity.Conversation>().eq("user_id", user.getId())));
            
            QueryWrapper<com.example.llm.entity.KnowledgeBase> kbQuery = new QueryWrapper<>();
            kbQuery.eq("user_id", user.getId());
            kbQuery.eq("status", 0);
            stats.put("kbCount", knowledgeBaseMapper.selectCount(kbQuery));
            
            stats.put("mindMapCount", mindMapMapper.selectCount(new QueryWrapper<com.example.llm.entity.MindMap>().eq("user_id", user.getId())));
            stats.put("fileCount", documentMapper.selectCount(new QueryWrapper<com.example.llm.entity.Document>().eq("user_id", user.getId())));
            
            map.put("stats", stats);

            return map;
        }).collect(Collectors.toList());

        return Result.success(result);
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getUserById(@PathVariable Long id) {
        User user = userMapper.selectById(id);
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        Map<String, Object> map = new HashMap<>();
        map.put("id", user.getId());
        map.put("username", user.getUsername());
        map.put("nickname", user.getNickname());
        map.put("avatar", user.getAvatar());
        map.put("status", user.getStatus() != null && user.getStatus() == 0 ? "normal" : "banned");
        map.put("registerTime", user.getCreateTime());
        map.put("lastLogin", user.getLastLoginTime());

        // stats
        Map<String, Long> stats = new HashMap<>();
        stats.put("convCount", conversationMapper.selectCount(new QueryWrapper<com.example.llm.entity.Conversation>().eq("user_id", user.getId())));
        
        QueryWrapper<com.example.llm.entity.KnowledgeBase> kbQuery = new QueryWrapper<>();
        kbQuery.eq("user_id", user.getId());
        kbQuery.eq("status", 0);
        stats.put("kbCount", knowledgeBaseMapper.selectCount(kbQuery));
        
        stats.put("mindMapCount", mindMapMapper.selectCount(new QueryWrapper<com.example.llm.entity.MindMap>().eq("user_id", user.getId())));
        stats.put("fileCount", documentMapper.selectCount(new QueryWrapper<com.example.llm.entity.Document>().eq("user_id", user.getId())));
        
        map.put("stats", stats);

        // preferences
        UserSettings settings = userSettingsMapper.selectOne(new QueryWrapper<UserSettings>().eq("user_id", user.getId()));
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("theme", settings != null && settings.getTheme() != null ? settings.getTheme() : "system");
        prefs.put("defaultModel", settings != null && settings.getDefaultModel() != null ? settings.getDefaultModel() : "deepseek-chat");
        map.put("preferences", prefs);
        map.put("settings", prefs);

        return Result.success(map);
    }

    @PutMapping("/{id}/status")
    public Result<Map<String, Boolean>> updateUserStatus(@PathVariable Long id, @RequestBody Map<String, String> payload) {
        User user = userMapper.selectById(id);
        if (user != null) {
            String status = payload.get("status");
            user.setStatus("normal".equals(status) ? 0 : 1);
            userMapper.updateById(user);
        }
        return Result.success(Map.of("success", true));
    }

}
