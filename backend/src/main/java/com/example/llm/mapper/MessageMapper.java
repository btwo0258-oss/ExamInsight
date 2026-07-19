package com.example.llm.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.llm.entity.Message;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface MessageMapper extends BaseMapper<Message> {

    @Select("""
            WITH RECURSIVE msg_tree AS (
                SELECT * FROM message WHERE id = #{msgId} AND status = 0
                UNION ALL
                SELECT m.* FROM message m
                INNER JOIN msg_tree t ON m.id = t.parent_id
                WHERE m.status = 0
            )
            SELECT * FROM msg_tree LIMIT #{limit}
            """)
    List<Message> getMessageHistoryRecursive(@Param("msgId") Long msgId, @Param("limit") int limit);
}
