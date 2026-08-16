package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆情绪标签归并表
 * <p>
 * 情绪锚点记忆：AI 自由生成情绪标签后，定期归并为规范情绪，
 * 后续检索按「情绪优先 + 内容匹配」双通道，而非只比文字。
 */
@Data
@TableName("memory_emotion")
public class MemoryEmotion {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 情绪标签（归并后的规范名，如：松弛/治愈/疲惫/开心/平淡/想念/充实） */
    private String emotion;

    /** 归并进来的原始标签集合（逗号分隔） */
    private String aliases;

    /** 出现次数 */
    private Long count;

    /** 最近出现时间（支持时序归并） */
    private LocalDateTime lastSeenAt;
}
