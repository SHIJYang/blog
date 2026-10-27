package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_tag")
public class BlogTag {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;

    /** PG 没有 ON UPDATE CURRENT_TIMESTAMP，由 MetaObjectHandler 自动回填 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}