package com.blog.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 对话供应商
 * <p>
 * 独立存储于 ai_provider 表，每行一个供应商（OpenAI 兼容接口）。
 * 向量固定走 SiliconFlow，与本表无关。
 */
@Data
@TableName("ai_provider")
public class AiProvider {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 供应商展示名（如 Agnes / DeepSeek / MiMo） */
    private String name;

    /** OpenAI 兼容接口地址（如 https://api.agnes-ai.cn/v1/chat/completions） */
    private String baseUrl;

    /** API Key（真实值入库；前端回传空/脱敏值时保留原值） */
    private String apiKey;

    /** 对话模型名（如 agnes-2.5-flash） */
    private String model;

    /** 是否当前激活（仅一行 = 1） */
    private Integer isActive;

    /** 是否启用（0/1） */
    private Integer enabled;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
