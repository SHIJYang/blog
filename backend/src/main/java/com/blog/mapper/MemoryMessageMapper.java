package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.MemoryMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface MemoryMessageMapper extends BaseMapper<MemoryMessage> {

    /**
     * 查询某用户每条记忆的最新一条消息时间（用于自动浓缩判 idle）
     *
     * @param userId 用户ID
     * @return [ { memoryId, latestTime } ]
     */
    @Select("SELECT m.memory_id AS memoryId, MAX(m.create_time) AS latestTime " +
            "FROM memory_message m " +
            "WHERE m.user_id = #{userId} AND m.is_deleted = 0 " +
            "GROUP BY m.memory_id")
    List<MemoryLikeMap> selectLatestTimes(@Param("userId") Long userId);

    /** 用于 selectLatestTimes 返回行结构（避免 Map 键类型歧义） */
    class MemoryLikeMap {
        public Long memoryId;
        public java.sql.Timestamp latestTime;
    }
}
