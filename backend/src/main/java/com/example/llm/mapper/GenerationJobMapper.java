package com.example.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.llm.entity.GenerationJob;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface GenerationJobMapper extends BaseMapper<GenerationJob> {
}
