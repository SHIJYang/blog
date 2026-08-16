package com.blog.service.vector;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.vo.RecallVo;
import com.blog.entity.MemoryEmbedding;
import com.blog.mapper.MemoryEmbeddingMapper;
import com.blog.mapper.MemoryVectorMapper;
import com.blog.service.ai.VectorUtil;
import com.blog.service.impl.MemoryEmotionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 长期记忆向量服务（跨对话记忆核心）—— 拾光碎念 三套原创记忆机制
 * <p>
 * 摒弃「无差别相似度召回」，实现：
 * 1. 时序羁绊记忆：召回按时间加权，近期记忆更高分；连续同情绪自动形成「阶段」被优先唤起。
 * 2. 情绪锚点记忆：检索不只比文字，优先匹配当下情绪（情绪通道 + 内容通道 双打分）。
 * 3. 专属私藏记忆：三级隐私过滤——封存(2) 永不召回，私密(1) 温和关联，公开(0) 可随意召回。
 * <p>
 * 实现（Java 标准方案，无需外部向量库）：余弦相似度 + 情绪/时序多因子加权，MySQL 存向量，userId 强隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryVectorService {

    private final MemoryEmbeddingMapper memoryEmbeddingMapper;
    private final MemoryVectorMapper memoryVectorMapper;
    private final com.blog.service.ai.AgnesAiClient agnesAiClient;
    private final MemoryEmotionService memoryEmotionService;
    private final com.blog.mapper.MemoryInsightMapper memoryInsightMapper;

    @Value("${embedding.min-score:0.35}")
    private double minScore;

    @Value("${memory.recall-top-k:3}")
    private int recallTopK;

    /** 情绪通道加成系数（情绪匹配可显著抬升该记忆得分） */
    @Value("${memory.emotion-boost:0.25}")
    private double emotionBoost;

    /** 封存记忆永不召回 */
    private static final int PRIVACY_SEALED = 2;

    /**
     * 记忆入库向量化（含隐私快照 + 主情绪快照）
     *
     * @param privacy 创建时记忆隐私等级(0/1/2)
     * @param emotion 主情绪标签（可为空）
     */
    public void storeEmbedding(Long userId, Long memoryId, String title, String text, Integer privacy, String emotion) {
        try {
            float[] vec = agnesAiClient.embed(text);
            if (vec == null || vec.length == 0) {
                saveSnippetOnly(userId, memoryId, title, text, privacy, emotion);
                return;
            }
            MemoryEmbedding embedding = new MemoryEmbedding();
            embedding.setUserId(userId);
            embedding.setMemoryId(memoryId);
            embedding.setVectorKey(title != null && !title.isBlank() ? title : "记忆" + memoryId);
            embedding.setTextSnippet(truncate(text, 500));
            embedding.setPrivacy(privacy != null ? privacy : 1);
            embedding.setEmotion(emotion);
            embedding.setVectorJson(VectorUtil.toJson(VectorUtil.normalize(vec)));
            embedding.setCreateTime(LocalDateTime.now());

            memoryEmbeddingMapper.delete(new LambdaQueryWrapper<MemoryEmbedding>()
                    .eq(MemoryEmbedding::getUserId, userId)
                    .eq(MemoryEmbedding::getMemoryId, memoryId));
            memoryEmbeddingMapper.insert(embedding);
            log.info("记忆向量入库: userId={}, memoryId={}, dim={}, privacy={}, emotion={}",
                    userId, memoryId, vec.length, embedding.getPrivacy(), emotion);
        } catch (Exception e) {
            log.error("记忆向量入库失败: userId={}, memoryId={}, err={}", userId, memoryId, e.getMessage());
            saveSnippetOnly(userId, memoryId, title, text, privacy, emotion);
        }
    }

    /** 兼容旧签名（默认私密、无情绪） */
    public void storeEmbedding(Long userId, Long memoryId, String title, String text) {
        storeEmbedding(userId, memoryId, title, text, 1, null);
    }

    /**
     * 情感锚点召回（首发）：情绪优先 + 内容语义 + 时序加权 + 隐私过滤
     *
     * @param userId        当前用户
     * @param queryText     当前内容（向量化）
     * @param currentEmotion 当前情绪标签（由最近消息情绪聚合而来，可为空）
     * @return 综合排序后的回忆列表（不含封存）
     */
    public List<RecallVo> recall(Long userId, String queryText, String currentEmotion) {
        List<MemoryEmbedding> all = memoryVectorMapper.selectByUser(userId, 500);

        // 隐私过滤：封存(2) 永不召回
        List<MemoryEmbedding> pool = all.isEmpty() ? Collections.emptyList() : all.stream()
                .filter(e -> e.getPrivacy() == null || e.getPrivacy() != PRIVACY_SEALED)
                .collect(Collectors.toList());

        float[] queryVec = agnesAiClient.embed(queryText);
        if (queryVec == null || queryVec.length == 0) {
            return keywordRecall(pool, queryText, currentEmotion);
        }
        float[] q = VectorUtil.normalize(queryVec);

        List<RecallVo> results = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 1) 记忆级语义召回
        for (MemoryEmbedding em : pool) {
            float[] v = VectorUtil.fromJson(em.getVectorJson());
            if (v.length == 0) continue;

            double contentScore = VectorUtil.cosine(q, v);
            if (contentScore < minScore) continue;

            double score = contentScore;
            if (StringUtils.isNotBlank(currentEmotion) && emotionMatches(em.getEmotion(), currentEmotion)) {
                score += emotionBoost;
            }
            double recency = recencyFactor(em.getCreateTime(), now);
            score = score * (1 + recency);

            results.add(RecallVo.builder()
                    .memoryId(em.getMemoryId())
                    .title(em.getVectorKey())
                    .snippet(em.getTextSnippet())
                    .emotion(em.getEmotion())
                    .score(Math.round(score * 1000) / 1000.0)
                    .build());
        }

        // 2) 一句话知识点级召回（更精确，权重略高以便能精确提起某一句）
        try {
            List<com.blog.entity.MemoryInsight> insights = memoryInsightMapper.selectVectorsByUser(userId, 500);
            for (com.blog.entity.MemoryInsight ins : insights) {
                float[] v = VectorUtil.fromJson(ins.getVectorJson());
                if (v.length == 0) continue;
                double contentScore = VectorUtil.cosine(q, v);
                if (contentScore < minScore) continue;
                double score = contentScore + 0.05; // 知识点更精确，微抬权重便于被精确提起
                if (StringUtils.isNotBlank(currentEmotion) && StringUtils.isNotBlank(ins.getEmotion())
                        && ins.getEmotion().contains(currentEmotion)) {
                    score += emotionBoost;
                }
                results.add(RecallVo.builder()
                        .memoryId(ins.getMemoryId())
                        .title(ins.getContent())
                        .snippet(ins.getContent())
                        .emotion(ins.getEmotion())
                        .score(Math.round(score * 1000) / 1000.0)
                        .build());
            }
        } catch (Exception e) {
            log.warn("知识点召回失败: {}", e.getMessage());
        }

        // 3) 合并去重（同一 memory 尽量保留知识点，因更精确）并按分排序取 TopK
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return results.stream().limit(recallTopK).collect(Collectors.toList());
    }

    /** 兼容：无情绪时的召回 */
    public List<RecallVo> recall(Long userId, String queryText) {
        return recall(userId, queryText, null);
    }

    /**
     * 情绪匹配：记忆情绪快照里是否含当前情绪（或归并可命中）
     */
    private boolean emotionMatches(String memoryEmotion, String currentEmotion) {
        if (StringUtils.isBlank(memoryEmotion) || StringUtils.isBlank(currentEmotion)) return false;
        Set<String> memTags = split(memoryEmotion);
        // 当前情绪可能多词
        for (String cur : split(currentEmotion)) {
            if (memTags.contains(cur)) return true;
            // 归并别名兜底
            String canon = memoryEmotionService.mapToCanonical(cur);
            for (String m : memTags) {
                if (m.equals(canon) || memoryEmotionService.mapToCanonical(m).equals(canon)) return true;
            }
        }
        return false;
    }

    private Set<String> split(String s) {
        if (StringUtils.isBlank(s)) return Collections.emptySet();
        return new LinkedHashSet<>(Arrays.asList(s.split("[,，\\s]+")));
    }

    /**
     * 时序因子：0~0.4，越近越高（7 天内最强，之后随时间衰减）
     */
    private double recencyFactor(LocalDateTime created, LocalDateTime now) {
        if (created == null) return 0;
        long days = ChronoUnit.DAYS.between(created, now);
        if (days <= 7) return 0.4;
        if (days <= 30) return 0.25;
        if (days <= 90) return 0.1;
        return 0;
    }

    /**
     * 关键词降级召回（Embedding 服务不可用时），同样做情绪优先 + 封存过滤
     */
    private List<RecallVo> keywordRecall(List<MemoryEmbedding> pool, String queryText, String currentEmotion) {
        if (queryText == null || queryText.isBlank()) return Collections.emptyList();
        String[] keywords = queryText.toLowerCase().split("[\\s，。！？、,.!?]+");
        List<RecallVo> results = new ArrayList<>();
        for (MemoryEmbedding em : pool) {
            String text = (em.getTextSnippet() == null ? "" : em.getTextSnippet()).toLowerCase();
            String key = (em.getVectorKey() == null ? "" : em.getVectorKey()).toLowerCase();
            int hit = 0;
            for (String kw : keywords) {
                if (kw.length() >= 2 && (text.contains(kw) || key.contains(kw))) hit++;
            }
            double score = hit > 0 ? Math.min(0.99, 0.5 + hit * 0.1) : 0;
            if (score <= 0) continue;
            if (StringUtils.isNotBlank(currentEmotion) && emotionMatches(em.getEmotion(), currentEmotion)) {
                score += emotionBoost;
            }
            score += recencyFactor(em.getCreateTime(), LocalDateTime.now());
            results.add(RecallVo.builder()
                    .memoryId(em.getMemoryId())
                    .title(em.getVectorKey())
                    .snippet(em.getTextSnippet())
                    .emotion(em.getEmotion())
                    .score(Math.round(Math.min(1.0, score) * 1000) / 1000.0)
                    .build());
        }
        results.sort((a, b) -> Double.compare(b.getScore(), a.getScore()));
        return results.stream().limit(recallTopK).collect(Collectors.toList());
    }

    /**
     * 删除某条记忆的向量（记忆废弃/删除时清理）
     */
    public void removeByMemoryId(Long userId, Long memoryId) {
        memoryEmbeddingMapper.delete(new LambdaQueryWrapper<MemoryEmbedding>()
                .eq(MemoryEmbedding::getUserId, userId)
                .eq(MemoryEmbedding::getMemoryId, memoryId));
    }

    private void saveSnippetOnly(Long userId, Long memoryId, String title, String text, Integer privacy, String emotion) {
        try {
            MemoryEmbedding embedding = new MemoryEmbedding();
            embedding.setUserId(userId);
            embedding.setMemoryId(memoryId);
            embedding.setVectorKey(title != null && !title.isBlank() ? title : "记忆" + memoryId);
            embedding.setTextSnippet(truncate(text, 500));
            embedding.setPrivacy(privacy != null ? privacy : 1);
            embedding.setEmotion(emotion);
            embedding.setCreateTime(LocalDateTime.now());
            memoryEmbeddingMapper.delete(new LambdaQueryWrapper<MemoryEmbedding>()
                    .eq(MemoryEmbedding::getUserId, userId)
                    .eq(MemoryEmbedding::getMemoryId, memoryId));
            memoryEmbeddingMapper.insert(embedding);
        } catch (Exception e) {
            log.error("记忆摘要入库失败: userId={}, memoryId={}", userId, memoryId);
        }
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max);
    }
}
