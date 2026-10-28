package com.blog.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 记忆照片（受控存储）。
 * <p>
 * storage=1 时 {@code url} 存的是相对 uploads/memory 的文件路径，
 * 对外一律通过 {@code /api/memory/photo/{id}} 鉴权读取，不再暴露磁盘路径；
 * storage=2 是历史 picui 公网图（D5 已接受的既成事实）。
 */
@Data
@TableName("memory_photo")
public class MemoryPhoto {

    /** 受控存储：经后端鉴权流式输出 */
    public static final int STORAGE_CONTROLLED = 1;
    /** 历史公网图床（picui），保持原 URL */
    public static final int STORAGE_PUBLIC = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long memoryId;

    private Long userId;

    /** storage=1 时为相对路径；storage=2 时为公网 URL */
    private String url;

    private String thumbUrl;

    private Integer width;

    private Integer height;

    private LocalDateTime takenAt;

    private Integer storage;

    private Integer sort;

    @TableLogic
    private Integer isDeleted;

    private LocalDateTime createTime;
}
