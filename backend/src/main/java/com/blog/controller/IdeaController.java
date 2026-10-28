package com.blog.controller;

import com.blog.common.Result;
import com.blog.dto.IdeaFromInsightDto;
import com.blog.dto.IdeaMoveDto;
import com.blog.dto.IdeaSaveDto;
import com.blog.entity.IdeaAnalysis;
import com.blog.entity.IdeaTransition;
import com.blog.security.utils.SecurityUtils;
import com.blog.service.impl.IdeaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 念生：想法工作台。
 * <p>
 * 普通用户只能操作自己的想；管理员可传 {@code targetUserId} 查看指定用户。
 */
@RestController
@RequestMapping("/api/idea")
@RequiredArgsConstructor
public class IdeaController {

    private final IdeaService ideaService;

    @GetMapping("/states")
    public Result<?> states() {
        return Result.success(ideaService.states());
    }

    /** 看板：按六态分组（管理员不传 targetUserId 则看全部用户） */
    @GetMapping("/board")
    public Result<?> board(@RequestParam(value = "targetUserId", required = false) Long targetUserId) {
        Long current = SecurityUtils.getUserId();
        return Result.success(ideaService.board(current, targetUserId));
    }

    @PostMapping
    public Result<?> create(@Valid @RequestBody IdeaSaveDto dto) {
        Long current = SecurityUtils.getUserId();
        return Result.success(ideaService.create(current, dto));
    }

    /** 拾光闪光点一键升级为想法 */
    @PostMapping("/from-insight")
    public Result<?> fromInsight(@Valid @RequestBody IdeaFromInsightDto dto) {
        Long current = SecurityUtils.getUserId();
        return Result.success(ideaService.createFromInsight(current, dto.getInsightId()));
    }

    @GetMapping("/{id}")
    public Result<?> detail(@PathVariable Long id,
                            @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
        Long current = SecurityUtils.getUserId();
        return Result.success(ideaService.detail(current, targetUserId, id));
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable Long id,
                            @Valid @RequestBody IdeaSaveDto dto,
                            @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
        Long current = SecurityUtils.getUserId();
        return Result.success(ideaService.update(current, targetUserId, id, dto));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable Long id,
                            @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
        Long current = SecurityUtils.getUserId();
        ideaService.delete(current, targetUserId, id);
        return Result.success("已删除");
    }

    /** 状态迁移：非法迁移返回 409 */
    @PatchMapping("/{id}/move")
    public Result<?> move(@PathVariable Long id,
                          @Valid @RequestBody IdeaMoveDto dto,
                          @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
        Long current = SecurityUtils.getUserId();
        return Result.success(ideaService.move(current, targetUserId, id, dto.getToState(), dto.getReason()));
    }

    @GetMapping("/{id}/history")
    public Result<?> history(@PathVariable Long id,
                             @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
        Long current = SecurityUtils.getUserId();
        List<IdeaTransition> list = ideaService.history(current, targetUserId, id);
        return Result.success(list);
    }

    @PostMapping("/{id}/analyze")
    public Result<?> analyze(@PathVariable Long id,
                             @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
        Long current = SecurityUtils.getUserId();
        return Result.success(ideaService.analyze(current, targetUserId, id, IdeaAnalysis.KIND_ANALYZE));
    }

    @PostMapping("/{id}/challenge")
    public Result<?> challenge(@PathVariable Long id,
                               @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
        Long current = SecurityUtils.getUserId();
        return Result.success(ideaService.analyze(current, targetUserId, id, IdeaAnalysis.KIND_CHALLENGE));
    }

    @GetMapping("/{id}/analyses")
    public Result<?> analyses(@PathVariable Long id,
                              @RequestParam(value = "kind", required = false) String kind,
                              @RequestParam(value = "targetUserId", required = false) Long targetUserId) {
        Long current = SecurityUtils.getUserId();
        return Result.success(ideaService.analyses(current, targetUserId, id, kind));
    }
}
