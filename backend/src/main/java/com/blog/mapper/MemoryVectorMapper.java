package com.blog.mapper;

import com.blog.entity.MemoryEmbedding;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 向量语义检索 Mapper
 * 直接 SQL 取回候选向量，在 Java 侧做余弦相似度计算（MySQL 8 也可用 JSON_TABLE，
 * 但数据量小时纯 Java 计算更简单可控、且与 userId 隔离天然兼容）。
 */
public interface MemoryVectorMapper {

    /**
     * 拉取某用户的全部向量（按 memory_id 去重，最多取最近 N 条）
     */
    @Select("SELECT id, user_id, memory_id, vector_key, text_snippet, vector_json " +
            "FROM memory_embedding " +
            "WHERE user_id = #{userId} " +
            "ORDER BY id DESC LIMIT #{limit}")
    List<MemoryEmbedding> selectByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
