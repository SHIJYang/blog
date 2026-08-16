package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.entity.MemoryNote;
import com.blog.mapper.MemoryMessageMapper;
import com.blog.mapper.MemoryNoteMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 拾光碎念：对话冷场自动浓缩
 * <p>
 * 用户一段时间（默认 2 分钟）没有对话后，自动把「对话中(2)」的记忆凝聚为日记。
 * 避免用户忘记手动浓缩，让记忆自然沉淀为「已成念(3)」。
 * <p>
 * 使用 @Scheduled 定时扫描，需在主类开启 @EnableScheduling。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryAutoCondenseService {

    private final MemoryNoteMapper memoryNoteMapper;
    private final MemoryMessageMapper memoryMessageMapper;
    private final MemoryCoreService memoryCoreService;

    /** 冷场判定时长（分钟），默认 2 分钟 */
    @Value("${memory.auto-condense-idle-minutes:2}")
    private int idleMinutes;

    /** 单轮最多自动浓缩条数（避免并发把 AI 打爆） */
    @Value("${memory.auto-condense-max-per-round:5}")
    private int maxPerRound;

    /**
     * 每 30 秒扫描一次「对话中」且最后消息距今超过 idleMinutes 的记忆
     */
    @Scheduled(fixedDelay = 30_000)
    public void autoCondenseIdleChats() {
        try {
            // 1. 所有「对话中(2)」记忆
            List<MemoryNote> chatting = memoryNoteMapper.selectList(
                    new LambdaQueryWrapper<MemoryNote>()
                            .eq(MemoryNote::getStatus, MemoryNote.STATUS_CHATTING));
            if (chatting.isEmpty()) {
                return;
            }

            // 2. 按用户分组，查每用户每条记忆的最后消息时间
            Map<Long, Timestamp> latestTimes = collectLatestTimes(chatting);

            LocalDateTime now = LocalDateTime.now();
            int condensed = 0;

            for (MemoryNote note : chatting) {
                if (condensed >= maxPerRound) {
                    log.info("自动浓缩已达单轮上限 {}，本轮到此为止", maxPerRound);
                    break;
                }
                if (note.getUserId() == null) continue;

                Timestamp ts = latestTimes.get(note.getId());
                if (ts == null) {
                    continue; // 无消息，跳过
                }
                LocalDateTime lastMsg = ts.toLocalDateTime();
                long idleSeconds = Duration.between(lastMsg, now).getSeconds();
                if (idleSeconds < idleMinutes * 60L) {
                    continue; // 还在活跃窗口内，不浓缩
                }

                try {
                    memoryCoreService.condense(note.getUserId(), note.getId(), null);
                    condensed++;
                    log.info("自动浓缩完成: userId={}, memoryId={}, idleSeconds={}",
                            note.getUserId(), note.getId(), idleSeconds);
                } catch (Exception e) {
                    // 单条失败不影响其它（可能已在别处浓缩、或 AI 失败）
                    log.warn("自动浓缩失败: memoryId={}, err={}", note.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("自动浓缩扫描异常: {}", e.getMessage());
        }
    }

    /**
     * 聚合各记忆的最后消息时间（memoryId -> Timestamp）
     */
    private Map<Long, Timestamp> collectLatestTimes(List<MemoryNote> chatting) {
        Map<Long, Timestamp> map = new HashMap<>();
        Map<Long, List<MemoryNote>> byUser = chatting.stream()
                .collect(Collectors.groupingBy(MemoryNote::getUserId));
        for (Map.Entry<Long, List<MemoryNote>> entry : byUser.entrySet()) {
            try {
                List<MemoryMessageMapper.MemoryLikeMap> rows =
                        memoryMessageMapper.selectLatestTimes(entry.getKey());
                if (rows != null) {
                    for (MemoryMessageMapper.MemoryLikeMap row : rows) {
                        if (row.memoryId != null && row.latestTime != null) {
                            map.put(row.memoryId, row.latestTime);
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("查询最后消息时间失败: userId={}, err={}", entry.getKey(), e.getMessage());
            }
        }
        return map;
    }
}
