package com.blog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@TableName("blog_article_category")
@AllArgsConstructor
@NoArgsConstructor
public class BlogArticleCategory {
    private Long articleId;
    private Long categoryId;
}