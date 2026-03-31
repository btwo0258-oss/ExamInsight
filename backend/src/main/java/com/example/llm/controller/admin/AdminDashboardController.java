package com.example.llm.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.llm.common.Result;
import com.example.llm.mapper.ConversationMapper;
import com.example.llm.mapper.DocumentMapper;
import com.example.llm.mapper.KnowledgeBaseMapper;
import com.example.llm.mapper.MindMapMapper;
import com.example.llm.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

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

    @GetMapping("/stats")
    public Result<List<Map<String, Object>>> getStats() {
        QueryWrapper<com.example.llm.entity.User> userQuery = new QueryWrapper<>();
        userQuery.eq("role", "user");
        long totalUsers = userMapper.selectCount(userQuery);
        long totalConvs = conversationMapper.selectCount(null);
        
        QueryWrapper<com.example.llm.entity.KnowledgeBase> kbQuery = new QueryWrapper<>();
        kbQuery.eq("status", 0);
        long totalKbs = knowledgeBaseMapper.selectCount(kbQuery);
        
        long totalMindMaps = mindMapMapper.selectCount(null);

        // Calculate 7-day active users
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        QueryWrapper<com.example.llm.entity.User> activeUserQuery = new QueryWrapper<>();
        activeUserQuery.ge("last_login_time", sevenDaysAgo);
        long activeUsers = userMapper.selectCount(activeUserQuery);

        return Result.success(List.of(
            Map.of("title", "总用户", "value", String.format("%,d", totalUsers), "icon", "users", "color", "#10b981"),
            Map.of("title", "对话数量", "value", String.format("%,d", totalConvs), "icon", "message-square", "color", "#3b82f6"),
            Map.of("title", "知识库数量", "value", String.format("%,d", totalKbs), "icon", "book", "color", "#f59e0b"),
            Map.of("title", "思维导图数量", "value", String.format("%,d", totalMindMaps), "icon", "layers", "color", "#f43f5e"),
            Map.of("title", "用户活跃度", "value", String.format("%,d", activeUsers), "icon", "activity", "color", "#8b5cf6")
        ));
    }

    @GetMapping("/trends")
    public Result<List<Map<String, Object>>> getTrends() {
        // Mock data or actual data for recent registrations
        List<Map<String, Object>> trends = new ArrayList<>();
        // In a real scenario we would group by date in SQL, but for simplicity let's return some basic mock data or query it
        // For simplicity and avoiding complex SQL in mybatis plus, we mock 7 days based on total users if no specific query exists
        for(int i=6; i>=0; i--) {
            LocalDateTime date = LocalDateTime.now().minusDays(i);
            String dateStr = date.getMonthValue() + "-" + date.getDayOfMonth();
            QueryWrapper<com.example.llm.entity.User> q = new QueryWrapper<>();
            q.ge("create_time", date.withHour(0).withMinute(0).withSecond(0));
            q.lt("create_time", date.plusDays(1).withHour(0).withMinute(0).withSecond(0));
            long count = userMapper.selectCount(q);
            
            Map<String, Object> map = new HashMap<>();
            map.put("date", dateStr);
            map.put("value", count);
            trends.add(map);
        }
        return Result.success(trends);
    }

    @GetMapping("/types")
    public Result<List<Map<String, Object>>> getTypeDistribution() {
        // Group by file_type manually
        List<com.example.llm.entity.Document> docs = documentMapper.selectList(null);
        Map<String, Long> typeCounts = new HashMap<>();
        for (com.example.llm.entity.Document doc : docs) {
            String type = doc.getFileType();
            if (type == null) type = "unknown";
            typeCounts.put(type, typeCounts.getOrDefault(type, 0L) + 1);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<String, Long> entry : typeCounts.entrySet()) {
            String type = entry.getKey();
            String color = switch (type.toLowerCase()) {
                case "pdf" -> "var(--color-primary)";
                case "docx" -> "#8b5cf6";
                case "md", "txt" -> "#94a3b8";
                default -> "#cbd5e1";
            };
            Map<String, Object> map = new HashMap<>();
            map.put("type", type.toUpperCase());
            map.put("value", entry.getValue());
            map.put("color", color);
            results.add(map);
        }
        return Result.success(results);
    }
}