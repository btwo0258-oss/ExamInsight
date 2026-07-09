package com.example.llm.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.example.llm.entity.UserSettings;
import com.example.llm.dto.UserSettingsUpdateReq;
import com.example.llm.vo.UserSettingsVO;

public interface UserSettingsService extends IService<UserSettings> {
    UserSettingsVO getUserSettings(Long userId);
    void updateUserSettings(Long userId, UserSettingsUpdateReq req);
}
