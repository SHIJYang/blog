package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_link")
public class BlogLink {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String url;

    private String logo;

    private String description;

    private Integer sort; // 排序值

    /**
     * 状态: 0-隐藏, 1-显示
     */
    private Integer status;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}