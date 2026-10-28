package com.blog.controller;

import com.blog.common.Result;
import com.blog.service.impl.MemoryQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 公开记忆读面（D14）。
 * <p>
 * 未登录可读，只返回 privacy=0 且已成念的记忆；作者信息只出昵称与头像。
 */
@RestController
@RequestMapping("/api/public/memories")
@RequiredArgsConstructor
public class PublicMemoryController {

    private final MemoryQueryService memoryQueryService;

    /**
     * 公开记忆分页
     *
     * @param current 页码
     * @param size    每页条数（上限 50）
     * @param year    按年筛选（finish_time）
     * @param month   按月筛选
     * @param emotion 按情绪筛选（精确匹配某个情绪标签）
     */
    @GetMapping("/page")
    public Result<?> page(@RequestParam(defaultValue = "1") Integer current,
                          @RequestParam(defaultValue = "10") Integer size,
                          @RequestParam(required = false) Integer year,
                          @RequestParam(required = false) Integer month,
                          @RequestParam(required = false) String emotion) {
        return Result.success(memoryQueryService.publicPage(current, size, year, month, emotion));
    }

    /** 公开记忆详情（非 privacy=0 一律 404，不区分"不存在"与"无权"） */
    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id) {
        return Result.success(memoryQueryService.publicDetail(id));
    }
}
