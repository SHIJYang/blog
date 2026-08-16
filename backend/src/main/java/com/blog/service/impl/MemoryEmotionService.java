package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.entity.MemoryEmotion;
import com.blog.entity.MemoryMessage;
import com.blog.entity.MemoryNote;
import com.blog.mapper.MemoryEmotionMapper;
import com.blog.mapper.MemoryMessageMapper;
import com.blog.mapper.MemoryNoteMapper;
import com.blog.service.ai.AgnesAiClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 情绪锚点记忆服务（原创核心）
 * <p>
 * 市面 AI 日记只记忆「内容」，本项目独家记忆「情绪」：
 * 1. AI 自由生成情绪标签：每次对话/照片入库时用 AI 提取情绪（不限定枚举，自由表达）
 * 2. 情绪落库：写入 memory_message.emotion，并在 memory_note.emotions 聚合
 * 3. 定期归并：把相似/同义的自由情绪标签合并为规范情绪（memory_emotion），
 *    既保证检索可匹配，又保留用户细腻的情感表达。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryEmotionService {

    private final MemoryMessageMapper memoryMessageMapper;
    private final MemoryNoteMapper memoryNoteMapper;
    private final MemoryEmotionMapper memoryEmotionMapper;
    private final AgnesAiClient agnesAiClient;

    /** 规范情绪基准集合（归并的目标桶，自由标签会映射到最接近的桶，仍保留原始标签） */
    private static final List<String> CANONICAL = List.of(
            "松弛", "治愈", "疲惫", "开心", "平淡", "想念", "充实"
    );

    /**
     * 用 AI 从一段文字中提取情绪标签（自由生成，1-3 个）
     *
     * @return 逗号分隔的情绪标签；识别失败返回 null（不阻断主流程）
     */
    public String extractEmotions(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            String prompt = "从这段文字中提取用户的情绪/心境，用 1-3 个中文双字词表述（如实、克制，不要鸡汤化）。"
                    + "直接输出用逗号分隔的词，不要解释，不要标点符号。\n文字：" + text;
            String raw = agnesAiClient.chat(
                    "你是敏锐温柔的情绪感知器，只如实捕捉文字里的真实情绪。",
                    List.of(prompt), 60, 0.5);
            if (StringUtils.isBlank(raw)) return null;
            // 清洗：去括号、符号，按中英文逗号/顿号/空格切分
            String cleaned = raw.replaceAll("[\\[\\]\"\"''()（）]", "").trim();
            String[] parts = cleaned.split("[，,、\\s]+");
            List<String> tags = new ArrayList<>();
            for (String p : parts) {
                String t = p.trim();
                if (t.length() >= 2 && t.length() <= 6 && !tags.contains(t)) {
                    tags.add(t);
                }
                if (tags.size() >= 3) break;
            }
            return tags.isEmpty() ? null : String.join(",", tags);
        } catch (Exception e) {
            log.warn("情绪提取失败，忽略: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 为用户的一条消息落情绪标签，并回写记忆的情绪聚合
     */
    @Transactional(rollbackFor = Exception.class)
    public void attachEmotion(Long userId, Long memoryId, Long messageId, String text) {
        String emotions = extractEmotions(text);
        if (StringUtils.isBlank(emotions)) return;

        // 1. 写入消息
        if (messageId != null) {
            MemoryMessage upd = new MemoryMessage();
            upd.setId(messageId);
            upd.setEmotion(emotions);
            memoryMessageMapper.updateById(upd);
        }

        // 2. 回写记忆聚合
        MemoryNote note = memoryNoteMapper.selectById(memoryId);
        if (note != null) {
            mergeIntoNote(memoryId, emotions);
        }
    }

    /**
     * 把一组情绪标签合并写入记忆的情绪聚合字段（去重、限长）
     */
    public void mergeIntoNote(Long memoryId, String emotions) {
        if (StringUtils.isBlank(emotions)) return;
        Set<String> all = new LinkedHashSet<>();
        MemoryNote note = memoryNoteMapper.selectById(memoryId);
        if (note != null && StringUtils.isNotBlank(note.getEmotions())) {
            all.addAll(Arrays.asList(note.getEmotions().split(",")));
        }
        for (String e : emotions.split(",")) {
            String t = e.trim();
            if (!t.isEmpty()) all.add(t);
        }
        if (all.size() > 12) {
            // 只保留最近 12 个（近似，取前 6 后 6 会破坏顺序，这里简单截断）
            List<String> list = new ArrayList<>(all);
            all = new LinkedHashSet<>(list.subList(Math.max(0, list.size() - 12), list.size()));
        }
        String joined = String.join(",", all);
        MemoryNote upd = new MemoryNote();
        upd.setId(memoryId);
        upd.setEmotions(joined);
        memoryNoteMapper.updateById(upd);
    }

    /**
     * 情绪归并：把该用户的自由情绪标签合并进规范情绪桶（memory_emotion）
     * 固定/定时触发。free 标签会先尝试映射到 CANONICAL 桶；无法归并的保留为独立桶。
     */
    @Transactional(rollbackFor = Exception.class)
    public void mergeEmotions(Long userId) {
        // 取该用户所有记忆的情绪聚合，拆成 (自由标签 → 次数)
        List<MemoryNote> notes = memoryNoteMapper.selectList(
                new LambdaQueryWrapper<MemoryNote>()
                        .eq(MemoryNote::getUserId, userId)
                        .isNotNull(MemoryNote::getEmotions)
                        .ne(MemoryNote::getEmotions, ""));
        Map<String, Long> tagCount = new HashMap<>();
        for (MemoryNote n : notes) {
            if (StringUtils.isBlank(n.getEmotions())) continue;
            for (String t : n.getEmotions().split(",")) {
                String tag = t.trim();
                if (!tag.isEmpty()) tagCount.merge(tag, 1L, Long::sum);
            }
        }
        if (tagCount.isEmpty()) return;

        // 规范桶累计 + 别名映射
        Map<String, Long> canonicalCount = new HashMap<>();
        Map<String, List<String>> aliasMap = new HashMap<>();
        for (Map.Entry<String, Long> e : tagCount.entrySet()) {
            String tag = e.getKey();
            String bucket = mapToCanonical(tag);
            canonicalCount.merge(bucket, e.getValue(), Long::sum);
            aliasMap.computeIfAbsent(bucket, k -> new ArrayList<>()).add(tag);
        }

        // 写库 upsert
        for (Map.Entry<String, Long> e : canonicalCount.entrySet()) {
            String bucket = e.getKey();
            MemoryEmotion existing = memoryEmotionMapper.selectOne(
                    new LambdaQueryWrapper<MemoryEmotion>()
                            .eq(MemoryEmotion::getUserId, userId)
                            .eq(MemoryEmotion::getEmotion, bucket));
            if (existing == null) {
                MemoryEmotion me = new MemoryEmotion();
                me.setUserId(userId);
                me.setEmotion(bucket);
                me.setAliases(String.join(",", aliasMap.get(bucket)));
                me.setCount(e.getValue());
                me.setLastSeenAt(LocalDateTime.now());
                memoryEmotionMapper.insert(me);
            } else {
                MemoryEmotion upd = new MemoryEmotion();
                upd.setId(existing.getId());
                upd.setCount(existing.getCount() == null ? e.getValue() : existing.getCount() + e.getValue());
                upd.setLastSeenAt(LocalDateTime.now());
                upd.setAliases(unionAliases(existing.getAliases(), aliasMap.get(bucket)));
                memoryEmotionMapper.updateById(upd);
            }
        }
        log.info("情绪归并完成: userId={}, 桶数={}", userId, canonicalCount.size());
    }

    /**
     * 把情绪标签映射到最接近的规范桶（简单包含匹配，前端保留原始标签）
     */
    public String mapToCanonical(String tag) {
        if (tag == null || tag.isBlank()) return tag;
        for (String c : CANONICAL) {
            if (c.equals(tag) || tag.contains(c) || c.contains(tag)) {
                return c;
            }
        }
        return tag; // 未归并，保留独立桶
    }

    private String unionAliases(String oldAliases, List<String> newAliases) {
        Set<String> all = new LinkedHashSet<>();
        if (StringUtils.isNotBlank(oldAliases)) {
            all.addAll(Arrays.asList(oldAliases.split(",")));
        }
        if (newAliases != null) all.addAll(newAliases);
        if (all.size() > 20) {
            List<String> list = new ArrayList<>(all);
            all = new LinkedHashSet<>(list.subList(Math.max(0, list.size() - 20), list.size()));
        }
        return String.join(",", all);
    }
}
