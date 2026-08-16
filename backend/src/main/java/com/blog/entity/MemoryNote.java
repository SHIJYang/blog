package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户记忆主表
 * 对应一条「照片 + 对话 + 日记」的完整记忆单元
 */
@Data
@TableName("memory_note")
public class MemoryNote {

    /** 状态：未开始 */
    public static final int STATUS_NOT_STARTED = 1;
    /** 状态：对话中 */
    public static final int STATUS_CHATTING = 2;
    /** 状态：已成念（已凝聚为日记） */
    public static final int STATUS_CONDENSED = 3;
    /** 状态：已废弃 */
    public static final int STATUS_ABANDONED = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联博客用户ID */
    private Long userId;

    /** 记忆封面照片URL */
    private String coverPhoto;

    /** 记忆封面缩略图URL（图床缩略图，列表展示用，可为空） */
    private String coverThumb;

    /** AI 图像识别自动生成标题 */
    private String autoTitle;

    /** 凝聚后生成的完整日记正文 */
    private String finalDiary;

    /** 状态：1-未开始 2-对话中 3-已成念 4-已废弃 */
    private Integer status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    /** 记忆凝聚完成时间 */
    private LocalDateTime finishTime;

    /** 发布到博客的文章ID（未发布为null） */
    private Long publishedArticleId;

    /** 记忆隐私等级：0-公开 1-私密 2-封存（默认私密） */
    private Integer privacy;

    /** 本记忆聚合的情绪标签（逗号分隔，AI 自由生成） */
    private String emotions;

    /** 软删除 */
    @TableLogic
    private Integer isDeleted;
}
