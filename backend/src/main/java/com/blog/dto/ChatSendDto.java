package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发送对话消息入参
 */
@Data
public class ChatSendDto {

    /** 记忆ID */
    @NotNull(message = "记忆ID不能为空")
    private Long memoryId;

    /** 用户消息文本 */
    @NotBlank(message = "消息内容不能为空")
    private String content;

    /** 图形验证码（AI 接口防刷）。10 分钟免验证窗口内可空 */
    private String captchaCode;

    /** 验证码 KEY（获取验证码时下发）。10 分钟免验证窗口内可空 */
    private String captchaKey;
}
