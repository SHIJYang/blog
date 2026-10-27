package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_comment")
public class BlogComment {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long articleId;

    private Long userId;

    private String nickname;

    private String email;

    private String website;

    private String content;

    /**
     * 直接被回复的父评论 ID (一级的父评论为 NULL)
     */
    private Long parentId;

    /**
     * 顶层一级主评论 ID (一级的 rootId 为 NULL，子孙回复全部指向一级主评论)
     */
    private Long rootId;

    /**
     * 审核状态: 0-待审核, 1-审核通过, 2-垃圾评论
     */
    private Integer status;

    private String ipAddress;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;
}