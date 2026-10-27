package com.blog.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.blog.entity.MemoryInsight;
import com.blog.vo.VectorHitVo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 闪光点 Mapper —— 与 V1 的 {@code embedding vector(1024)} 列对齐。
 * <p>
 * 与 {@link MemoryVectorMapper} 同一约定：向量列不进实体，写入走显式 SQL + {@code ::vector} 转型。
 * 通用 {@code BaseMapper} 仍然可用（列清单由实体决定，不含 embedding），
 * 但不要再直接 {@code insert(insight)} —— 那样存不进向量，语义召回对该条永久失效。
 */
public interface MemoryInsightMapper extends BaseMapper<MemoryInsight> {

    /**
     * 带向量入库。{@code useGeneratedKeys} 让回填的 id 落到 {@code insight.id} 上，
     * 调用方仍可像以前一样直接用 {@code insight.getId()}。
     */
    @Insert("""
            INSERT INTO memory_insight (user_id, memory_id, content, emotion, embedding, source, create_time)
            VALUES (#{insight.userId}, #{insight.memoryId}, #{insight.content}, #{insight.emotion},
                    #{embedding}::vector, #{insight.source}, now())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "insight.id", keyColumn = "id")
    int insertWithVector(@Param("insight") MemoryInsight insight, @Param("embedding") String embedding);

    /**
     * 不带向量入库（Embedding 服务不可用时的降级）：该条只参与列表展示，不参与语义召回。
     */
    @Insert("""
            INSERT INTO memory_insight (user_id, memory_id, content, emotion, embedding, source, create_time)
            VALUES (#{insight.userId}, #{insight.memoryId}, #{insight.content}, #{insight.emotion},
                    NULL, #{insight.source}, now())
            """)
    @Options(useGeneratedKeys = true, keyProperty = "insight.id", keyColumn = "id")
    int insertWithoutVector(@Param("insight") MemoryInsight insight);

    /**
     * 闪光点级 ANN 召回：比记忆级更精确，用于"按语义精确提起某一句"。
     * 已软删的不召回；embedding 为 NULL 的行不在 HNSW 索引里，天然被排除。
     */
    @Select("""
            SELECT memory_id                          AS memoryId,
                   content                            AS title,
                   content                            AS snippet,
                   emotion,
                   1 - (embedding <=> #{vec}::vector) AS score,
                   create_time                        AS createTime
            FROM memory_insight
            WHERE user_id = #{userId}
              AND is_deleted = 0
              AND embedding IS NOT NULL
            ORDER BY embedding <=> #{vec}::vector
            LIMIT #{limit}
            """)
    List<VectorHitVo> searchTopK(@Param("userId") Long userId,
                                 @Param("vec") String vec,
                                 @Param("limit") int limit);
}
