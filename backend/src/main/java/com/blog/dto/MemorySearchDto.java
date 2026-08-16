package com.blog.dto;

import lombok.Data;

/**
 * 记忆全文搜索入参
 */
@Data
public class MemorySearchDto {

    /** 搜索关键词（匹配标题、对话内容、日记正文） */
    private String keyword;

    private Integer current = 1;

    private Integer size = 10;
}
