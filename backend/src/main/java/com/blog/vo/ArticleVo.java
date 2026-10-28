package com.blog.vo;

import com.blog.entity.BlogCategory;
import com.blog.entity.BlogTag;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ArticleVo {
    private Long id;
    private Long authorId;
    private String authorName; // 从用户表联查的昵称
    private String title;
    private String content;
    private String summary;
    private String coverImage;
    private Integer status;
    private Integer views;
    private Integer allowComment;
    /** 来源记忆 ID（非空表示这是拾光记忆的投影） */
    private Long sourceMemoryId;
    /** 1=人工编辑过 */
    private Integer manualEdited;
    /** 1=记忆已改但文章被人工编辑过，待人工合并 */
    private Integer needsReview;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private List<BlogCategory> categories; // 关联的分类详情列表
    private List<BlogTag> tags;             // 关联的标签详情列表
}