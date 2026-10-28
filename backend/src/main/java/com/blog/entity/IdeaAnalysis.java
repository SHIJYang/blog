package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 分析 / 质疑结果（只追加，永不覆盖原文）。
 */
@Data
@TableName("idea_analysis")
public class IdeaAnalysis {

    public static final String KIND_ANALYZE = "analyze";
    public static final String KIND_CHALLENGE = "challenge";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ideaId;

    private Long userId;

    /** analyze / challenge */
    private String kind;

    private String model;

    private String promptScene;

    /** jsonb 文本：{claims,assumptions,risks,questions} 或降级时的 {degraded,error} */
    private String resultJson;

    /** 1=模型输出无法解析，降级为纯文本 */
    private Integer degraded;

    private LocalDateTime createTime;
}
