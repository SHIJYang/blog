package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class IdeaMoveDto {

    @NotBlank(message = "目标状态不能为空")
    private String toState;

    @Size(max = 255, message = "迁移理由过长")
    private String reason;
}
