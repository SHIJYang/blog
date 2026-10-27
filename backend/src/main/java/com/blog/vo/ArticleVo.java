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
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private List<BlogCategory> categories; // 关联的分类详情列表
    private List<BlogTag> tags;             // 关联的标签详情列表
}