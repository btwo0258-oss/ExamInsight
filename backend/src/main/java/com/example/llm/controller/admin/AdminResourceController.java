package com.example.llm.controller.admin;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.common.Result;
import com.example.llm.dto.ResourceCreateReq;
import com.example.llm.entity.Resource;
import com.example.llm.service.ResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/resource")
public class AdminResourceController {

    @Autowired
    private ResourceService resourceService;

    @Value("${upload.path}")
    private String uploadPath;

    @GetMapping("/list")
    public Result<List<Resource>> listResources(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer year) {
        List<Resource> list = resourceService.listResources(category, year);
        return Result.success(list);
    }

    @PostMapping("/upload")
    public Result<Long> uploadResource(
            @RequestParam("title") String title,
            @RequestParam("category") String category,
            @RequestParam("year") Integer year,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam("file") MultipartFile file) throws IOException {

        if (file.isEmpty()) {
            return Result.error(400, "文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        String fileType = getFileExtension(originalFilename);
        if (!isValidFileType(fileType)) {
            return Result.error(400, "不支持的文件类型，仅支持 pdf/docx/doc/txt");
        }

        File uploadDir = new File(new File(uploadPath).getAbsoluteFile(), "resources");
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }

        String savedFileName = UUID.randomUUID().toString().replace("-", "") + "." + fileType;
        File destFile = new File(uploadDir, savedFileName).getAbsoluteFile();
        file.transferTo(destFile);

        Resource resource = new Resource();
        resource.setTitle(title);
        resource.setCategory(category);
        resource.setYear(year);
        resource.setDescription(description);
        resource.setFileName(originalFilename);
        resource.setFileType(fileType);
        resource.setFileSize(file.getSize());
        resource.setFilePath(destFile.getAbsolutePath());
        resource.setDownloadCount(0);
        resource.setStatus(0);
        resourceService.save(resource);

        return Result.success(resource.getId());
    }

    @PutMapping("/{id}")
    public Result<Void> updateResource(@PathVariable Long id, @RequestBody ResourceCreateReq req) {
        Resource resource = resourceService.getById(id);
        if (resource == null || resource.getStatus() != 0) {
            return Result.error(404, "资料不存在");
        }
        if (req.getTitle() != null) resource.setTitle(req.getTitle());
        if (req.getCategory() != null) resource.setCategory(req.getCategory());
        if (req.getYear() != null) resource.setYear(req.getYear());
        if (req.getDescription() != null) resource.setDescription(req.getDescription());
        resourceService.updateById(resource);
        return Result.success(null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteResource(@PathVariable Long id) {
        resourceService.deleteResource(id);
        return Result.success(null);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".") + 1).toLowerCase();
    }

    private boolean isValidFileType(String fileType) {
        return "pdf".equals(fileType) || "docx".equals(fileType) || "doc".equals(fileType) || "txt".equals(fileType);
    }
}
