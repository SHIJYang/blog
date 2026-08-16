package com.blog.vo;

import lombok.Builder;
import lombok.Data;

/**
 * 跨对话召回的历史记忆
 */
@Data
@Builder
public class RecallVo {

    private Long memoryId;
    /** 记忆标题 */
    private String title;
    /** 摘要片段 */
    private String snippet;
    /** 主情绪标签 */
    private String emotion;
    /** 相似度得分 (0-1) */
    private Double score;
}
