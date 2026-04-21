package com.example.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.llm.entity.UserResource;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserResourceMapper extends BaseMapper<UserResource> {
}
