package com.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Mapper 内部投影：ANN 召回命中的一行。
 * <p>
 * 只用于 {@code MemoryVectorMapper#searchTopK} / {@code MemoryInsightMapper#searchTopK}
 * 承接 SQL 里的相似度列，**不对外暴露**（对外统一走 {@link RecallVo}）。
 * 不能复用 RecallVo：它只有 {@code @Builder}、没有无参构造，MyBatis 无法实例化。
 */
@Data
public class VectorHitVo {

    /** 命中的记忆ID */
    private Long memoryId;

    /** 标题（记忆取 vector_key，闪光点取 content） */
    private String title;

    /** 片段（记忆取 text_snippet，闪光点取 content） */
    private String snippet;

    /** 情绪标签 */
    private String emotion;

    /** 余弦相似度 = 1 - 余弦距离，PG 用 `<=>` 算 */
    private Double score;

    /** 用于时序加权 */
    private LocalDateTime createTime;
}
