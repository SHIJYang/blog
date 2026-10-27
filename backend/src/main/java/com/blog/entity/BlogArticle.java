package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_article")
public class BlogArticle {
    @TableId(type = IdType.AUTO)
    private Long id;

    private Long authorId;

    private String title;

    private String content;

    private String summary;

    private String coverImage;

    /**
     * 状态: 0-草稿, 1-公开已发布, 2-私密/仅自己可见
     */
    private Integer status;

    private Integer views;

    /**
     * 是否允许评论: 0-否, 1-是
     */
    private Integer allowComment;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;

    /** PG 没有 ON UPDATE CURRENT_TIMESTAMP，由 MetaObjectHandler 自动回填 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}