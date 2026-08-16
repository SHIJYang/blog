package com.blog.vo;

import com.blog.entity.MemoryMessage;
import com.blog.entity.MemoryNote;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 记忆详情（日记视图 / 对话视图数据源）
 */
@Data
@Builder
public class MemoryDetailVo {

    private MemoryNote note;

    /** 全部对话消息（按时间正序） */
    private List<MemoryMessage> messages;

    /** 对话消息数 */
    private Long messageCount;

    /** 跨对话召回的相关历史记忆（AI 上下文溯源，供前端展示） */
    private List<RecallVo> relatedMemories;
}
