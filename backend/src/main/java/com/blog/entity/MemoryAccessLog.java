package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 封存/隐私访问审计：谁在何时、针对哪条记忆做了什么。
 */
@Data
@TableName("memory_access_log")
public class MemoryAccessLog {

    public static final String ACTION_VIEW_SEALED = "view_sealed";
    public static final String ACTION_UNLOCK_SUCCESS = "unlock_success";
    public static final String ACTION_UNLOCK_FAIL = "unlock_fail";
    public static final String ACTION_PRIVACY_CHANGE = "privacy_change";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long memoryId;

    private Integer privacy;

    private String action;

    private String ipAddress;

    private String userAgent;

    private LocalDateTime createTime;
}
