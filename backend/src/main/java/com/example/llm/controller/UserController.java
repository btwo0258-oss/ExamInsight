package com.example.llm.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.dto.UserLoginReq;
import com.example.llm.dto.UserRegisterReq;
import com.example.llm.dto.UserSettingsUpdateReq;
import com.example.llm.dto.UserUpdateReq;
import com.example.llm.entity.PasswordResetRequest;
import com.example.llm.entity.User;
import com.example.llm.mapper.PasswordResetRequestMapper;
import com.example.llm.mapper.UserMapper;
import com.example.llm.service.UserService;
import com.example.llm.service.UserSettingsService;
import com.example.llm.vo.UserInfoVO;
import com.example.llm.vo.UserLoginVO;
import com.example.llm.vo.UserSettingsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserSettingsService userSettingsService;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private PasswordResetRequestMapper passwordResetRequestMapper;

    @PostMapping("/register")
    public Result<UserLoginVO> register(@Validated @RequestBody UserRegisterReq req) {
        return Result.success("注册成功", userService.register(req));
    }

    @PostMapping("/login")
    public Result<UserLoginVO> login(@Validated @RequestBody UserLoginReq req) {
        return Result.success("登录成功", userService.login(req));
    }

    @GetMapping("/info") //
    public Result<UserInfoVO> getInfo() {
        return Result.success(userService.getUserInfo(UserContext.getUserId()));
    }

    @PutMapping("/update")
    public Result<Void> updateInfo(@RequestBody UserUpdateReq req) {
        userService.updateUserInfo(UserContext.getUserId(), req);
        return Result.success("修改成功", null);
    }

    @GetMapping("/settings")
    public Result<UserSettingsVO> getSettings() {
        return Result.success(userSettingsService.getUserSettings(UserContext.getUserId()));
    }

    @PutMapping("/settings")
    public Result<Void> updateSettings(@RequestBody UserSettingsUpdateReq req) {
        userSettingsService.updateUserSettings(UserContext.getUserId(), req);
        return Result.success("设置已更新", null);
    }

    @PostMapping("/forgot-password")
    public Result<Void> forgotPassword(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        
        // 检查是否已经存在待处理的申请，避免重复提交
        boolean hasExisting = passwordResetRequestMapper.selectCount(
            new LambdaQueryWrapper<PasswordResetRequest>()
                .eq(PasswordResetRequest::getUserId, user.getId())
                .eq(PasswordResetRequest::getStatus, 0)
        ) > 0;
        
        if (!hasExisting) {
            PasswordResetRequest resetRequest = new PasswordResetRequest();
            resetRequest.setUserId(user.getId());
            resetRequest.setUsername(user.getUsername());
            resetRequest.setStatus(0);
            resetRequest.setCreateTime(LocalDateTime.now());
            passwordResetRequestMapper.insert(resetRequest);
        }
        return Result.success("密码重置申请已提交，请等待管理员处理", null);
    }

    @PostMapping("/reset-password")
    public Result<Void> resetPassword(@RequestBody Map<String, String> payload) {
        String username = payload.get("username");
        String verifyCode = payload.get("verifyCode");
        String newPassword = payload.get("newPassword");
        
        if (username == null || verifyCode == null || newPassword == null) {
            return Result.error(400, "参数不完整");
        }
        
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
        if (user == null) {
            return Result.error(404, "用户不存在");
        }
        
        user.setPassword(cn.hutool.crypto.digest.BCrypt.hashpw(newPassword, cn.hutool.crypto.digest.BCrypt.gensalt()));
        userMapper.updateById(user);
        
        return Result.success("密码重置成功", null);
    }
}
