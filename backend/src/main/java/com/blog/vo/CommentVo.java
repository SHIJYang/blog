package com.blog.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentVo {
    private Long id;
    private Long articleId;
    private Long userId;
    private String nickname;
    private String email;
    private String website;
    private String content;
    private Long parentId;
    private String parentNickname; // 被回复人的昵称 (在平铺盖楼里，用于：@parentNickname)
    private Long rootId;
    private String avatar;          // 评论人头像 (若是登录注册用户则展示其头像)
    private LocalDateTime createTime;

    private List<CommentVo> replies; // 平铺展示在当前主评论下属的子回复列表
}