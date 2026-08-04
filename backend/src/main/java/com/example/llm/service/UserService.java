package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.entity.User;
import com.example.llm.dto.UserUpdateReq;
import com.example.llm.vo.UserInfoVO;

public interface UserService extends IService<User> {
    UserInfoVO getUserInfo(Long userId);
    void updateUserInfo(Long userId, UserUpdateReq req);
}
