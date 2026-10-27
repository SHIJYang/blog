package com.blog.mapper;

import com.blog.entity.MemoryEmbedding;
import com.blog.vo.VectorHitVo;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 向量读写 Mapper —— 与 V1 的 {@code embedding vector(1024)} 列对齐。
 * <p>
 * 约定：**向量列不进实体**。所有涉及 embedding 的 SQL 都写在注解里，参数用 {@code ::vector} 显式转型，
 * 文本由 {@code VectorUtil.toJson()} 产出（pgvector 的文本格式恰好就是 {@code [0.1,0.2,...]}，
 * 与原来的 JSON 数组同形，因此不需要清洗旧数据格式）。
 * <p>
 * 好处：不需要 MyBatis TypeHandler，也不用把 {@code PGvector} 对象塞进实体；
 * MyBatis-Plus 的通用 insert/update 永远不会误碰向量列（JDBC 往 vector 列传 varchar 会直接报错）。
 * <p>
 * 召回从「Java 侧全量余弦」改为「DB 侧 ANN 先筛 + Java 侧情绪/时序加权」，
 * 走 V1 的部分 HNSW 索引 {@code idx_emb_hnsw}（{@code WHERE privacy <> 2}）——
 * 封存记忆物理上不在索引里，DB 层就保证了「永不召回」。
 */
public interface MemoryVectorMapper {

    /**
     * 写入或覆盖一条记忆向量（唯一键 user_id + memory_id）。
     * <p>
     * 取代原来的 delete + insert：原写法在唯一键上先删后插会拿间隙锁，
     * 且自增序列被快速烧掉（生产库 memory_embedding 自增已涨到 2963，而记忆只有 38 条）。
     */
    @Insert("""
            INSERT INTO memory_embedding
                (user_id, memory_id, vector_key, text_snippet, privacy, emotion, embedding, create_time)
            VALUES
                (#{userId}, #{memoryId}, #{vectorKey}, #{textSnippet},
                 #{privacy}, #{emotion}, #{embedding}::vector, now())
            ON CONFLICT (user_id, memory_id) DO UPDATE SET
                vector_key   = EXCLUDED.vector_key,
                text_snippet = EXCLUDED.text_snippet,
                privacy      = EXCLUDED.privacy,
                emotion      = EXCLUDED.emotion,
                embedding    = EXCLUDED.embedding,
                create_time  = EXCLUDED.create_time
            """)
    int upsertEmbedding(@Param("userId") Long userId,
                        @Param("memoryId") Long memoryId,
                        @Param("vectorKey") String vectorKey,
                        @Param("textSnippet") String textSnippet,
                        @Param("privacy") Integer privacy,
                        @Param("emotion") String emotion,
                        @Param("embedding") String embedding);

    /**
     * 降级写：Embedding 服务不可用时只留摘要与情绪快照（embedding 为 NULL）。
     * 语义召回用不上它，但关键词降级召回还能靠 text_snippet。
     */
    @Insert("""
            INSERT INTO memory_embedding
                (user_id, memory_id, vector_key, text_snippet, privacy, emotion, embedding, create_time)
            VALUES
                (#{userId}, #{memoryId}, #{vectorKey}, #{textSnippet},
                 #{privacy}, #{emotion}, NULL, now())
            ON CONFLICT (user_id, memory_id) DO UPDATE SET
                vector_key   = EXCLUDED.vector_key,
                text_snippet = EXCLUDED.text_snippet,
                privacy      = EXCLUDED.privacy,
                emotion      = EXCLUDED.emotion,
                embedding    = NULL,
                create_time  = EXCLUDED.create_time
            """)
    int upsertSnippetOnly(@Param("userId") Long userId,
                          @Param("memoryId") Long memoryId,
                          @Param("vectorKey") String vectorKey,
                          @Param("textSnippet") String textSnippet,
                          @Param("privacy") Integer privacy,
                          @Param("emotion") String emotion);

    /**
     * 记忆级 ANN 召回：数据库按余弦距离排序取 TopN，Java 侧再叠加情绪加成与时序因子。
     *
     * @param vec 查询向量文本（{@code [0.1,0.2,...]}），由调用方保证已 L2 归一化
     */
    @Select("""
            SELECT memory_id                          AS memoryId,
                   vector_key                         AS title,
                   text_snippet                       AS snippet,
                   emotion,
                   1 - (embedding <=> #{vec}::vector) AS score,
                   create_time                        AS createTime
            FROM memory_embedding
            WHERE user_id = #{userId}
              AND privacy <> 2
              -- 必须显式排掉 NULL：实测小表会走顺序扫描，而 HNSW 索引不包含 NULL 行这条保证
              -- 只对索引扫描成立；漏掉这行会让降级写入的"只有摘要"行混进候选，score 为 NULL
              AND embedding IS NOT NULL
            ORDER BY embedding <=> #{vec}::vector
            LIMIT #{limit}
            """)
    List<VectorHitVo> searchTopK(@Param("userId") Long userId,
                                 @Param("vec") String vec,
                                 @Param("limit") int limit);

    /**
     * 关键词降级召回用：不读向量列（embedding 可能为 NULL，也不该被解析）。
     */
    @Select("""
            SELECT id, user_id, memory_id, vector_key, text_snippet, privacy, emotion, create_time
            FROM memory_embedding
            WHERE user_id = #{userId}
              AND privacy <> 2
            ORDER BY id DESC
            LIMIT #{limit}
            """)
    List<MemoryEmbedding> selectByUser(@Param("userId") Long userId, @Param("limit") int limit);
}
