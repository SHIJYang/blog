package com.blog.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.common.Result;
import com.blog.dto.ConfigSaveDto;
import com.blog.entity.BlogConfig;
import com.blog.service.BlogConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/configs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // 仅限管理员操作
public class AdminConfigController {

    private final BlogConfigService blogConfigService;

    /**
     * 获取所有配置项，用于前端全局展示 (不分页，因为配置项通常不多)
     */
    @GetMapping("/all")
    public Result<?> getAll() {
        List<BlogConfig> configList = blogConfigService.getAllConfig();
        return Result.success(configList);
    }

    /**
     * 条件分页查询配置项 (用于后台管理列表)
     */
    @GetMapping("/page")
    public Result<?> getPage(
            @RequestParam(defaultValue = "1") Integer current,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        Page<BlogConfig> page = blogConfigService.getConfigPage(current, size, keyword);
        return Result.success(page);
    }

    /**
     * 通过 key 获取单个配置值
     */
    @GetMapping("/{key}")
    public Result<?> getByKey(@PathVariable String key) {
        String value = blogConfigService.getConfigValue(key);
        // 返回时，也需保障 value 的安全，尽管 Service 层已清洗
        // return Result.success(JsoupUtils.cleanHtml(value)); // 如果不信任 service 层，这里可再次清洗
        return Result.success(value);
    }

    /**
     * 创建新配置项
     */
    @PostMapping
    public Result<?> create(@Valid @RequestBody ConfigSaveDto dto) {
        blogConfigService.saveConfig(dto);
        return Result.success("系统配置创建成功");
    }

    /**
     * 更新现有配置项
     */
    @PutMapping
    public Result<?> update(@Valid @RequestBody ConfigSaveDto dto) {
        blogConfigService.updateConfig(dto);
        return Result.success("系统配置更新成功");
    }

    /**
     * 逻辑删除配置项
     */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        blogConfigService.removeConfig(id);
        return Result.success("系统配置删除成功");
    }
}