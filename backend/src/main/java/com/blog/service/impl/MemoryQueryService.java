package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.BusinessException;
import com.blog.dto.MemoryQueryDto;
import com.blog.dto.MemorySearchDto;
import com.blog.vo.MemoryDetailVo;
import com.blog.vo.MemoryListVo;
import com.blog.vo.MemoryStatVo;
import com.blog.entity.MemoryEmbedding;
import com.blog.entity.MemoryMessage;
import com.blog.entity.MemoryNote;
import com.blog.entity.MemoryUserStat;
import com.blog.entity.SysUser;
import com.blog.mapper.MemoryEmbeddingMapper;
import com.blog.mapper.MemoryMessageMapper;
import com.blog.mapper.MemoryNoteMapper;
import com.blog.mapper.MemoryUserStatMapper;
import com.blog.mapper.SysUserMapper;
import com.blog.security.utils.SecurityUtils;
import com.blog.service.vector.MemoryVectorService;
import com.blog.util.DiaryFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 记忆查询与统计服务
 * <p>
 * 权限模型：普通用户只能查自己的记忆；管理员可查询所有用户的记忆（可传 targetUserId 定位单个用户）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryQueryService {

    private final MemoryNoteMapper memoryNoteMapper;
    private final MemoryMessageMapper memoryMessageMapper;
    private final MemoryEmbeddingMapper memoryEmbeddingMapper;
    private final MemoryUserStatMapper memoryUserStatMapper;
    private final SysUserMapper sysUserMapper;
    private final MemoryVectorService memoryVectorService;

    /**
     * 解析查询目标用户范围：
     * - 普通用户：强制查自己（忽略 targetUserId）
     * - 管理员 + targetUserId：查指定用户
     * - 管理员 + 空：查所有用户（返回 null 表示不过滤）
     */
    private Long resolveQueryUserId(Long currentUserId, Long targetUserId) {
        boolean admin = SecurityUtils.isAdmin();
        if (!admin) {
            // 普通用户只能看自己
            return currentUserId;
        }
        // 管理员
        if (targetUserId == null) {
            return null; // 全部
        }
        return targetUserId;
    }

    /**
     * 分页列表（管理员可传 targetUserId 查指定用户；不传则查全部）
     */
    public Page<MemoryListVo> pageList(Long currentUserId, Long targetUserId, MemoryQueryDto query) {
        LambdaQueryWrapper<MemoryNote> wrapper = new LambdaQueryWrapper<>();
        Long scope = resolveQueryUserId(currentUserId, targetUserId);
        if (scope != null) {
            wrapper.eq(MemoryNote::getUserId, scope);
        }
        if (query.getStatus() != null) {
            wrapper.eq(MemoryNote::getStatus, query.getStatus());
        }
        switch (StringUtils.defaultIfBlank(query.getOrder(), "create_desc")) {
            case "create_asc" -> wrapper.orderByAsc(MemoryNote::getCreateTime);
            case "finish_desc" -> wrapper.orderByDesc(MemoryNote::getFinishTime);
            default -> wrapper.orderByDesc(MemoryNote::getCreateTime);
        }
        Page<MemoryNote> page = memoryNoteMapper.selectPage(
                new Page<>(query.getCurrent(), query.getSize()), wrapper);

        return toPageVo(page);
    }

    /**
     * 全文搜索（管理员可查全部）
     */
    public Page<MemoryListVo> search(Long currentUserId, Long targetUserId, MemorySearchDto dto) {
        String keyword = StringUtils.trimToNull(dto.getKeyword());
        LambdaQueryWrapper<MemoryNote> wrapper = new LambdaQueryWrapper<MemoryNote>()
                .orderByDesc(MemoryNote::getCreateTime);
        Long scope = resolveQueryUserId(currentUserId, targetUserId);
        if (scope != null) {
            wrapper.eq(MemoryNote::getUserId, scope);
        }
        if (keyword != null) {
            List<Object> hitMemoryIds = memoryMessageMapper.selectObjs(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryMessage>()
                            .select("DISTINCT memory_id")
                            .like("content", keyword));
            wrapper.and(w -> w
                    .like(MemoryNote::getAutoTitle, keyword)
                    .or().like(MemoryNote::getFinalDiary, keyword)
                    .or().in(!hitMemoryIds.isEmpty(), MemoryNote::getId, hitMemoryIds));
        }
        Page<MemoryNote> page = memoryNoteMapper.selectPage(
                new Page<>(dto.getCurrent(), dto.getSize()), wrapper);

        return toPageVo(page);
    }

    /**
     * 记忆详情（管理员可查看任意记忆）
     */
    public MemoryDetailVo detail(Long currentUserId, Long targetUserId, Long memoryId) {
        MemoryNote note = memoryNoteMapper.selectById(memoryId);
        if (note == null) {
            throw new BusinessException(404, "记忆不存在");
        }
        boolean admin = SecurityUtils.isAdmin();
        // 普通用户只能看自己的；管理员可看任意（含他人）
        if (!admin && !note.getUserId().equals(currentUserId)) {
            throw new BusinessException(403, "无权访问该记忆");
        }
        List<MemoryMessage> messages = memoryMessageMapper.selectList(
                new LambdaQueryWrapper<MemoryMessage>()
                        .eq(MemoryMessage::getMemoryId, memoryId)
                        .orderByAsc(MemoryMessage::getCreateTime));
        return MemoryDetailVo.builder()
                .note(note)
                .messages(messages)
                .messageCount((long) messages.size())
                .relatedMemories(java.util.Collections.emptyList())
                .build();
    }

    /**
     * 用户统计（管理员可传 targetUserId 查指定用户；不传查全部聚合）
     */
    public Object stat(Long currentUserId, Long targetUserId) {
        Long scope = resolveQueryUserId(currentUserId, targetUserId);
        if (scope != null) {
            return statForUser(scope);
        }
        // 管理员查全部：聚合所有用户
        Long totalMemory = memoryNoteMapper.selectCount(
                new LambdaQueryWrapper<MemoryNote>()
                        .eq(MemoryNote::getStatus, MemoryNote.STATUS_CONDENSED));
        Long chattingCount = memoryNoteMapper.selectCount(
                new LambdaQueryWrapper<MemoryNote>()
                        .in(MemoryNote::getStatus, MemoryNote.STATUS_NOT_STARTED, MemoryNote.STATUS_CHATTING));
        Long userCount = memoryUserStatMapper.selectCount(null);
        return MemoryStatVo.builder()
                .continueDays(null)
                .totalMemory(totalMemory.intValue())
                .lastRecordTime(null)
                .chattingCount(chattingCount)
                .build()
                .withUserCount(userCount != null ? userCount.intValue() : 0);
    }

    private MemoryStatVo statForUser(Long userId) {
        MemoryUserStat stat = memoryUserStatMapper.selectOne(
                new LambdaQueryWrapper<MemoryUserStat>().eq(MemoryUserStat::getUserId, userId));
        Long chattingCount = memoryNoteMapper.selectCount(
                new LambdaQueryWrapper<MemoryNote>()
                        .eq(MemoryNote::getUserId, userId)
                        .in(MemoryNote::getStatus, MemoryNote.STATUS_NOT_STARTED, MemoryNote.STATUS_CHATTING));
        return MemoryStatVo.builder()
                .continueDays(stat != null ? stat.getContinueDays() : 0)
                .totalMemory(stat != null ? stat.getTotalMemory() : 0)
                .lastRecordTime(stat != null ? stat.getLastRecordTime() : null)
                .chattingCount(chattingCount)
                .build();
    }

    /**
     * 废弃记忆（普通用户只可废弃自己的；管理员可废弃任意）
     */
    public void abandon(Long currentUserId, Long targetUserId, Long memoryId) {
        MemoryNote note = memoryNoteMapper.selectById(memoryId);
        if (note == null) {
            throw new BusinessException(404, "记忆不存在");
        }
        boolean admin = SecurityUtils.isAdmin();
        if (!admin && !note.getUserId().equals(currentUserId)) {
            throw new BusinessException(403, "无权操作该记忆");
        }
        MemoryNote update = new MemoryNote();
        update.setId(memoryId);
        update.setStatus(MemoryNote.STATUS_ABANDONED);
        memoryNoteMapper.updateById(update);

        memoryEmbeddingMapper.delete(new LambdaQueryWrapper<MemoryEmbedding>()
                .eq(MemoryEmbedding::getUserId, note.getUserId())
                .eq(MemoryEmbedding::getMemoryId, memoryId));
    }

    /**
     * 编辑记忆（标题 / 情绪标签多选 / 正文）
     * 普通用户只能改自己的；管理员可改任意。
     *
     * @return 更新后的记忆详情
     */
    public MemoryNote updateMemory(Long currentUserId, Long targetUserId, Long memoryId,
                                   String autoTitle, List<String> emotions, String finalDiary) {
        MemoryNote note = memoryNoteMapper.selectById(memoryId);
        if (note == null) {
            throw new BusinessException(404, "记忆不存在");
        }
        boolean admin = SecurityUtils.isAdmin();
        if (!admin && !note.getUserId().equals(currentUserId)) {
            throw new BusinessException(403, "无权操作该记忆");
        }

        MemoryNote update = new MemoryNote();
        update.setId(memoryId);

        if (autoTitle != null) {
            String t = autoTitle.trim();
            if (t.isEmpty()) {
                throw new BusinessException(400, "标题不能为空");
            }
            update.setAutoTitle(t.length() > 128 ? t.substring(0, 128) : t);
        }

        if (emotions != null) {
            // 情绪标签多选 + 自定义：去空白、去重、限长
            List<String> cleaned = emotions.stream()
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .distinct()
                    .limit(12)
                    .collect(java.util.stream.Collectors.toList());
            update.setEmotions(String.join(",", cleaned));
        }

        if (finalDiary != null) {
            update.setFinalDiary(finalDiary);
        }

        if (update.getAutoTitle() != null || update.getEmotions() != null || update.getFinalDiary() != null) {
            memoryNoteMapper.updateById(update);
        }

        // 若改动了正文，同步更新向量库（原图内容变了，召回语义也要跟上）
        if (finalDiary != null) {
            try {
                String titleNow = StringUtils.defaultIfBlank(update.getAutoTitle(), note.getAutoTitle());
                String emotionsNow = update.getEmotions() != null ? update.getEmotions()
                        : (note.getEmotions() == null ? "" : note.getEmotions());
                String primaryEmotion = emotionsNow.isBlank() ? null : emotionsNow.split(",")[0].trim();
                String vectorText = titleNow + "\n" + finalDiary;
                memoryVectorService.storeEmbedding(
                        note.getUserId(), memoryId, titleNow, vectorText,
                        note.getPrivacy() != null ? note.getPrivacy() : 1, primaryEmotion);
            } catch (Exception e) {
                log.warn("编辑记忆后同步向量失败: memoryId={}, err={}", memoryId, e.getMessage());
            }
        }

        return memoryNoteMapper.selectById(memoryId);
    }

    /**
     * 设置记忆隐私等级（普通用户只能改自己的；管理员可改任意）
     */
    public MemoryNote setPrivacy(Long currentUserId, Long targetUserId, Long memoryId, Integer privacy) {
        MemoryNote note = memoryNoteMapper.selectById(memoryId);
        if (note == null) {
            throw new BusinessException(404, "记忆不存在");
        }
        boolean admin = SecurityUtils.isAdmin();
        if (!admin && !note.getUserId().equals(currentUserId)) {
            throw new BusinessException(403, "无权操作该记忆");
        }
        if (privacy == null || privacy < 0 || privacy > 2) {
            throw new BusinessException(400, "隐私等级需为 0(公开)/1(私密)/2(封存)");
        }
        Long ownerId = note.getUserId();
        MemoryNote update = new MemoryNote();
        update.setId(memoryId);
        update.setPrivacy(privacy);
        memoryNoteMapper.updateById(update);

        try {
            MemoryEmbedding emb = memoryEmbeddingMapper.selectOne(new LambdaQueryWrapper<MemoryEmbedding>()
                    .eq(MemoryEmbedding::getUserId, ownerId)
                    .eq(MemoryEmbedding::getMemoryId, memoryId));
            if (emb != null) {
                MemoryEmbedding embUpd = new MemoryEmbedding();
                embUpd.setId(emb.getId());
                embUpd.setPrivacy(privacy);
                memoryEmbeddingMapper.updateById(embUpd);
            }
        } catch (Exception e) {
            log.warn("同步向量隐私失败: memoryId={}, err={}", memoryId, e.getMessage());
        }

        return memoryNoteMapper.selectById(memoryId);
    }

    /**
     * 情绪标签统计（管理员查全部聚合；普通用户查自己）
     */
    public List<Map<String, Object>> listEmotionStats(Long currentUserId, Long targetUserId) {
        // emotions 是 text[]：不能写 .ne(x, "")（text[] 与 varchar 比较 PG 会直接报错），
        // 空数组在下面的 isBlank 判断里过滤
        LambdaQueryWrapper<MemoryNote> wrapper = new LambdaQueryWrapper<MemoryNote>()
                .isNotNull(MemoryNote::getEmotions);
        Long scope = resolveQueryUserId(currentUserId, targetUserId);
        if (scope != null) {
            wrapper.eq(MemoryNote::getUserId, scope);
        }
        List<MemoryNote> notes = memoryNoteMapper.selectList(wrapper);
        Map<String, Long> count = new HashMap<>();
        for (MemoryNote n : notes) {
            if (n.getEmotions() == null || n.getEmotions().isBlank()) continue;
            for (String e : n.getEmotions().split(",")) {
                String tag = e.trim();
                if (!tag.isEmpty()) count.merge(tag, 1L, Long::sum);
            }
        }
        return count.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("emotion", e.getKey());
                    item.put("count", e.getValue());
                    return item;
                })
                .collect(Collectors.toList());
    }

    // ---------------- 内部工具 ----------------

    private Page<MemoryListVo> toPageVo(Page<MemoryNote> page) {
        List<MemoryNote> records = page.getRecords();
        List<Long> ids = records.stream().map(MemoryNote::getId).collect(Collectors.toList());

        // 消息数
        Map<Long, Long> countMap = loadMessageCounts(ids);

        // 用户名（管理展示用）
        Map<Long, String> userNames = loadUsernames(records);

        Page<MemoryListVo> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(records.stream().map(n -> MemoryListVo.builder()
                .id(n.getId())
                .userId(n.getUserId())
                .username(userNames.get(n.getUserId()))
                .coverPhoto(n.getCoverPhoto())
                .coverThumb(n.getCoverThumb())
                .autoTitle(n.getAutoTitle())
                .diaryPreview(DiaryFormatter.preview(n.getFinalDiary(), 80))
                .status(n.getStatus())
                .privacy(n.getPrivacy() != null ? n.getPrivacy() : 1)
                .emotions(n.getEmotions())
                .messageCount(countMap.getOrDefault(n.getId(), 0L))
                .createTime(n.getCreateTime())
                .finishTime(n.getFinishTime())
                .build()).collect(Collectors.toList()));
        return result;
    }

    /**
     * 批量统计各记忆的对话消息数
     */
    private Map<Long, Long> loadMessageCounts(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        List<Map<String, Object>> counts = memoryMessageMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<MemoryMessage>()
                        .select("memory_id", "COUNT(*) AS cnt")
                        .in("memory_id", ids)
                        .groupBy("memory_id"));
        return counts.stream().collect(Collectors.toMap(
                m -> (Long) m.get("memory_id"),
                m -> ((Number) m.get("cnt")).longValue()));
    }

    /**
     * 批量加载记忆所属用户名（nickname 优先）
     */
    private Map<Long, String> loadUsernames(List<MemoryNote> records) {
        Map<Long, String> userNames = new HashMap<>();
        if (records == null || records.isEmpty()) {
            return userNames;
        }
        Set<Long> userIds = records.stream().map(MemoryNote::getUserId).collect(Collectors.toSet());
        List<SysUser> users = sysUserMapper.selectBatchIds(userIds);
        for (SysUser u : users) {
            userNames.put(u.getId(), StringUtils.defaultIfBlank(u.getNickname(), u.getUsername()));
        }
        return userNames;
    }
}
