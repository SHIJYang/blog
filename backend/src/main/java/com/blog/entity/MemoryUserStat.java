package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户记忆统计表
 * 支撑「和念念的第 X 天、已记 X 段」统计，避免实时聚合查询
 */
@Data
@TableName("memory_user_stat")
public class MemoryUserStat {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /** 连续记录天数 */
    private Integer continueDays;

    /** 累计记忆条数 */
    private Integer totalMemory;

    /** 最后记录时间 */
    private LocalDateTime lastRecordTime;
}
