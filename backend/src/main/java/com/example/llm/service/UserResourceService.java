package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.entity.UserResource;

import java.util.List;

public interface UserResourceService extends IService<UserResource> {
    void addToKb(Long userId, Long resourceId, Long kbId);
    void moveToKb(Long userId, Long resourceId, Long newKbId);
    void removeFromKb(Long userId, Long resourceId);
    List<UserResource> listByUser(Long userId);
    boolean existsByUserAndResource(Long userId, Long resourceId);
    UserResource getByUserAndResource(Long userId, Long resourceId);
}
