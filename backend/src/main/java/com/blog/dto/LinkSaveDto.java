package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LinkSaveDto {
    private Long id; // 修改时传入

    @NotBlank(message = "链接名称不能为空")
    private String name;

    @NotBlank(message = "链接地址不能为空")
    private String url;

    private String logo;
    private String description;

    @NotNull(message = "排序值不能为空")
    private Integer sort;

    @NotNull(message = "显示状态不能为空")
    private Integer status; // 0-隐藏, 1-显示
}