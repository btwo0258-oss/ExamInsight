package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.entity.User;
import com.example.llm.dto.UserLoginReq;
import com.example.llm.dto.UserRegisterReq;
import com.example.llm.dto.UserUpdateReq;
import com.example.llm.vo.UserInfoVO;
import com.example.llm.vo.UserLoginVO;

public interface UserService extends IService<User> {
    UserLoginVO register(UserRegisterReq req);
    UserLoginVO login(UserLoginReq req);
    UserInfoVO getUserInfo(Long userId);
    void updateUserInfo(Long userId, UserUpdateReq req);
}
