package com.blog.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 用户记忆统计出参
 */
@Data
@Builder
public class MemoryStatVo {

    /** 和念念的第几天（连续记录天数） */
    private Integer continueDays;

    /** 累计记忆条数（已成念） */
    private Integer totalMemory;

    /** 最后记录时间 */
    private java.time.LocalDateTime lastRecordTime;

    /** 进行中的记忆数（对话中/未开始） */
    private Long chattingCount;

    /** 有记忆的用户数（管理员查全部时返回） */
    private Integer userCount;

    /** 链式附加 userCount（避免 Lombok @Builder 无该方法） */
    public MemoryStatVo withUserCount(Integer userCount) {
        this.userCount = userCount;
        return this;
    }
}
