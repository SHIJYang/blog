package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.entity.MemoryInsight;
import com.blog.mapper.MemoryInsightMapper;
import com.blog.service.ai.AgnesAiClient;
import com.blog.service.ai.VectorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆闪光点服务（拾光碎念）
 * <p>
 * 凝聚后由 AI 提炼 1-5 条「闪光点」（值得被记住的具体亮点，宁缺毋滥），
 * 用户也可手动添加/删除自己的闪光点。每条独立向量化存储，
 * 后续聊天可按语义精确提起某一段闪光点。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryInsightService {

    private final MemoryInsightMapper memoryInsightMapper;
    private final AgnesAiClient agnesAiClient;

    /**
     * 用 AI 把日记/对话提炼成若干「记忆闪光点」
     *
     * @param diary    日记正文
     * @param memories 记忆标题/情绪（辅助上下文）
     * @return 提炼后的闪光点列表（去空、去重，最多 5 条）
     */
    public List<String> extractInsights(String diary, String memories) {
        if (StringUtils.isBlank(diary) && StringUtils.isBlank(memories)) {
            return List.of();
        }
        String context = diary;
        if (StringUtils.isNotBlank(memories)) {
            context = memories + "\n" + StringUtils.defaultString(diary);
        }
        try {
            String prompt = "下面是一段私人记忆。请提炼出其中最值得被记住的「闪光点」："
                    + "具体的事、真实的情绪、有画面感的瞬间、自己在意的小细节。\n"
                    + "要求：\n"
                    + "- 每条只保留一个独立、具体、有信息量的点，不要流水账、不要复述全文、不要空泛的感慨。\n"
                    + "- 用第一人称「我」，语气自然像自己会记住的那句话。\n"
                    + "- 最多 5 条，每条不超过 40 字；如果没有值得记住的，输出空。\n"
                    + "- 每条独立成一行，不要编号。\n\n记忆内容：\n" + context;
            String raw = agnesAiClient.chat(
                    "你是拾光碎念的闪光点提炼器，只提炼真实、具体、值得被记住的瞬间，不编造、不空泛。",
                    List.of(prompt), 500, 0.5);
            if (StringUtils.isBlank(raw)) return List.of();
            List<String> lines = new ArrayList<>();
            for (String line : raw.split("\\n")) {
                String t = cleanLine(line);
                if (!t.isEmpty() && !lines.contains(t)) {
                    lines.add(t);
                }
                if (lines.size() >= 5) break;
            }
            log.info("闪光点提炼: inputLen={}, 提炼出 {} 条", context.length(), lines.size());
            return lines;
        } catch (Exception e) {
            log.warn("闪光点提炼失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 用户手动添加一条闪光点（自动向量化，后续聊天可提起）
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryInsight addInsight(Long userId, Long memoryId, String content) {
        if (StringUtils.isBlank(content)) {
            throw new BusinessException(400, "闪光点内容不能为空");
        }
        String text = content.trim();
        if (text.length() > 200) {
            text = text.substring(0, 200);
        }
        MemoryInsight insight = new MemoryInsight();
        insight.setUserId(userId);
        insight.setMemoryId(memoryId);
        insight.setContent(text);
        insight.setSource(MemoryInsight.SOURCE_USER);
        insight.setCreateTime(LocalDateTime.now());
        String embedding = null;
        try {
            float[] vec = agnesAiClient.embed(text);
            if (vec != null && vec.length > 0) {
                embedding = VectorUtil.toJson(VectorUtil.normalize(vec));
            }
        } catch (Exception e) {
            log.warn("闪光点向量化失败: {}", e.getMessage());
        }
        // 向量列不进实体，写入必须走带 ::vector 转型的专用 SQL
        if (embedding != null) {
            memoryInsightMapper.insertWithVector(insight, embedding);
        } else {
            memoryInsightMapper.insertWithoutVector(insight);
        }
        log.info("用户添加闪光点: userId={}, memoryId={}, id={}, 含向量={}", userId, memoryId, insight.getId(), embedding != null);
        return insight;
    }

    /**
     * 删除一条闪光点（仅本人/管理员）
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteInsight(Long userId, Long insightId, boolean admin) {
        MemoryInsight insight = memoryInsightMapper.selectById(insightId);
        if (insight == null) {
            throw new BusinessException(404, "闪光点不存在");
        }
        if (!admin && !insight.getUserId().equals(userId)) {
            throw new BusinessException(403, "无权删除他人的闪光点");
        }
        memoryInsightMapper.deleteById(insightId);
        log.info("删除闪光点: id={}", insightId);
    }

    /**
     * 为一条记忆生成并保存记忆闪光点（每人 1-N 条，每条独立向量）
     */
    @Transactional(rollbackFor = Exception.class)
    public List<MemoryInsight> generateForMemory(Long userId, Long memoryId,
                                                  String title, String diary, String conversation) {
        // 1. 只清掉 AI 自动生成的旧闪光点（保留用户手动添加的）
        memoryInsightMapper.delete(new LambdaQueryWrapper<MemoryInsight>()
                .eq(MemoryInsight::getUserId, userId)
                .eq(MemoryInsight::getMemoryId, memoryId)
                .eq(MemoryInsight::getSource, MemoryInsight.SOURCE_AI));

        String source = StringUtils.defaultString(diary) + "\n" + StringUtils.defaultString(conversation);
        List<String> points = extractInsights(source, title);
        if (points.isEmpty()) return List.of();

        List<MemoryInsight> saved = new ArrayList<>();
        for (String pt : points) {
            MemoryInsight insight = new MemoryInsight();
            insight.setUserId(userId);
            insight.setMemoryId(memoryId);
            insight.setContent(pt.length() > 500 ? pt.substring(0, 500) : pt);
            insight.setSource(MemoryInsight.SOURCE_AI);
            insight.setCreateTime(LocalDateTime.now());
            // 独立向量化（失败则该条只入库无向量，语义召回用不上，列表展示不受影响）
            String embedding = null;
            try {
                float[] vec = agnesAiClient.embed(pt);
                if (vec != null && vec.length > 0) {
                    embedding = VectorUtil.toJson(VectorUtil.normalize(vec));
                }
            } catch (Exception e) {
                log.warn("闪光点向量化失败: {}", e.getMessage());
            }
            if (embedding != null) {
                memoryInsightMapper.insertWithVector(insight, embedding);
            } else {
                memoryInsightMapper.insertWithoutVector(insight);
            }
            saved.add(insight);
        }
        log.info("闪光点入库完成: userId={}, memoryId={}, 共 {} 条", userId, memoryId, saved.size());
        return saved;
    }

    /**
     * 查询某条记忆的闪光点（前端列表/详情展示）
     */
    public List<MemoryInsight> listByMemoryId(Long userId, Long memoryId) {
        return memoryInsightMapper.selectList(new LambdaQueryWrapper<MemoryInsight>()
                .eq(MemoryInsight::getUserId, userId)
                .eq(MemoryInsight::getMemoryId, memoryId)
                .orderByAsc(MemoryInsight::getId));
    }

    private String cleanLine(String line) {
        if (line == null) return "";
        String t = line.trim()
                .replaceAll("^[-•*\\d.、\\s]+", "")   // 去序号/项目符号
                .replaceAll("^\"|“|”|\"$", "")          // 去首尾引号
                .trim();
        // 去除可能的“1. ”等残留
        return t;
    }
}
