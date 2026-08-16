package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.entity.MemoryNote;
import com.blog.entity.MemoryUserStat;
import com.blog.mapper.MemoryNoteMapper;
import com.blog.mapper.MemoryUserStatMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 用户记忆统计服务
 * 维护「和念念的第 X 天」「已记 X 段」数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryStatService {

    private final MemoryUserStatMapper memoryUserStatMapper;
    private final MemoryNoteMapper memoryNoteMapper;

    /**
     * 记忆凝聚成功后调用：更新连续天数与累计条数
     */
    @Transactional(rollbackFor = Exception.class)
    public void onMemoryCondensed(Long userId) {
        MemoryUserStat stat = memoryUserStatMapper.selectOne(
                new LambdaQueryWrapper<MemoryUserStat>().eq(MemoryUserStat::getUserId, userId));

        if (stat == null) {
            stat = new MemoryUserStat();
            stat.setUserId(userId);
            stat.setContinueDays(1);
            stat.setTotalMemory(1);
            stat.setLastRecordTime(LocalDateTime.now());
            memoryUserStatMapper.insert(stat);
            return;
        }

        // 连续天数计算：今天有记录 +1；昨天有记录保持不变；更早就重置为 1
        LocalDate today = LocalDate.now();
        LocalDate lastDate = stat.getLastRecordTime() != null ? stat.getLastRecordTime().toLocalDate() : null;
        int continueDays = stat.getContinueDays() == null ? 1 : stat.getContinueDays();
        if (lastDate != null) {
            long gap = ChronoUnit.DAYS.between(lastDate, today);
            if (gap == 1) {
                continueDays += 1;
            } else if (gap > 1) {
                continueDays = 1;
            }
            // gap == 0 同一天多次凝聚，天数不变
        }

        MemoryUserStat update = new MemoryUserStat();
        update.setId(stat.getId());
        update.setContinueDays(continueDays);
        update.setTotalMemory((stat.getTotalMemory() == null ? 0 : stat.getTotalMemory()) + 1);
        update.setLastRecordTime(LocalDateTime.now());
        memoryUserStatMapper.updateById(update);
    }

    /**
     * 初始化统计（创建记忆时兜底）
     */
    public void ensureStat(Long userId) {
        Long count = memoryUserStatMapper.selectCount(
                new LambdaQueryWrapper<MemoryUserStat>().eq(MemoryUserStat::getUserId, userId));
        if (count == 0) {
            MemoryUserStat stat = new MemoryUserStat();
            stat.setUserId(userId);
            stat.setContinueDays(0);
            stat.setTotalMemory(0);
            memoryUserStatMapper.insert(stat);
        }
    }
}
