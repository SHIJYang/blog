package com.blog.controller.admin;

import com.blog.common.Result;
import com.blog.entity.AiProvider;
import com.blog.service.ai.AiProviderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * AI 对话供应商管理（数据库版）
 * <p>
 * 供应商入 ai_provider 表，支持增删改查、激活/禁用切换。
 * 向量固定走 SiliconFlow，与此无关。
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/ai-provider")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AiProviderController {

    private final AiProviderService aiProviderService;

    // 默认供应商的初始化不放在这里：@PostConstruct 与 Flyway 谁先跑没有保证，
    // 空库首启会先查 ai_provider 然后炸掉。已移到 config/StartupTasksConfig 的 ApplicationRunner。

    /** 查询全部供应商 */
    @GetMapping
    public Result<?> list() {
        List<AiProvider> list = aiProviderService.listProviders();
        // 脱敏 Key
        for (AiProvider p : list) {
            if (p.getApiKey() != null && !p.getApiKey().isBlank()) {
                p.setApiKey(mask(p.getApiKey()));
            }
        }
        return Result.success(list);
    }

    /** 新增 */
    @PostMapping
    public Result<?> create(@RequestBody AiProvider provider) {
        return Result.success(aiProviderService.create(provider));
    }

    /** 更新 */
    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id, @RequestBody AiProvider provider) {
        provider.setId(id);
        return Result.success(aiProviderService.update(provider));
    }

    /** 删除 */
    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id) {
        aiProviderService.delete(id);
        return Result.success("已删除");
    }

    /** 激活 */
    @PostMapping("/{id}/activate")
    public Result<?> activate(@PathVariable Long id) {
        aiProviderService.activate(id);
        return Result.success("已激活");
    }

    /** 测试连通性 */
    @PostMapping("/{id}/test")
    public Result<?> test(@PathVariable Long id) {
        Map<String, String> info = aiProviderService.testConnection(id);
        return Result.success(info);
    }

    private String mask(String key) {
        if (key.length() < 6) return "***";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 2);
    }
}
