package com.blog.vo;

import lombok.Builder;
import lombok.Data;

/**
 * AI 回复出参
 */
@Data
@Builder
public class ChatReplyVo {

    /** AI 回复文本 */
    private String reply;

    /** 当前消息情绪标签（可能为空） */
    private String emotion;

    /** 当前记忆状态 */
    private Integer status;

    /** 是否触发了历史记忆召回 */
    private Boolean recalled;

    /** 召回的相关历史记忆 */
    private java.util.List<RecallVo> relatedMemories;
}
