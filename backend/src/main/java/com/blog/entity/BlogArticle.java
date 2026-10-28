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

    /** 来源记忆 ID：非空表示这篇文章是拾光记忆的投影 */
    private Long sourceMemoryId;

    /** 投影水位：最近一次从记忆同步时的 memory_note.update_time */
    private LocalDateTime sourceRev;

    /** 1=人工编辑过，记忆再改时不覆盖正文 */
    private Integer manualEdited;

    /** 1=记忆又改了但文章被人工编辑过，待人工合并 */
    private Integer needsReview;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;

    /** PG 没有 ON UPDATE CURRENT_TIMESTAMP，由 MetaObjectHandler 自动回填 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}