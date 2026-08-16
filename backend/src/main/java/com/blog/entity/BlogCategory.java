package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("blog_category")
public class BlogCategory {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String description;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}