package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆向量表
 * 采用 MySQL 存储浮点向量（JSON），本地余弦相似度计算实现语义召回，
 * 无需额外部署向量数据库，天然支持 userId 强隔离。
 */
@Data
@TableName("memory_embedding")
public class MemoryEmbedding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 关联记忆ID */
    private Long memoryId;

    /** 向量KEY（语义检索时使用，默认取记忆标题） */
    private String vectorKey;

    /** 向量化的原文摘要（用于溯源展示） */
    private String textSnippet;

    /** 创建时记忆的隐私快照(0/1/2) */
    private Integer privacy;

    /** 该记忆主情绪标签 */
    private String emotion;

    /** 1024 维向量 JSON 字符串 */
    private String vectorJson;

    private LocalDateTime createTime;
}
