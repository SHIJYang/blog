package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AiPromptSaveDto {

    @NotBlank(message = "scene 不能为空")
    @Size(max = 64, message = "scene 过长")
    private String scene;

    @NotBlank(message = "提示词内容不能为空")
    private String content;

    @Size(max = 255, message = "备注过长")
    private String remark;

    /** 创建后是否立即激活 */
    private Boolean activate;
}
