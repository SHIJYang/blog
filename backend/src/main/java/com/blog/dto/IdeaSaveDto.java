package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class IdeaSaveDto {

    @NotBlank(message = "想法内容不能为空")
    @Size(max = 4000, message = "想法内容不能超过 4000 字")
    private String content;

    private List<String> tags;

    /** 优先级：越大越靠前（0~9） */
    private Integer priority;
}
