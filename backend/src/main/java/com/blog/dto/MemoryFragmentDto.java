package com.blog.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 纯文本碎念入参（不再强制带图）。
 */
@Data
public class MemoryFragmentDto {

    @NotBlank(message = "碎念内容不能为空")
    @Size(max = 2000, message = "碎念内容不能超过 2000 字")
    private String content;

    /** 可选标题；不传则取正文首行前 16 字 */
    private String title;

    /** 可选情绪标签 */
    private List<String> emotions;

    /** 隐私等级 0-公开 1-私密（碎念不允许直接封存），默认私密 */
    private Integer privacy;
}
