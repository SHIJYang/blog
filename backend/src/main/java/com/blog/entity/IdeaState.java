package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 念生六态字典（表驱动，不写死在代码里）。
 */
@Data
@TableName("idea_state")
public class IdeaState {

    @TableId(type = IdType.INPUT)
    private String code;

    private String name;

    private Integer sort;

    private Integer isTerminal;

    private String color;
}
