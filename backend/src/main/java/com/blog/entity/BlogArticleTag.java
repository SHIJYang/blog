package com.blog.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@TableName("blog_article_tag")
@AllArgsConstructor
@NoArgsConstructor
public class BlogArticleTag {
    private Long articleId;
    private Long tagId;
}