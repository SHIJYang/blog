package com.blog.vo;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公开记忆视图（D14）。
 * <p>
 * 只暴露作者昵称与头像，**不返回 user_id / username / email**，
 * 避免"读公开记忆"变成用户身份检索。
 */
@Data
@Builder
public class PublicMemoryVo {

    private Long id;
    private String title;
    /** 正文（列表页只给摘要，详情给全文） */
    private String diary;
    private String coverPhoto;
    private String emotions;
    private LocalDateTime finishTime;
    private LocalDateTime createTime;
    private String authorNickname;
    private String authorAvatar;
}
