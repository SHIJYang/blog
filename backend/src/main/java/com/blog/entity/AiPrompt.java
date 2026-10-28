package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Prompt 治理：按 scene 版本化，同一 scene 只允许一条 is_active=1。
 */
@Data
@TableName("ai_prompt")
public class AiPrompt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String scene;

    private Integer version;

    private String content;

    private Integer isActive;

    private String remark;

    private LocalDateTime createTime;
}
