package com.blog.dto;

import lombok.Data;

/**
 * 记忆列表查询入参
 */
@Data
public class MemoryQueryDto {

    /** 页码 */
    private Integer current = 1;

    /** 每页条数 */
    private Integer size = 10;

    /** 状态筛选：1-未开始 2-对话中 3-已成念 4-已废弃（空=全部） */
    private Integer status;

    /** 排序：create_desc(默认) / create_asc / finish_desc */
    private String order = "create_desc";
}
