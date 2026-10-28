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
import com.blog.service.impl.MemoryVisibilityService;
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
 * 记忆投影服务（D12 / D13）。
 * <p>
 * 语义：**记忆是源，博客文章是投影**。
 * <ul>
 *   <li>privacy=0 → 立即投影为 status=1 的公开文章（幂等锚点 source_memory_id）</li>
 *   <li>privacy=1/2 → 文章立即下架（status=0）</li>
 *   <li>记忆正文/标题/封面变更 → 比对 source_rev 后重投影</li>
 *   <li>文章被人工编辑过（manual_edited=1）→ 不覆盖正文，只置 needs_review=1 等人工合并</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryProjectionService {

    private static final String MEMORY_CATEGORY = "记忆";

    private final MemoryNoteMapper memoryNoteMapper;
    private final BlogArticleMapper blogArticleMapper;
    private final BlogCategoryMapper blogCategoryMapper;
    private final BlogArticleCategoryMapper blogArticleCategoryMapper;
    private final AgnesAiClient agnesAiClient;
    private final MemoryVisibilityService visibilityService;

    /**
     * 手动发布（兼容旧入口）：发布语义等价于「设为公开」，随后由投影维护。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long publish(Long memoryId, Long userId) {
        MemoryNote note = getOwnedNote(memoryId, userId);
        visibilityService.requireSealedAccess(note);
        if (note.getStatus() == null || note.getStatus() != MemoryNote.STATUS_CONDENSED) {
            throw new BusinessException(400, "只有已成念的日记才能发布为博客文章");
        }
        if (StringUtils.isBlank(note.getFinalDiary())) {
            throw new BusinessException(400, "日记内容为空，无法发布");
        }
        MemoryNote update = new MemoryNote();
        update.setId(memoryId);
        update.setPrivacy(MemoryVisibilityService.PRIVACY_PUBLIC);
        memoryNoteMapper.updateById(update);
        note.setPrivacy(MemoryVisibilityService.PRIVACY_PUBLIC);

        Long articleId = project(note, true);
        if (articleId == null) {
            throw new BusinessException(500, "文章投影失败");
        }
        return articleId;
    }

    public Long getPublishedArticleId(Long memoryId, Long userId) {
        MemoryNote note = getOwnedNote(memoryId, userId);
        BlogArticle article = findBySourceMemoryId(memoryId);
        return article != null ? article.getId() : note.getPublishedArticleId();
    }

    /**
     * 投影（幂等）。manual_edited=1 时不覆盖正文，只打 needs_review。
     *
     * @param useAiSummary 首次建文章时用 AI 生成摘要；同步场景走纯文本截断
     * @return 文章 ID；记忆尚未成篇时返回 null
     */
    @Transactional(rollbackFor = Exception.class)
    public Long project(MemoryNote note, boolean useAiSummary) {
        if (note == null || note.getStatus() == null || note.getStatus() != MemoryNote.STATUS_CONDENSED
                || StringUtils.isBlank(note.getFinalDiary())) {
            log.info("记忆 {} 尚未成篇，跳过投影", note == null ? null : note.getId());
            return null;
        }
        BlogArticle existing = findBySourceMemoryId(note.getId());
        if (existing == null) {
            return createArticle(note, useAiSummary);
        }
        if (Integer.valueOf(1).equals(existing.getManualEdited())) {
            if (!Integer.valueOf(1).equals(existing.getNeedsReview())) {
                BlogArticle update = new BlogArticle();
                update.setId(existing.getId());
                update.setNeedsReview(1);
                blogArticleMapper.updateById(update);
                log.info("记忆 {} 已更新，但文章 {} 被人工编辑过，标记待合并", note.getId(), existing.getId());
            }
            return existing.getId();
        }
        updateArticle(existing, note, false);
        return existing.getId();
    }

    /** 下架投影（privacy 离开 0 时） */
    @Transactional(rollbackFor = Exception.class)
    public void unpublish(MemoryNote note) {
        BlogArticle existing = findBySourceMemoryId(note.getId());
        if (existing == null) {
            return;
        }
        if (Integer.valueOf(1).equals(existing.getStatus())) {
            BlogArticle update = new BlogArticle();
            update.setId(existing.getId());
            update.setStatus(0);
            blogArticleMapper.updateById(update);
            log.info("记忆 {} 改为非公开，文章 {} 已下架", note.getId(), existing.getId());
        }
    }

    /**
     * 记忆变更后的统一入口：公开则重投影，非公开则下架。
     */
    @Transactional(rollbackFor = Exception.class)
    public void syncFromNote(MemoryNote note) {
        if (note == null) {
            return;
        }
        int privacy = note.getPrivacy() == null ? MemoryVisibilityService.PRIVACY_PRIVATE : note.getPrivacy();
        if (privacy != MemoryVisibilityService.PRIVACY_PUBLIC) {
            unpublish(note);
            return;
        }
        project(note, false);
    }

    /** 文章被人工编辑：断点打开，后续记忆变更不再覆盖正文 */
    public void markManualEdited(BlogArticle article) {
        if (article == null || article.getSourceMemoryId() == null) {
            return;
        }
        BlogArticle update = new BlogArticle();
        update.setId(article.getId());
        update.setManualEdited(1);
        update.setNeedsReview(0);
        blogArticleMapper.updateById(update);
    }

    public BlogArticle findBySourceMemoryId(Long memoryId) {
        if (memoryId == null) {
            return null;
        }
        List<BlogArticle> list = blogArticleMapper.selectList(new LambdaQueryWrapper<BlogArticle>()
                .eq(BlogArticle::getSourceMemoryId, memoryId));
        return list.isEmpty() ? null : list.get(0);
    }

    // ---------------- 内部 ----------------

    private Long createArticle(MemoryNote note, boolean useAiSummary) {
        BlogArticle article = new BlogArticle();
        article.setAuthorId(note.getUserId());
        article.setTitle(buildTitle(note));

        String safeContent = JsoupUtils.cleanHtml(toHtml(note.getFinalDiary()));
        article.setContent(safeContent);

        String plainText = JsoupUtils.cleanToPlainText(safeContent);
        article.setSummary(useAiSummary ? generateSummary(note, plainText)
                : DiaryFormatter.preview(plainText, 150));

        article.setCoverImage(note.getCoverPhoto());
        article.setStatus(1);
        article.setAllowComment(1);
        article.setViews(0);
        article.setSourceMemoryId(note.getId());
        article.setSourceRev(note.getUpdateTime() != null ? note.getUpdateTime() : LocalDateTime.now());
        article.setManualEdited(0);
        article.setNeedsReview(0);
        article.setCreateTime(LocalDateTime.now());
        article.setUpdateTime(LocalDateTime.now());
        blogArticleMapper.insert(article);

        attachMemoryCategory(article.getId());

        // 只写关联字段，避免 update_time 被自动回填后污染 sourceRev 水位
        memoryNoteMapper.updatePublishedArticleId(note.getId(), article.getId());

        log.info("记忆 {} 已投影为博客文章 {}", note.getId(), article.getId());
        return article.getId();
    }

    private void updateArticle(BlogArticle existing, MemoryNote note, boolean useAiSummary) {
        String safeContent = JsoupUtils.cleanHtml(toHtml(note.getFinalDiary()));
        BlogArticle update = new BlogArticle();
        update.setId(existing.getId());
        update.setTitle(buildTitle(note));
        update.setContent(safeContent);
        update.setSummary(useAiSummary ? generateSummary(note, JsoupUtils.cleanToPlainText(safeContent))
                : DiaryFormatter.preview(JsoupUtils.cleanToPlainText(safeContent), 150));
        update.setCoverImage(note.getCoverPhoto());
        update.setStatus(1);
        update.setSourceRev(note.getUpdateTime() != null ? note.getUpdateTime() : LocalDateTime.now());
        update.setNeedsReview(0);
        blogArticleMapper.updateById(update);
        log.info("记忆 {} 变更已同步到文章 {}", note.getId(), existing.getId());
    }

    private String buildTitle(MemoryNote note) {
        if (StringUtils.isNotBlank(note.getAutoTitle())) {
            return note.getAutoTitle();
        }
        LocalDateTime time = note.getFinishTime() != null ? note.getFinishTime() : LocalDateTime.now();
        return "记忆日记 " + time.toLocalDate();
    }

    private void attachMemoryCategory(Long articleId) {
        try {
            BlogCategory category = blogCategoryMapper.selectOne(
                    new LambdaQueryWrapper<BlogCategory>().eq(BlogCategory::getName, MEMORY_CATEGORY));
            if (category == null) {
                category = new BlogCategory();
                category.setName(MEMORY_CATEGORY);
                category.setCreateTime(LocalDateTime.now());
                blogCategoryMapper.insert(category);
                log.info("自动创建分类「{}」id={}", MEMORY_CATEGORY, category.getId());
            }
            Long count = blogArticleCategoryMapper.selectCount(
                    new LambdaQueryWrapper<BlogArticleCategory>()
                            .eq(BlogArticleCategory::getArticleId, articleId)
                            .eq(BlogArticleCategory::getCategoryId, category.getId()));
            if (count == null || count == 0) {
                blogArticleCategoryMapper.insert(new BlogArticleCategory(articleId, category.getId()));
            }
        } catch (Exception e) {
            log.warn("挂分类失败 articleId={}: {}", articleId, e.getMessage());
        }
    }

    private String generateSummary(MemoryNote note, String plainText) {
        String fallback = DiaryFormatter.preview(plainText, 150);
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
                    List.of(prompt), 120, 0.6);
            summary = summary.replaceAll("[\"'「」『』]", "").replaceAll("\\s+", " ").trim();
            if (StringUtils.isBlank(summary) || summary.length() > 80) {
                return fallback;
            }
            return summary;
        } catch (Exception e) {
            log.warn("记忆 {} AI 摘要生成失败，降级截断：{}", note.getId(), e.getMessage());
            return fallback;
        }
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

    /** 纯文本日记 → HTML 段落（空行分段，Markdown 标题转 h2/h3） */
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

        return paragraphs.isEmpty() ? "" : String.join("\n", paragraphs);
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
