package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.entity.LearningResource;
import com.example.llm.service.LearningResourceService;
import com.example.llm.vo.LearningResourceVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/learning/resources")
public class LearningResourceController {
    
    @Autowired
    private LearningResourceService learningResourceService;
    
    @GetMapping("/plan/{planId}")
    public Result<List<LearningResourceVO>> getResourcesByPlanId(@PathVariable Long planId) {
        List<LearningResource> resources = learningResourceService.getResourcesByPlanId(planId);
        List<LearningResourceVO> voList = resources.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }
    
    @GetMapping("/plan/{planId}/group/{groupType}")
    public Result<List<LearningResourceVO>> getResourcesByPlanIdAndGroupType(@PathVariable Long planId, @PathVariable String groupType) {
        List<LearningResource> resources = learningResourceService.getResourcesByPlanIdAndGroupType(planId, groupType);
        List<LearningResourceVO> voList = resources.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }
    
    @PostMapping
    public Result<LearningResourceVO> createResource(@RequestBody Map<String, String> request) {
        Long planId = Long.valueOf(request.get("planId"));
        String groupType = request.get("groupType");
        String title = request.get("title");
        String description = request.get("description");
        String action = request.get("action");
        LearningResource resource = learningResourceService.createResource(planId, groupType, title, description, action);
        return Result.success(convertToVO(resource));
    }
    
    @PutMapping("/{resourceId}")
    public Result<Void> updateResource(@PathVariable Long resourceId, @RequestBody Map<String, String> request) {
        String title = request.get("title");
        String description = request.get("description");
        String action = request.get("action");
        learningResourceService.updateResource(resourceId, title, description, action);
        return Result.success(null);
    }
    
    @DeleteMapping("/{resourceId}")
    public Result<Void> deleteResource(@PathVariable Long resourceId) {
        learningResourceService.deleteResource(resourceId);
        return Result.success(null);
    }
    
    private LearningResourceVO convertToVO(LearningResource resource) {
        LearningResourceVO vo = new LearningResourceVO();
        BeanUtils.copyProperties(resource, vo);
        return vo;
    }
}
