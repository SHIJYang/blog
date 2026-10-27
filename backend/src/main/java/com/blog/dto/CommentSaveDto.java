package com.blog.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentSaveDto {
    @NotNull(message = "文章ID不能为空")
    private Long articleId;

    // 游客必须填写该昵称，登录用户会自动使用账号信息
    private String nickname;

    @Email(message = "邮箱格式不正确")
    private String email;

    private String website; // 评论者的网站

    @NotBlank(message = "评论内容不能为空")
    private String content;

    private Long parentId; // 被回复的那条评论 ID (可空，为空说明是直接针对文章的主评论)
}