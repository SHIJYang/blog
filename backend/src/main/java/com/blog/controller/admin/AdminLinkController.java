package com.blog.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.Result;
import com.blog.dto.LinkSaveDto;
import com.blog.entity.BlogLink;
import com.blog.service.BlogLinkService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/links")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // 仅限管理员操作
public class AdminLinkController {

    private final BlogLinkService blogLinkService;

    @GetMapping("/page")
    public Result<?> getPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        Page<BlogLink> page = blogLinkService.getLinkPage(current, size, keyword);
        return Result.success(page);
    }

    @PostMapping
    public Result<?> create(@Valid @RequestBody LinkSaveDto dto) {
        blogLinkService.saveLink(dto);
        return Result.success("友情链接创建成功");
    }

    @PutMapping
    public Result<?> update(@Valid @RequestBody LinkSaveDto dto) {
        blogLinkService.updateLink(dto);
        return Result.success("友情链接更新成功");
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        blogLinkService.removeLink(id);
        return Result.success("友情链接删除成功");
    }
}