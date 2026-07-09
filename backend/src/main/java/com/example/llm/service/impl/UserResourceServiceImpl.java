package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.entity.Resource;
import com.example.llm.entity.UserResource;
import com.example.llm.mapper.UserResourceMapper;
import com.example.llm.service.DocumentService;
import com.example.llm.service.ResourceService;
import com.example.llm.service.UserResourceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

@Service
public class UserResourceServiceImpl extends ServiceImpl<UserResourceMapper, UserResource> implements UserResourceService {

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private DocumentService documentService;

    @Override
    public void addToKb(Long userId, Long resourceId, Long kbId) {
        if (existsByUserAndResource(userId, resourceId)) {
            throw new RuntimeException("该资料已添加到知识库");
        }
        
        Resource resource = resourceService.getResourceDetail(resourceId);
        if (resource == null) {
            throw new RuntimeException("资料不存在");
        }

        File resourceFile = new File(resource.getFilePath());
        if (!resourceFile.exists()) {
            throw new RuntimeException("资料文件不存在");
        }

        UserResource ur = new UserResource();
        ur.setUserId(userId);
        ur.setResourceId(resourceId);
        ur.setKbId(kbId);
        this.save(ur);

        try {
            byte[] fileBytes = Files.readAllBytes(resourceFile.toPath());
            MultipartFile multipartFile = new SimpleMultipartFile(
                resource.getFileName(),
                resource.getFileName(),
                "application/octet-stream",
                fileBytes
            );
            documentService.uploadDocument(userId, kbId, multipartFile);
        } catch (IOException e) {
            throw new RuntimeException("上传资料到知识库失败: " + e.getMessage());
        }
    }

    @Override
    public void moveToKb(Long userId, Long resourceId, Long newKbId) {
        UserResource existing = getByUserAndResource(userId, resourceId);
        if (existing == null) {
            throw new RuntimeException("该资料未添加到任何知识库");
        }

        Long oldKbId = existing.getKbId();
        if (oldKbId != null && oldKbId.equals(newKbId)) {
            throw new RuntimeException("资料已在目标知识库中");
        }

        Resource resource = resourceService.getResourceDetail(resourceId);
        if (resource == null) {
            throw new RuntimeException("资料不存在");
        }

        File resourceFile = new File(resource.getFilePath());
        if (!resourceFile.exists()) {
            throw new RuntimeException("资料文件不存在");
        }

        existing.setKbId(newKbId);
        this.updateById(existing);

        try {
            byte[] fileBytes = Files.readAllBytes(resourceFile.toPath());
            MultipartFile multipartFile = new SimpleMultipartFile(
                resource.getFileName(),
                resource.getFileName(),
                "application/octet-stream",
                fileBytes
            );
            documentService.uploadDocument(userId, newKbId, multipartFile);
        } catch (IOException e) {
            throw new RuntimeException("移动资料到知识库失败: " + e.getMessage());
        }
    }

    @Override
    public UserResource getByUserAndResource(Long userId, Long resourceId) {
        LambdaQueryWrapper<UserResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserResource::getUserId, userId);
        wrapper.eq(UserResource::getResourceId, resourceId);
        return this.getOne(wrapper);
    }

    private static class SimpleMultipartFile implements MultipartFile {
        private final String name;
        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        public SimpleMultipartFile(String name, String originalFilename, String contentType, byte[] content) {
            this.name = name;
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() { return name; }

        @Override
        public String getOriginalFilename() { return originalFilename; }

        @Override
        public String getContentType() { return contentType; }

        @Override
        public boolean isEmpty() { return content.length == 0; }

        @Override
        public long getSize() { return content.length; }

        @Override
        public byte[] getBytes() throws IOException { return content; }

        @Override
        public java.io.InputStream getInputStream() { return new ByteArrayInputStream(content); }

        @Override
        public void transferTo(File dest) throws IOException, IllegalStateException {
            Files.write(dest.toPath(), content);
        }
    }

    @Override
    public void removeFromKb(Long userId, Long resourceId) {
        LambdaQueryWrapper<UserResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserResource::getUserId, userId);
        wrapper.eq(UserResource::getResourceId, resourceId);
        this.remove(wrapper);
    }

    @Override
    public List<UserResource> listByUser(Long userId) {
        LambdaQueryWrapper<UserResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserResource::getUserId, userId);
        return this.list(wrapper);
    }

    @Override
    public boolean existsByUserAndResource(Long userId, Long resourceId) {
        LambdaQueryWrapper<UserResource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserResource::getUserId, userId);
        wrapper.eq(UserResource::getResourceId, resourceId);
        return this.count(wrapper) > 0;
    }
}
