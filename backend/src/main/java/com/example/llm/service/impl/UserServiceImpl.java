package com.example.llm.service.impl;

import cn.hutool.crypto.digest.BCrypt;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.dto.UserLoginReq;
import com.example.llm.dto.UserRegisterReq;
import com.example.llm.dto.UserUpdateReq;
import com.example.llm.entity.User;
import com.example.llm.entity.UserSettings;
import com.example.llm.mapper.UserMapper;
import com.example.llm.mapper.UserSettingsMapper;
import com.example.llm.service.UserService;
import com.example.llm.vo.UserInfoVO;
import com.example.llm.vo.UserLoginVO;
import com.example.llm.utils.JwtUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Autowired
    private UserSettingsMapper userSettingsMapper;

    @Autowired
    private JwtUtils jwtUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserLoginVO register(UserRegisterReq req) {
        User exists = this.getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (exists != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        User user = new User();
        user.setUsername(req.getUsername());
        user.setPassword(BCrypt.hashpw(req.getPassword(), BCrypt.gensalt()));
        user.setNickname(req.getNickname());
        user.setStatus(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        this.save(user);
// 初始化用户设置
        UserSettings settings = new UserSettings();
        settings.setUserId(user.getId());
        settings.setTheme("light");
        settings.setDefaultModel("qwen-plus-2025-07-28");
        settings.setCreateTime(LocalDateTime.now());
        settings.setUpdateTime(LocalDateTime.now());
        userSettingsMapper.insert(settings);

        UserLoginVO vo = new UserLoginVO();
        BeanUtils.copyProperties(user, vo);
        vo.setToken(jwtUtils.generateToken(user.getId()));
        return vo;
    }

    @Override
    public UserLoginVO login(UserLoginReq req) {
        User user = this.getOne(new LambdaQueryWrapper<User>().eq(User::getUsername, req.getUsername()));
        if (user == null || !BCrypt.checkpw(req.getPassword(), user.getPassword())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (user.getStatus() != null && user.getStatus() == 1) {
            throw new IllegalArgumentException("该账户已被禁用");
        }
        
        // 如果是管理员登录（请求了带isAdmin的参数，或者前端有相关标识），需要校验role
        if (req.getIsAdmin() != null && req.getIsAdmin() && !"admin".equals(user.getRole())) {
            throw new IllegalArgumentException("权限不足，无法登录管理员后台");
        }

        user.setLastLoginTime(LocalDateTime.now());
        this.updateById(user);

        UserLoginVO vo = new UserLoginVO();
        BeanUtils.copyProperties(user, vo);
        vo.setToken(jwtUtils.generateToken(user.getId()));
        return vo;
    }

    @Override
    public UserInfoVO getUserInfo(Long userId) {
        User user = this.getById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        UserInfoVO vo = new UserInfoVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    @Override
    public void updateUserInfo(Long userId, UserUpdateReq req) {
        User user = new User();
        user.setId(userId);
        if (req.getNickname() != null) user.setNickname(req.getNickname());
        if (req.getAvatar() != null) user.setAvatar(req.getAvatar());
        this.updateById(user);
    }
}
