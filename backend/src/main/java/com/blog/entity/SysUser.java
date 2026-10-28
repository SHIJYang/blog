package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String password;

    private String nickname;

    private String email;

    private String avatar;

    /**
     * 角色: ADMIN(管理员), AUTHOR(作者), READER(读者)
     */
    private String role;

    /**
     * 状态: 0-禁用, 1-启用
     */
    private Integer status;

    /**
     * 逻辑删除：0-未删除, 1-已删除
     */
    @TableLogic
    private Integer isDeleted;

    /** 封存解锁 PIN（BCrypt）。为空表示未设置，封存记忆一律不可读 */
    private String privacyPinHash;

    /** 连续失败次数，达到 5 次锁定 30 分钟 */
    private Integer pinFailedCount;

    /** 锁定截止时间 */
    private LocalDateTime pinLockedUntil;

    /** PIN 修改时间：早于此时间签发的解锁令牌一律失效 */
    private LocalDateTime pinUpdatedAt;

    private LocalDateTime createTime;

    /** PG 没有 ON UPDATE CURRENT_TIMESTAMP，由 MetaObjectHandler 自动回填 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}