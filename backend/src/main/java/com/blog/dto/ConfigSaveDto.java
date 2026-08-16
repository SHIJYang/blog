package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ConfigSaveDto {
    @NotBlank(message = "配置键不能为空")
    private String configKey;

    @NotBlank(message = "配置值不能为空")
    private String configValue;

    @NotBlank(message = "配置名称不能为空")
    private String configName;

    @NotNull(message = "是否公开不能为空")
    private Integer isPublic; // 0-私密, 1-公开
}