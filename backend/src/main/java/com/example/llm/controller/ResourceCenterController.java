package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.dto.AddToKbReq;
import com.example.llm.entity.Resource;
import com.example.llm.entity.UserResource;
import com.example.llm.service.ResourceService;
import com.example.llm.service.UserResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
    public Result<List<Resource>> listResources(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer year) {
        List<Resource> list = resourceService.listResources(category, year);
        return Result.success(list);
    }

    @GetMapping("/{id}")
    public Result<Resource> getResourceDetail(@PathVariable Long id) {
        Resource resource = resourceService.getResourceDetail(id);
        return Result.success(resource);
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

        String encodedFileName = URLEncoder.encode(resource.getFileName(), StandardCharsets.UTF_8)
                .replaceAll("\\+", "%20");

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + encodedFileName + "\"; filename*=UTF-8''" + encodedFileName)
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
    public Result<List<UserResource>> myResources() {
        Long userId = UserContext.getUserId();
        List<UserResource> list = userResourceService.listByUser(userId);
        return Result.success(list);
    }

    @GetMapping("/is-added")
    public Result<Boolean> isAdded(@RequestParam Long resourceId) {
        Long userId = UserContext.getUserId();
        boolean added = userResourceService.existsByUserAndResource(userId, resourceId);
        return Result.success(added);
    }
}
