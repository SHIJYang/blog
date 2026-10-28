package com.blog.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 记忆列表项
 */
@Data
@Builder
public class MemoryListVo {

    private Long id;
    /** 所属用户ID（管理员查看所有记忆时用于区分） */
    private Long userId;
    /** 所属用户名/昵称（管理展示用） */
    private String username;
    private String coverPhoto;
    /** 封面缩略图URL */
    private String coverThumb;
    private String autoTitle;
    /** 日记正文摘要（前 80 字） */
    private String diaryPreview;
    /** 1-未开始 2-对话中 3-已成念 4-已废弃 */
    private Integer status;
    /** 记忆隐私等级：0-公开 1-私密 2-封存 */
    private Integer privacy;
    /** 记忆类型：1-照片 2-纯文本碎念 3-心境随笔 */
    private Integer kind;
    /** 封存时间（privacy=2 时非空） */
    private LocalDateTime sealedAt;
    /** 聚合情绪标签（逗号分隔） */
    private String emotions;
    /** 对话消息数 */
    private Long messageCount;
    private LocalDateTime createTime;
    private LocalDateTime finishTime;
}
