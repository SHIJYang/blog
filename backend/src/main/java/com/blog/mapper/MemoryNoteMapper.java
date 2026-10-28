package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.MemoryNote;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Map;

public interface MemoryNoteMapper extends BaseMapper<MemoryNote> {

    /**
     * 只写投影关联，不碰 update_time。
     * <p>
     * 用 MP 的 updateById 会触发 update_time 自动回填，导致 sourceRev 水位永远落后于记忆，
     * 下次同步又判定"记忆更新了"——徒增投影写入。这里显式绕过。
     */
    @Update("UPDATE memory_note SET published_article_id = #{articleId} WHERE id = #{memoryId}")
    int updatePublishedArticleId(@Param("memoryId") Long memoryId,
                                 @Param("articleId") Long articleId);
    /**
     * 公开记忆分页（D14）：仅 privacy=0、已成念、未删除。
     * <p>
     * 用 array_to_string 把 text[] 摊成字符串，避免在注解 SQL 里依赖字段级 TypeHandler。
     */
    @Select("""
            SELECT id,
                   user_id                      AS "userId",
                   COALESCE(auto_title, '')     AS "title",
                   LEFT(COALESCE(final_diary, ''), 300) AS "diary",
                   cover_photo                  AS "coverPhoto",
                   array_to_string(emotions, ',') AS "emotions",
                   COALESCE(finish_time, create_time) AS "finishTime",
                   create_time                  AS "createTime"
            FROM memory_note
            WHERE is_deleted = 0
              AND privacy = 0
              AND status = 3
              AND (#{year,jdbcType=INTEGER} IS NULL
                   OR EXTRACT(YEAR FROM COALESCE(finish_time, create_time)) = #{year,jdbcType=INTEGER})
              AND (#{month,jdbcType=INTEGER} IS NULL
                   OR EXTRACT(MONTH FROM COALESCE(finish_time, create_time)) = #{month,jdbcType=INTEGER})
              AND (#{emotion,jdbcType=VARCHAR} IS NULL OR #{emotion,jdbcType=VARCHAR} = ANY(emotions))
            ORDER BY COALESCE(finish_time, create_time) DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<Map<String, Object>> selectPublicMemories(@Param("year") Integer year,
                                                   @Param("month") Integer month,
                                                   @Param("emotion") String emotion,
                                                   @Param("limit") int limit,
                                                   @Param("offset") int offset);

    @Select("""
            SELECT COUNT(*)
            FROM memory_note
            WHERE is_deleted = 0
              AND privacy = 0
              AND status = 3
              AND (#{year,jdbcType=INTEGER} IS NULL
                   OR EXTRACT(YEAR FROM COALESCE(finish_time, create_time)) = #{year,jdbcType=INTEGER})
              AND (#{month,jdbcType=INTEGER} IS NULL
                   OR EXTRACT(MONTH FROM COALESCE(finish_time, create_time)) = #{month,jdbcType=INTEGER})
              AND (#{emotion,jdbcType=VARCHAR} IS NULL OR #{emotion,jdbcType=VARCHAR} = ANY(emotions))
            """)
    long countPublicMemories(@Param("year") Integer year,
                             @Param("month") Integer month,
                             @Param("emotion") String emotion);

    /** 公开记忆详情：只有 privacy=0 能读到全文 */
    @Select("""
            SELECT id,
                   user_id                      AS "userId",
                   COALESCE(auto_title, '')     AS "title",
                   COALESCE(final_diary, '')    AS "diary",
                   cover_photo                  AS "coverPhoto",
                   array_to_string(emotions, ',') AS "emotions",
                   COALESCE(finish_time, create_time) AS "finishTime",
                   create_time                  AS "createTime"
            FROM memory_note
            WHERE id = #{id}
              AND is_deleted = 0
              AND privacy = 0
            """)
    Map<String, Object> selectPublicMemory(@Param("id") Long id);
}
