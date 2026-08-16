package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆闪光点
 * <p>
 * 凝聚时 AI 提炼 1-5 条「闪光点」，用户也可手动添加；每条独立向量化，
 * 后续聊天可按语义精确提起某一段闪光点。
 */
@Data
@TableName("memory_insight")
public class MemoryInsight {

    /** 来源：AI 自动提炼 */
    public static final int SOURCE_AI = 1;
    /** 来源：用户手动添加 */
    public static final int SOURCE_USER = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 关联记忆ID */
    private Long memoryId;

    /** 闪光点内容 */
    private String content;

    /** 该条闪光点情绪标签 */
    private String emotion;

    /** 该闪光点独立向量(JSON,1024维) */
    private String vectorJson;

    /** 来源：1-AI自动提炼 2-用户手动添加 */
    private Integer source;

    private LocalDateTime createTime;
}
