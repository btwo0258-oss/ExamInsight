package com.example.llm.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.llm.dto.UserSettingsUpdateReq;
import com.example.llm.entity.UserSettings;
import com.example.llm.mapper.UserSettingsMapper;
import com.example.llm.service.UserSettingsService;
import com.example.llm.vo.UserSettingsVO;
import org.springframework.stereotype.Service;

@Service
public class UserSettingsServiceImpl extends ServiceImpl<UserSettingsMapper, UserSettings> implements UserSettingsService {

    @Override
    public UserSettingsVO getUserSettings(Long userId) {
        UserSettings settings = this.getOne(new LambdaQueryWrapper<UserSettings>().eq(UserSettings::getUserId, userId));
        if (settings == null) {
            settings = new UserSettings();
            settings.setUserId(userId);
            settings.setTheme("light");
            settings.setDefaultModel("qwen-plus-2025-07-28");
            settings.setCreateTime(java.time.LocalDateTime.now());
            settings.setUpdateTime(java.time.LocalDateTime.now());
            this.save(settings);
        }
        UserSettingsVO vo = new UserSettingsVO();
        vo.setTheme(settings.getTheme());
        vo.setDefaultModel(settings.getDefaultModel());
        return vo;
    }

    @Override
    public void updateUserSettings(Long userId, UserSettingsUpdateReq req) {
        UserSettings settings = this.getOne(new LambdaQueryWrapper<UserSettings>().eq(UserSettings::getUserId, userId));
        if (settings == null) {
            settings = new UserSettings();
            settings.setUserId(userId);
            settings.setTheme(req.getTheme() != null ? req.getTheme() : "system");
            settings.setDefaultModel(req.getDefaultModel() != null ? req.getDefaultModel() : "qwen-plus-2025-07-28");
            settings.setCreateTime(java.time.LocalDateTime.now());
            settings.setUpdateTime(java.time.LocalDateTime.now());
            this.save(settings);
        } else {
            boolean needUpdate = false;
            if (req.getTheme() != null && !req.getTheme().trim().isEmpty()) {
                settings.setTheme(req.getTheme());
                needUpdate = true;
            }
            if (req.getDefaultModel() != null && !req.getDefaultModel().trim().isEmpty()) {
                settings.setDefaultModel(req.getDefaultModel());
                needUpdate = true;
            }
            if (needUpdate) {
                settings.setUpdateTime(java.time.LocalDateTime.now());
                this.updateById(settings);
            }
        }
    }
}
