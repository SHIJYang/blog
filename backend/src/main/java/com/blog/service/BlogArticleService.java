package com.blog.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.dto.ArticleSaveDto;
import com.blog.entity.BlogArticle;
import com.blog.vo.ArticleVo;

public interface BlogArticleService extends IService<BlogArticle> {
    /**
     * 保存新文章（包含 HTML 洗涤、多分类和标签多对多关系处理）
     */
    void saveArticle(ArticleSaveDto dto, Long authorId);

    /**
     * 修改文章（支持权限鉴别：仅作者本人或系统管理员可修改）
     */
    void updateArticle(ArticleSaveDto dto, Long currentUserId, String currentUserRole);

    /**
     * 获取文章详情（可选择是否需要增加浏览量 views）
     */
    ArticleVo getArticleVo(Long id, boolean incrementViews);

    /**
     * 条件分页检索文章
     */
    Page<ArticleVo> getArticleVoPage(Page<BlogArticle> page, Integer status, Long categoryId, Long tagId, String keyword);
}