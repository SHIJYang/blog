package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.common.BusinessException;
import com.blog.common.JsoupUtils;
import com.blog.dto.ArticleSaveDto;
import com.blog.entity.*;
import com.blog.mapper.*;
import com.blog.service.BlogArticleService;
import com.blog.vo.ArticleVo;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BlogArticleServiceImpl extends ServiceImpl<BlogArticleMapper, BlogArticle> implements BlogArticleService {

    private final BlogArticleMapper blogArticleMapper;
    private final BlogCategoryMapper blogCategoryMapper;
    private final BlogArticleCategoryMapper blogArticleCategoryMapper;
    private final BlogTagMapper blogTagMapper;
    private final BlogArticleTagMapper blogArticleTagMapper;
    private final SysUserMapper sysUserMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveArticle(ArticleSaveDto dto, Long authorId) {
        BlogArticle article = new BlogArticle();
        article.setAuthorId(authorId);
        article.setTitle(dto.getTitle());

        // Jsoup 过滤 HTML 攻击标签，保障富文本安全
        String safeContent = JsoupUtils.cleanHtml(dto.getContent());
        article.setContent(safeContent);

        // 如果未填写摘要，则过滤 HTML 标签后提取文本前 150 字作为摘要
        if (StringUtils.isBlank(dto.getSummary())) {
            String plainText = JsoupUtils.cleanToPlainText(safeContent);
            article.setSummary(plainText.length() > 150 ? plainText.substring(0, 150) + "..." : plainText);
        } else {
            article.setSummary(dto.getSummary());
        }

        article.setCoverImage(dto.getCoverImage());
        article.setStatus(dto.getStatus());
        article.setAllowComment(dto.getAllowComment() != null ? dto.getAllowComment() : 1);
        article.setViews(0);

        this.save(article);

        // 多对多绑定分类与标签
        saveRelations(article.getId(), dto.getCategoryIds(), dto.getTagIds());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateArticle(ArticleSaveDto dto, Long currentUserId, String currentUserRole) {
        BlogArticle article = this.getById(dto.getId());
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }

        // 仅博主本人（文章作者）或拥有 ADMIN 权限的管理员可修改
        if (!article.getAuthorId().equals(currentUserId) && !currentUserRole.equals("ADMIN")) {
            throw new BusinessException(403, "您无权修改他人的文章");
        }

        article.setTitle(dto.getTitle());
        String safeContent = JsoupUtils.cleanHtml(dto.getContent());
        article.setContent(safeContent);

        if (StringUtils.isBlank(dto.getSummary())) {
            String plainText = JsoupUtils.cleanToPlainText(safeContent);
            article.setSummary(plainText.length() > 150 ? plainText.substring(0, 150) + "..." : plainText);
        } else {
            article.setSummary(dto.getSummary());
        }

        article.setCoverImage(dto.getCoverImage());
        article.setStatus(dto.getStatus());
        if (dto.getAllowComment() != null) {
            article.setAllowComment(dto.getAllowComment());
        }

        this.updateById(article);

        // 物理删除老旧关联关系，重新绑定新关系
        blogArticleCategoryMapper.delete(new LambdaQueryWrapper<BlogArticleCategory>()
                .eq(BlogArticleCategory::getArticleId, article.getId()));
        blogArticleTagMapper.delete(new LambdaQueryWrapper<BlogArticleTag>()
                .eq(BlogArticleTag::getArticleId, article.getId()));

        saveRelations(article.getId(), dto.getCategoryIds(), dto.getTagIds());
    }

    private void saveRelations(Long articleId, List<Long> categoryIds, List<Long> tagIds) {
        // 1. 分类去重保护
        if (categoryIds != null && !categoryIds.isEmpty()) {
            List<Long> distinctCategoryIds = categoryIds.stream()
                    .distinct() // 过滤重复的分类 ID
                    .toList();
            for (Long categoryId : distinctCategoryIds) {
                blogArticleCategoryMapper.insert(new BlogArticleCategory(articleId, categoryId));
            }
        }

        // 2. 标签去重保护
        if (tagIds != null && !tagIds.isEmpty()) {
            List<Long> distinctTagIds = tagIds.stream()
                    .distinct() // 过滤重复的标签 ID
                    .toList();
            for (Long tagId : distinctTagIds) {
                blogArticleTagMapper.insert(new BlogArticleTag(articleId, tagId));
            }
        }
    }

    @Override
    public ArticleVo getArticleVo(Long id, boolean incrementViews) {
        BlogArticle article = this.getById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }

        if (incrementViews) {
            article.setViews(article.getViews() + 1);
            blogArticleMapper.updateById(article);
        }

        return assembleVo(article);
    }

    @Override
    public Page<ArticleVo> getArticleVoPage(Page<BlogArticle> page, Integer status, Long categoryId, Long tagId, String keyword) {
        List<Long> articleIds = null;

        // 通过分类条件进行过滤筛选
        if (categoryId != null) {
            List<BlogArticleCategory> list = blogArticleCategoryMapper.selectList(new LambdaQueryWrapper<BlogArticleCategory>()
                    .eq(BlogArticleCategory::getCategoryId, categoryId));
            articleIds = list.stream().map(BlogArticleCategory::getArticleId).collect(Collectors.toList());
            if (articleIds.isEmpty()) {
                return new Page<>();
            }
        }

        // 通过标签条件进行过滤筛选并交集汇总
        if (tagId != null) {
            List<BlogArticleTag> list = blogArticleTagMapper.selectList(new LambdaQueryWrapper<BlogArticleTag>()
                    .eq(BlogArticleTag::getTagId, tagId));
            List<Long> tagArticleIds = list.stream().map(BlogArticleTag::getArticleId).collect(Collectors.toList());
            if (articleIds == null) {
                articleIds = tagArticleIds;
            } else {
                articleIds.retainAll(tagArticleIds);
            }
            if (articleIds.isEmpty()) {
                return new Page<>();
            }
        }

        LambdaQueryWrapper<BlogArticle> wrapper = new LambdaQueryWrapper<>();
        if (status != null) {
            wrapper.eq(BlogArticle::getStatus, status);
        }
        if (articleIds != null) {
            wrapper.in(BlogArticle::getId, articleIds);
        }
        if (StringUtils.isNotBlank(keyword)) {
            wrapper.and(w -> w.like(BlogArticle::getTitle, keyword)
                    .or()
                    .like(BlogArticle::getContent, keyword));
        }
        wrapper.orderByDesc(BlogArticle::getCreateTime);

        Page<BlogArticle> articlePage = this.page(page, wrapper);
        Page<ArticleVo> voPage = new Page<>(articlePage.getCurrent(), articlePage.getSize(), articlePage.getTotal());

        List<ArticleVo> voList = articlePage.getRecords().stream()
                .map(this::assembleVo)
                .collect(Collectors.toList());

        voPage.setRecords(voList);
        return voPage;
    }

    private ArticleVo assembleVo(BlogArticle article) {
        ArticleVo vo = new ArticleVo();
        vo.setId(article.getId());
        vo.setAuthorId(article.getAuthorId());
        vo.setTitle(article.getTitle());
        vo.setContent(article.getContent());
        vo.setSummary(article.getSummary());
        vo.setCoverImage(article.getCoverImage());
        vo.setStatus(article.getStatus());
        vo.setViews(article.getViews());
        vo.setAllowComment(article.getAllowComment());
        vo.setCreateTime(article.getCreateTime());
        vo.setUpdateTime(article.getUpdateTime());

        // 查询作者昵称
        SysUser user = sysUserMapper.selectById(article.getAuthorId());
        if (user != null) {
            vo.setAuthorName(user.getNickname());
        }

        // 装配多对多分类
        List<BlogArticleCategory> acList = blogArticleCategoryMapper.selectList(new LambdaQueryWrapper<BlogArticleCategory>()
                .eq(BlogArticleCategory::getArticleId, article.getId()));
        if (!acList.isEmpty()) {
            List<Long> cIds = acList.stream().map(BlogArticleCategory::getCategoryId).collect(Collectors.toList());
            vo.setCategories(blogCategoryMapper.selectBatchIds(cIds));
        } else {
            vo.setCategories(new ArrayList<>());
        }

        // 装配多对多标签
        List<BlogArticleTag> atList = blogArticleTagMapper.selectList(new LambdaQueryWrapper<BlogArticleTag>()
                .eq(BlogArticleTag::getArticleId, article.getId()));
        if (!atList.isEmpty()) {
            List<Long> tIds = atList.stream().map(BlogArticleTag::getTagId).collect(Collectors.toList());
            vo.setTags(blogTagMapper.selectBatchIds(tIds));
        } else {
            vo.setTags(new ArrayList<>());
        }

        return vo;
    }
}