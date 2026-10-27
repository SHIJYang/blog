package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ArticleSaveDto {
    private Long id; // 修改时传，创建时不传

    @NotBlank(message = "文章标题不能为空")
    private String title;

    @NotBlank(message = "文章内容不能为空")
    private String content;

    private String summary;
    private String coverImage;

    @NotNull(message = "文章状态不能为空")
    private Integer status; // 0-草稿, 1-公开已发布, 2-私密/仅自己可见

    private Integer allowComment; // 0-不允许, 1-允许

    private List<Long> categoryIds; // 关联的分类 ID 集合
    private List<Long> tagIds;       // 关联的标签 ID 集合
}