package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.dto.MindMapCreateReq;
import com.example.llm.dto.MindMapUpdateReq;
import com.example.llm.entity.MindMap;
import com.example.llm.service.MindMapService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/mindmap")
public class MindMapController {

    @Autowired
    private MindMapService mindMapService;

    @PostMapping("/create")
    public Result<Long> createMindMap(@RequestBody MindMapCreateReq req) {
        Long userId = UserContext.getUserId();
        Long id = mindMapService.createMindMap(req, userId);
        return Result.success(id);
    }

    @PostMapping("/update")
    public Result<Void> updateMindMap(@RequestBody MindMapUpdateReq req) {
        Long userId = UserContext.getUserId();
        mindMapService.updateMindMap(req, userId);
        return Result.success(null);
    }

    @PostMapping("/delete/{id}")
    public Result<Void> deleteMindMap(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        mindMapService.deleteMindMap(id, userId);
        return Result.success(null);
    }

    @GetMapping("/list")
    public Result<List<MindMap>> listMindMaps(@RequestParam(required = false) Long kbId) {
        Long userId = UserContext.getUserId();
        List<MindMap> list = mindMapService.listMindMaps(kbId, userId);
        return Result.success(list);
    }

    @GetMapping("/detail/{id}")
    public Result<MindMap> getMindMapDetail(@PathVariable Long id) {
        Long userId = UserContext.getUserId();
        MindMap detail = mindMapService.getMindMapDetail(id, userId);
        return Result.success(detail);
    }
}
