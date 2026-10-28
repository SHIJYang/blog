package com.blog.controller.admin;

import com.blog.common.Result;
import com.blog.dto.AiPromptSaveDto;
import com.blog.service.impl.IdeaPromptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Prompt 治理（念生分析 / 质疑）。按 scene 版本化，可 A/B、可回滚。
 */
@RestController
@RequestMapping("/api/admin/ai-prompts")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminAiPromptController {

    private final IdeaPromptService promptService;

    @GetMapping
    public Result<?> list(@RequestParam(value = "scene", required = false) String scene) {
        return Result.success(scene == null || scene.isBlank()
                ? promptService.listAll()
                : promptService.listByScene(scene));
    }

    @GetMapping("/active")
    public Result<?> active(@RequestParam("scene") String scene) {
        return Result.success(promptService.activePrompt(scene));
    }

    @PostMapping
    public Result<?> create(@Valid @RequestBody AiPromptSaveDto dto) {
        return Result.success(promptService.create(dto));
    }

    @PostMapping("/{id}/activate")
    public Result<?> activate(@PathVariable Long id) {
        promptService.activate(id);
        return Result.success("已激活");
    }
}
