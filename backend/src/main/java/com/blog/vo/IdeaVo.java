package com.blog.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 念生想法视图：比实体多带状态字典信息与「当前状态允许迁移到哪」。
 */
@Data
@Builder
public class IdeaVo {

    private Long id;
    private Long userId;
    private String username;
    private String content;
    private String state;
    private String stateName;
    private String stateColor;
    private Integer isTerminal;
    private Integer priority;
    private String tags;
    private Integer source;
    private Long sourceInsightId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    /** 当前状态允许迁移的目标状态码 */
    private List<String> allowedTargets;
}
