package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.dto.AddToKbReq;
import com.example.llm.entity.Resource;
import com.example.llm.entity.UserResource;
import com.example.llm.service.ResourceService;
import com.example.llm.service.UserResourceService;
import com.example.llm.vo.ResourceVO;
import com.example.llm.vo.UserResourceVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/resource")
public class ResourceCenterController {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private UserResourceService userResourceService;

    @Value("${upload.path}")
    private String uploadPath;

    @GetMapping("/list")
    public Result<List<ResourceVO>> listResources(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer year) {
        List<Resource> list = resourceService.listResources(category, year);
        List<ResourceVO> voList = list.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }

    @GetMapping("/{id}")
    public Result<ResourceVO> getResourceDetail(@PathVariable Long id) {
        Resource resource = resourceService.getResourceDetail(id);
        return Result.success(convertToVO(resource));
    }

    @GetMapping("/download/{id}")
    public ResponseEntity<org.springframework.core.io.Resource> downloadResource(@PathVariable Long id) throws IOException {
        Resource resource = resourceService.getResourceDetail(id);
        File file = new File(resource.getFilePath());
        if (!file.exists()) {
            File altFile = new File(uploadPath, resource.getFileName());
            if (altFile.exists()) {
                file = altFile;
            } else {
                return ResponseEntity.notFound().build();
            }
        }

        resourceService.incrementDownloadCount(id);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(file.length())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(resource.getFileName(), StandardCharsets.UTF_8)
                        .build()
                        .toString())
                .body(new FileSystemResource(file));
    }

    @PostMapping("/add-to-kb")
    public Result<Void> addToKb(@RequestBody AddToKbReq req) {
        Long userId = UserContext.getUserId();
        userResourceService.addToKb(userId, req.getResourceId(), req.getKbId());
        return Result.success(null);
    }

    @PostMapping("/move-to-kb")
    public Result<Void> moveToKb(@RequestBody AddToKbReq req) {
        Long userId = UserContext.getUserId();
        userResourceService.moveToKb(userId, req.getResourceId(), req.getKbId());
        return Result.success(null);
    }

    @PostMapping("/remove-from-kb")
    public Result<Void> removeFromKb(@RequestBody AddToKbReq req) {
        Long userId = UserContext.getUserId();
        userResourceService.removeFromKb(userId, req.getResourceId());
        return Result.success(null);
    }

    @GetMapping("/my-resources")
    public Result<List<UserResourceVO>> myResources() {
        Long userId = UserContext.getUserId();
        List<UserResource> list = userResourceService.listByUser(userId);
        List<UserResourceVO> voList = list.stream()
                .map(this::convertToUserResourceVO)
                .collect(Collectors.toList());
        return Result.success(voList);
    }

    @GetMapping("/is-added")
    public Result<Boolean> isAdded(@RequestParam Long resourceId) {
        Long userId = UserContext.getUserId();
        boolean added = userResourceService.existsByUserAndResource(userId, resourceId);
        return Result.success(added);
    }

    private ResourceVO convertToVO(Resource resource) {
        ResourceVO vo = new ResourceVO();
        BeanUtils.copyProperties(resource, vo);
        return vo;
    }

    private UserResourceVO convertToUserResourceVO(UserResource userResource) {
        UserResourceVO vo = new UserResourceVO();
        BeanUtils.copyProperties(userResource, vo);
        return vo;
    }
}
