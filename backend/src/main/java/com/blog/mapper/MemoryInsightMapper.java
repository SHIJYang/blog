package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.MemoryInsight;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MemoryInsightMapper extends BaseMapper<MemoryInsight> {

    /**
     * 拉取某用户的全部知识点向量（用于召回，按 memory_id 去重，最多取最近 N 条）
     */
    @Select("SELECT id, user_id, memory_id, content, emotion, vector_json, create_time " +
            "FROM memory_insight " +
            "WHERE user_id = #{userId} AND vector_json IS NOT NULL AND vector_json <> '' " +
            "ORDER BY id DESC LIMIT #{limit}")
    List<MemoryInsight> selectVectorsByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
