package com.example.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.llm.entity.Resource;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ResourceMapper extends BaseMapper<Resource> {
}
