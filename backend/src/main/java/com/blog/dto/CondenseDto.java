package com.blog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 凝聚记忆入参
 */
@Data
public class CondenseDto {

    @NotNull(message = "记忆ID不能为空")
    private Long memoryId;

    /** 可选：用户自定义标题（为空则用 AI 自动标题） */
    private String customTitle;
}
