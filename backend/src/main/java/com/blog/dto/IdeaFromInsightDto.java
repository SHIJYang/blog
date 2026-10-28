package com.blog.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class IdeaFromInsightDto {

    @NotNull(message = "insightId 不能为空")
    private Long insightId;
}
