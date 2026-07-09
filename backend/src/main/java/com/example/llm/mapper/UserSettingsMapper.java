package com.example.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.llm.entity.UserSettings;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserSettingsMapper extends BaseMapper<UserSettings> {
}
