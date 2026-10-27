package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆闪光点（对应 V1 的 memory_insight）
 * <p>
 * 凝聚时 AI 提炼 1-5 条「闪光点」，用户也可手动添加；每条独立向量化，
 * 后续聊天可按语义精确提起某一段闪光点。
 * <p>
 * 向量列 {@code embedding vector(1024)} 同样不映射进实体，写入走
 * {@code MemoryInsightMapper#insertWithVector}（显式 {@code ::vector} 转型）。
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

    /** 来源：1-AI自动提炼 2-用户手动添加 */
    private Integer source;

    /** 软删标记：V1 已改为软删（原库是硬删），MP 全局 logic-delete-field 生效后 delete 即 UPDATE */
    private Integer isDeleted;

    private LocalDateTime createTime;
}
