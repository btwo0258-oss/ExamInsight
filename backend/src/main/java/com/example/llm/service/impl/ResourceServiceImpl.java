package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.dto.ResourceCreateReq;
import com.example.llm.entity.Resource;
import com.example.llm.mapper.ResourceMapper;
import com.example.llm.service.ResourceService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ResourceServiceImpl extends ServiceImpl<ResourceMapper, Resource> implements ResourceService {

    @Override
    public List<Resource> listResources(String category, Integer year) {
        LambdaQueryWrapper<Resource> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Resource::getStatus, 0);
        if (category != null && !category.isEmpty()) {
            wrapper.eq(Resource::getCategory, category);
        }
        if (year != null) {
            wrapper.eq(Resource::getYear, year);
        }
        wrapper.orderByDesc(Resource::getYear).orderByDesc(Resource::getCreateTime);
        return this.list(wrapper);
    }

    @Override
    public Resource getResourceDetail(Long id) {
        Resource resource = this.getById(id);
        if (resource == null || resource.getStatus() != 0) {
            throw new RuntimeException("资料不存在");
        }
        return resource;
    }

    @Override
    public Long createResource(ResourceCreateReq req) {
        Resource resource = new Resource();
        resource.setTitle(req.getTitle());
        resource.setCategory(req.getCategory());
        resource.setYear(req.getYear());
        resource.setDescription(req.getDescription());
        resource.setStatus(0);
        resource.setDownloadCount(0);
        this.save(resource);
        return resource.getId();
    }

    @Override
    public void deleteResource(Long id) {
        Resource resource = this.getById(id);
        if (resource == null) {
            throw new RuntimeException("资料不存在");
        }
        resource.setStatus(1);
        this.updateById(resource);
    }

    @Override
    public void incrementDownloadCount(Long id) {
        Resource resource = this.getById(id);
        if (resource != null) {
            resource.setDownloadCount(resource.getDownloadCount() + 1);
            this.updateById(resource);
        }
    }
}
