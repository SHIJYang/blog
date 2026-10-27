package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆向量表（对应 V1 的 memory_embedding）
 * <p>
 * 向量列 {@code embedding vector(1024)} **刻意不映射进实体**：
 * 写入与召回全部走 {@code MemoryVectorMapper} 里带 {@code ::vector} 转型的显式 SQL，
 * 这样既不需要 TypeHandler，也不会被 MyBatis-Plus 的通用 insert 误写（varchar 进 vector 列会直接报错）。
 * 语义召回由数据库 ANN（{@code <=>}）完成，本实体只承载"非向量"的元信息与关键词降级所需字段。
 */
@Data
@TableName("memory_embedding")
public class MemoryEmbedding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 关联记忆ID */
    private Long memoryId;

    /** 向量KEY（语义检索时使用，默认取记忆标题；库上限 varchar(64)） */
    private String vectorKey;

    /** 向量化的原文摘要（用于溯源展示，也是关键词降级的检索源） */
    private String textSnippet;

    /** 创建时记忆的隐私快照(0/1/2)，召回与索引都按它过滤 */
    private Integer privacy;

    /** 该记忆主情绪标签 */
    private String emotion;

    private LocalDateTime createTime;
}
