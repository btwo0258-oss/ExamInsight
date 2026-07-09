package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.dto.ResourceCreateReq;
import com.example.llm.entity.Resource;

import java.util.List;

public interface ResourceService extends IService<Resource> {
    List<Resource> listResources(String category, Integer year);
    Resource getResourceDetail(Long id);
    Long createResource(ResourceCreateReq req);
    void deleteResource(Long id);
    void incrementDownloadCount(Long id);
}
