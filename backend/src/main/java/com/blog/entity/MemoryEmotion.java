package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.blog.config.typehandler.PgTextArrayTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆情绪标签归并表（对应 V1 的 memory_emotion）
 * <p>
 * 情绪锚点记忆：AI 自由生成情绪标签后，定期归并为规范情绪，
 * 后续检索按「情绪优先 + 内容匹配」双通道，而非只比文字。
 * <p>
 * V1 相对旧库有两处结构变化，本实体已同步：
 * 1. 删掉 {@code count} 列——出现次数改为查询时按 {@code memory_note.emotions} 实时聚合
 *    （{@code MemoryQueryService#listEmotionStats}），根治"每次归并都在旧值上累加"的虚高 bug；
 * 2. {@code aliases} 改成 {@code text[]}，由 {@link PgTextArrayTypeHandler} 与逗号串互转。
 */
@Data
@TableName(value = "memory_emotion", autoResultMap = true)
public class MemoryEmotion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 情绪标签（归并后的规范名，如：松弛/治愈/疲惫/开心/平淡/想念/充实） */
    private String emotion;

    /** 归并进来的原始标签集合（库内 text[]，Java 侧逗号串） */
    @TableField(typeHandler = PgTextArrayTypeHandler.class)
    private String aliases;

    /** 最近出现时间（支持时序归并） */
    private LocalDateTime lastSeenAt;
}
