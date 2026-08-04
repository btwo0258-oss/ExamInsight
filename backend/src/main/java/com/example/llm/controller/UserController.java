package com.example.llm.controller;

import com.example.llm.common.Result;
import com.example.llm.common.UserContext;
import com.example.llm.dto.UserSettingsUpdateReq;
import com.example.llm.dto.UserUpdateReq;
import com.example.llm.service.UserService;
import com.example.llm.service.UserSettingsService;
import com.example.llm.vo.UserInfoVO;
import com.example.llm.vo.UserSettingsVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
public class UserController {

    @Autowired
    private UserService userService;

    @Autowired
    private UserSettingsService userSettingsService;

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

}
