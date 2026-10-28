package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.Idea;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface IdeaMapper extends BaseMapper<Idea> {

    /** 只写向量列，不碰 update_time（避免水位抖动） */
    @Update("UPDATE idea SET embedding = #{vec}::vector WHERE id = #{id}")
    int updateEmbedding(@Param("id") Long id, @Param("vec") String vec);

    /** 语义近邻（后续关系图谱 / 去重用；当前仅提供能力，不接 UI） */
    @Select("""
            SELECT id, content, state,
                   1 - (embedding <=> #{vec}::vector) AS score
            FROM idea
            WHERE user_id = #{userId}
              AND is_deleted = 0
              AND embedding IS NOT NULL
              AND id <> #{excludeId}
            ORDER BY embedding <=> #{vec}::vector
            LIMIT #{limit}
            """)
    List<java.util.Map<String, Object>> searchSimilar(@Param("userId") Long userId,
                                                      @Param("vec") String vec,
                                                      @Param("excludeId") Long excludeId,
                                                      @Param("limit") int limit);
}
