package com.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.common.JsoupUtils;
import com.blog.entity.BlogArticle;
import com.blog.entity.BlogArticleCategory;
import com.blog.entity.BlogCategory;
import com.blog.entity.MemoryNote;
import com.blog.mapper.BlogArticleCategoryMapper;
import com.blog.mapper.BlogArticleMapper;
import com.blog.mapper.BlogCategoryMapper;
import com.blog.mapper.MemoryNoteMapper;
import com.blog.service.ai.AgnesAiClient;
import com.blog.util.DiaryFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 日记一键转博客公开文章
 * 打通私密记忆与公开创作：把已成篇的日记（finalDiary）发布为 blog_article，
 * 记录关联（memory_note.published_article_id），支持幂等（已发布不重复创建）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryPublishService {

    private final MemoryNoteMapper memoryNoteMapper;
    private final BlogArticleMapper blogArticleMapper;
    private final BlogCategoryMapper blogCategoryMapper;
    private final BlogArticleCategoryMapper blogArticleCategoryMapper;
    private final AgnesAiClient agnesAiClient;

    /**
     * 发布日记为博客文章
     *
     * @param memoryId 记忆ID
     * @param userId   当前登录用户ID
     * @return 创建的文章ID（若已发布则返回已存在的文章ID）
     */
    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long memoryId, Long userId) {
        MemoryNote note = getOwnedNote(memoryId, userId);

        // 幂等：已发布过则直接返回已有文章ID
        if (note.getPublishedArticleId() != null) {
            return note.getPublishedArticleId();
        }

        // 校验已成篇
        if (note.getStatus() == null || note.getStatus() != MemoryNote.STATUS_CONDENSED) {
            throw new BusinessException(400, "只有已成篇的日记才能发布为博客文章");
        }
        if (StringUtils.isBlank(note.getFinalDiary())) {
            throw new BusinessException(400, "日记内容为空，无法发布");
        }

        BlogArticle article = new BlogArticle();
        article.setAuthorId(userId);
        // 标题：autoTitle 为空则用日期兜底
        String title = StringUtils.isNotBlank(note.getAutoTitle())
                ? note.getAutoTitle()
                : "记忆日记 " + (note.getFinishTime() != null ? note.getFinishTime().toLocalDate() : LocalDateTime.now().toLocalDate());
        article.setTitle(title);

        // 正文：纯文本日记 -> HTML 段落
        String htmlContent = toHtml(note.getFinalDiary());
        // Jsoup 清洗，防 XSS
        String safeContent = JsoupUtils.cleanHtml(htmlContent);
        article.setContent(safeContent);

        // 摘要：优先 AI 生成一句话（贴合日记风格），失败降级为纯文本截断
        String plainText = JsoupUtils.cleanToPlainText(safeContent);
        article.setSummary(generateSummary(note, plainText));

        article.setCoverImage(note.getCoverPhoto());
        article.setStatus(1); // 公开已发布
        article.setAllowComment(1);
        article.setViews(0);
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());

        blogArticleMapper.insert(article);

        // 自动挂上「记忆」分类（不存在则创建），让日记文章归入记忆板块
        attachMemoryCategory(article.getId());

        // 回写关联
        note.setPublishedArticleId(article.getId());
        note.setUpdateTime(LocalDateTime.now());
        memoryNoteMapper.updateById(note);

        log.info("记忆 {} 已发布为博客文章 {}", memoryId, article.getId());
        return article.getId();
    }

    /**
     * 为文章挂上「记忆」分类：按名称查找，不存在则自动创建；幂等（重复发布不重复绑定）
     */
    private void attachMemoryCategory(Long articleId) {
        final String CATEGORY_NAME = "记忆";
        try {
            // 1. 按名称查分类
            BlogCategory category = blogCategoryMapper.selectOne(
                    new LambdaQueryWrapper<BlogCategory>().eq(BlogCategory::getName, CATEGORY_NAME));
            if (category == null) {
                // 2. 不存在则创建
                category = new BlogCategory();
                category.setName(CATEGORY_NAME);
                category.setCreateTime(LocalDateTime.now());
                blogCategoryMapper.insert(category);
                log.info("自动创建分类「{}」id={}", CATEGORY_NAME, category.getId());
            }
            // 3. 绑定文章-分类（幂等：存在则跳过）
            Long count = blogArticleCategoryMapper.selectCount(
                    new LambdaQueryWrapper<BlogArticleCategory>()
                            .eq(BlogArticleCategory::getArticleId, articleId)
                            .eq(BlogArticleCategory::getCategoryId, category.getId()));
            if (count == null || count == 0) {
                blogArticleCategoryMapper.insert(new BlogArticleCategory(articleId, category.getId()));
                log.info("文章 {} 已挂上分类「{}」", articleId, CATEGORY_NAME);
            }
        } catch (Exception e) {
            // 分类绑定失败不阻断发布（文章主体已入库）
            log.warn("挂分类失败 articleId={}: {}", articleId, e.getMessage());
        }
    }

    /**
     * AI 生成一句话摘要：用拾光/日记风格概括正文，失败时降级为纯文本前 150 字截断
     */
    private String generateSummary(MemoryNote note, String plainText) {
        // 兜底：AI 不可用时直接截断
        String fallback = plainText.length() > 150 ? plainText.substring(0, 150) + "..." : plainText;
        if (StringUtils.isBlank(plainText)) {
            return fallback;
        }
        try {
            String prompt = "下面是一篇私人日记，请用一句话（不超过 60 字）概括它，"
                    + "保留最打动人的具体细节，语气温暖自然，不要开头写『日记』，不要引用原文，不要加标点号外符号。\n\n"
                    + "日记标题：" + (StringUtils.isNotBlank(note.getAutoTitle()) ? note.getAutoTitle() : "（无）") + "\n"
                    + "日记内容：\n" + plainText;
            String summary = agnesAiClient.chat(
                    "你是拾光，温柔安静的日记整理者。你只负责把这篇日记凝练成一句自然的摘要，不评价、不升华、不编造。",
                    java.util.List.of(prompt),
                    120, 0.6);
            // 清洗：去掉 AI 可能多输出的引号/换行/编号
            summary = summary.replaceAll("[\"'「」『』]", "")
                    .replaceAll("\\s+", " ")
                    .trim();
            if (StringUtils.isBlank(summary) || summary.length() > 80) {
                return fallback;
            }
            log.info("记忆 {} AI 生成摘要：{}", note.getId(), summary);
            return summary;
        } catch (Exception e) {
            log.warn("记忆 {} AI 摘要生成失败，降级为截断：{}", note.getId(), e.getMessage());
            return fallback;
        }
    }

    /**
     * 查询发布状态（前端展示「已发布」按钮态）
     */
    public Long getPublishedArticleId(Long memoryId, Long userId) {
        MemoryNote note = getOwnedNote(memoryId, userId);
        return note.getPublishedArticleId();
    }

    private MemoryNote getOwnedNote(Long memoryId, Long userId) {
        MemoryNote note = memoryNoteMapper.selectById(memoryId);
        if (note == null) {
            throw new BusinessException(404, "记忆不存在");
        }
        boolean admin = com.blog.security.utils.SecurityUtils.isAdmin();
        if (!note.getUserId().equals(userId) && !admin) {
            throw new BusinessException(403, "无权操作他人的记忆");
        }
        return note;
    }

    /**
     * 纯文本日记 -> HTML 段落
     * 空行分段，连续行合并为一段；标题行（#）转 h2/h3
     */
    private String toHtml(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String[] rawLines = text.replace("\r\n", "\n").split("\n");
        List<String> paragraphs = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String raw : rawLines) {
            String line = raw.trim();
            if (line.isEmpty()) {
                flush(current, paragraphs);
                continue;
            }
            // Markdown 标题行
            if (line.startsWith("### ")) {
                flush(current, paragraphs);
                paragraphs.add("<h3>" + escapeHtml(line.substring(4)) + "</h3>");
            } else if (line.startsWith("## ")) {
                flush(current, paragraphs);
                paragraphs.add("<h2>" + escapeHtml(line.substring(3)) + "</h2>");
            } else if (line.startsWith("# ")) {
                flush(current, paragraphs);
                paragraphs.add("<h2>" + escapeHtml(line.substring(2)) + "</h2>");
            } else {
                if (current.length() > 0) {
                    current.append(" ");
                }
                current.append(escapeHtml(line));
            }
        }
        flush(current, paragraphs);

        if (paragraphs.isEmpty()) {
            return "";
        }
        return String.join("\n", paragraphs);
    }

    private void flush(StringBuilder sb, List<String> out) {
        if (sb.length() > 0) {
            out.add("<p>" + sb + "</p>");
            sb.setLength(0);
        }
    }

    private String escapeHtml(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
