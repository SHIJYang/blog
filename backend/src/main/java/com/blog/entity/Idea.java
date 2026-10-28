package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.blog.config.typehandler.PgTextArrayTypeHandler;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 念生：想法。
 * <p>
 * 向量列不进实体（与记忆域同一约定），读写走 {@code IdeaMapper} 的显式 SQL + {@code ::vector}。
 */
@Data
@TableName(value = "idea", autoResultMap = true)
public class Idea {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String content;

    /** 六态英文码：seed/incubating/validating/acting/crystallized/dormant */
    private String state;

    private Integer priority;

    /** 标签（V1 是 text[]，Java 侧逗号串） */
    @TableField(typeHandler = PgTextArrayTypeHandler.class)
    private String tags;

    /** 1-手写 2-由拾光闪光点升级 */
    private Integer source;

    /** 来源闪光点 ID（复制非引用，仅留痕） */
    private Long sourceInsightId;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
