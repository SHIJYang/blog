package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆对话消息表
 * 存储单条记忆下的所有人机对话记录
 */
@Data
@TableName("memory_message")
public class MemoryMessage {

    /** 消息类型：用户文字 */
    public static final int TYPE_USER_TEXT = 1;
    /** 消息类型：AI 文字 */
    public static final int TYPE_AI_TEXT = 2;
    /** 消息类型：用户图片 */
    public static final int TYPE_USER_IMAGE = 3;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关联记忆主表ID */
    private Long memoryId;

    private Long userId;

    /** 消息类型：1-用户文字 2-AI文字 3-用户图片 */
    private Integer msgType;

    /** 消息文本内容 */
    private String content;

    /** 图片地址（图片消息专用） */
    private String imgUrl;

    /** AI 提取的情绪标签（自由生成） */
    private String emotion;

    private LocalDateTime createTime;

    /** 软删除 */
    @TableLogic
    private Integer isDeleted;
}
