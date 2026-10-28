package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 状态迁移流水（可完整回放）。
 */
@Data
@TableName("idea_transition")
public class IdeaTransition {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long ideaId;

    private Long userId;

    private String fromState;

    private String toState;

    /** manual / ai / rule */
    private String trigger;

    private String reason;

    private LocalDateTime createTime;
}
