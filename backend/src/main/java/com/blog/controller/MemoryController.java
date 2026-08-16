package com.blog.controller;

import com.blog.common.Result;
import com.blog.dto.ChatSendDto;
import com.blog.dto.CondenseDto;
import com.blog.dto.MemoryQueryDto;
import com.blog.dto.MemorySearchDto;
import com.blog.vo.ChatReplyVo;
import com.blog.entity.MemoryMessage;
import com.blog.entity.MemoryNote;
import com.blog.mapper.MemoryNoteMapper;
import com.blog.service.CaptchaService;
import com.blog.service.MemoryPublishService;
import com.blog.service.impl.MemoryCreateService;
import com.blog.service.impl.MemoryCoreService;
import com.blog.service.impl.MemoryEmotionService;
import com.blog.service.impl.MemoryInsightService;
import com.blog.service.impl.MemoryQueryService;
import com.blog.security.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * 念念 AI 记忆日记接口
 * 所有接口强制绑定当前登录用户，数据严格隔离
 */
@Slf4j
@RestController
@RequestMapping("/api/memory")
@RequiredArgsConstructor
public class MemoryController {

    private final MemoryCreateService memoryCreateService;
    private final MemoryCoreService memoryCoreService;
    private final MemoryQueryService memoryQueryService;
    private final CaptchaService captchaService;
    private final MemoryPublishService memoryPublishService;
    private final MemoryEmotionService memoryEmotionService;
    private final MemoryInsightService memoryInsightService;
    private final MemoryNoteMapper memoryNoteMapper;

    // ==================== 记忆创建 ====================

    /**
     * 上传照片创建记忆
     * 接收图片 → 转码存储 → AI 识别生成标题 → 初始化记忆记录
     */
    @PostMapping("/create")
    public Result<?> create(@RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getUserId();
        MemoryNote note = memoryCreateService.create(userId, file);
        return Result.success(note);
    }

    /**
     * 获取 AI 初始开场白（照片上传后 AI 主动开口）
     */
    @GetMapping("/ai/opening")
    @com.blog.aspect.AiRateLimit
    public Result<?> opening(@RequestParam("memoryId") Long memoryId) {
        Long userId = SecurityUtils.getUserId();
        String opening = memoryCoreService.getOpening(userId, memoryId);
        return Result.success(Map.of("opening", opening));
    }

    // ==================== AI 对话 ====================

    /**
     * 发送对话消息（文字）
     */
    @PostMapping("/chat/send")
    @com.blog.aspect.AiRateLimit
    public Result<?> chatSend(@Valid @RequestBody ChatSendDto dto) {
        Long userId = SecurityUtils.getUserId();
        // 10 分钟内已验证过则免验证码
        captchaService.verify(userId, dto.getCaptchaKey(), dto.getCaptchaCode());
        ChatReplyVo reply = memoryCoreService.chat(userId, dto.getMemoryId(), dto.getContent());
        return Result.success(reply);
    }

    /**
     * 追加对话图片
     */
    @PostMapping("/chat/img")
    public Result<?> chatImg(@RequestParam("memoryId") Long memoryId,
                             @RequestParam("file") MultipartFile file) {
        Long userId = SecurityUtils.getUserId();
        String imageUrl = memoryCreateService.saveImage(file).url;
        String description = "";
        try {
            description = memoryCoreService.describeImageForMessage(userId, memoryId, imageUrl);
        } catch (Exception e) {
            log.warn("图片描述失败: {}", e.getMessage());
        }
        MemoryMessage msg = memoryCoreService.addImageWithReturn(userId, memoryId, imageUrl, description);
        return Result.success(msg);
    }

    // ==================== 记忆凝聚 ====================

    /**
     * 凝聚记忆生成日记
     */
    @PostMapping("/condense")
    @com.blog.aspect.AiRateLimit
    public Result<?> condense(@RequestBody CondenseDto dto) {
        Long userId = SecurityUtils.getUserId();
        MemoryNote note = memoryCoreService.condense(userId, dto.getMemoryId(), dto.getCustomTitle());
        return Result.success(note);
    }

    // ==================== 记忆空间 ====================

    /**
     * 分页列表（管理员可传 userId 查指定用户；不传查全部）
     */
    @GetMapping("/list")
    public Result<?> list(MemoryQueryDto query,
                          @RequestParam(value = "userId", required = false) Long userId) {
        Long current = SecurityUtils.getUserId();
        return Result.success(memoryQueryService.pageList(current, userId, query));
    }

    /**
     * 全文搜索（管理员可传 userId 查指定用户）
     */
    @GetMapping("/search")
    public Result<?> search(MemorySearchDto dto,
                            @RequestParam(value = "userId", required = false) Long userId) {
        Long current = SecurityUtils.getUserId();
        return Result.success(memoryQueryService.search(current, userId, dto));
    }

    /**
     * 详情视图（管理员可查任意记忆）
     */
    @GetMapping("/detail")
    public Result<?> detail(@RequestParam("memoryId") Long memoryId,
                            @RequestParam(value = "userId", required = false) Long userId) {
        Long current = SecurityUtils.getUserId();
        return Result.success(memoryQueryService.detail(current, userId, memoryId));
    }

    /**
     * 用户统计（管理员可传 userId 查指定用户；不传查全部聚合）
     */
    @GetMapping("/stat")
    public Result<?> stat(@RequestParam(value = "userId", required = false) Long userId) {
        Long current = SecurityUtils.getUserId();
        return Result.success(memoryQueryService.stat(current, userId));
    }

    /**
     * 废弃记忆（管理员可废弃任意用户的记忆）
     */
    @PostMapping("/abandon")
    public Result<?> abandon(@RequestBody Map<String, Long> body,
                             @RequestParam(value = "userId", required = false) Long userId) {
        Long current = SecurityUtils.getUserId();
        memoryQueryService.abandon(current, userId, body.get("memoryId"));
        return Result.success("已废弃");
    }

    // ==================== 拾光碎念：三级隐私 + 情绪锚点 ====================

    /**
     * 设置记忆隐私等级（0-公开 1-私密 2-封存）
     * 封存记忆：AI 不再召回，但列表/详情仍可见。
     */
    @PostMapping("/privacy")
    public Result<?> setPrivacy(@RequestBody Map<String, Object> body,
                                @RequestParam(value = "userId", required = false) Long userId) {
        Long current = SecurityUtils.getUserId();
        Object id = body.get("memoryId");
        Object p = body.get("privacy");
        if (id == null || p == null) {
            return Result.error(400, "memoryId 与 privacy 不能为空");
        }
        Long memoryId = ((Number) id).longValue();
        Integer privacy = ((Number) p).intValue();
        return Result.success(memoryQueryService.setPrivacy(current, userId, memoryId, privacy));
    }

    /**
     * 编辑记忆（标题 / 情绪标签多选 / 正文）
     */
    @PostMapping("/update")
    public Result<?> updateMemory(@RequestBody Map<String, Object> body,
                                  @RequestParam(value = "userId", required = false) Long userId) {
        Long current = SecurityUtils.getUserId();
        Object id = body.get("memoryId");
        if (id == null) {
            return Result.error(400, "memoryId 不能为空");
        }
        Long memoryId = ((Number) id).longValue();
        String autoTitle = body.get("autoTitle") == null ? null : String.valueOf(body.get("autoTitle"));
        String finalDiary = body.get("finalDiary") == null ? null : String.valueOf(body.get("finalDiary"));
        // emotions 可能是数组或逗号字符串
        List<String> emotions = null;
        Object emoObj = body.get("emotions");
        if (emoObj instanceof List<?> list) {
            emotions = list.stream().map(String::valueOf).collect(java.util.stream.Collectors.toList());
        } else if (emoObj != null) {
            String s = String.valueOf(emoObj);
            emotions = new java.util.ArrayList<>();
            for (String e : s.split(",")) {
                String t = e.trim();
                if (!t.isEmpty()) emotions.add(t);
            }
        }
        return Result.success(memoryQueryService.updateMemory(current, userId, memoryId, autoTitle, emotions, finalDiary));
    }

    /**
     * 情绪锚点统计（管理员可传 userId 查指定用户；不传查全部）
     */
    @GetMapping("/emotion/stats")
    public Result<?> emotionStats(@RequestParam(value = "userId", required = false) Long userId) {
        Long current = SecurityUtils.getUserId();
        return Result.success(memoryQueryService.listEmotionStats(current, userId));
    }

    /**
     * 触发情绪归并（管理员可传 userId 为指定用户归并）
     */
    @PostMapping("/emotion/merge")
    public Result<?> mergeEmotions(@RequestParam(value = "userId", required = false) Long userId) {
        Long current = SecurityUtils.getUserId();
        Long scope = userId;
        if (scope == null && !SecurityUtils.isAdmin()) {
            scope = current;
        }
        memoryEmotionService.mergeEmotions(scope);
        return Result.success("情绪归并完成");
    }

    /**
     * 查询某条记忆的闪光点（前端「小念记得」列表/详情展示）
     */
    @GetMapping("/insights")
    public Result<?> insights(@RequestParam("memoryId") Long memoryId) {
        Long current = SecurityUtils.getUserId();
        MemoryNote note = memoryNoteMapper.selectById(memoryId);
        if (note == null) {
            return Result.error(404, "记忆不存在");
        }
        boolean admin = SecurityUtils.isAdmin();
        if (!admin && !note.getUserId().equals(current)) {
            return Result.error(403, "无权访问该记忆");
        }
        return Result.success(memoryInsightService.listByMemoryId(note.getUserId(), memoryId));
    }

    /**
     * 用户手动添加一条记忆闪光点（自动向量化，后续聊天可提起）
     */
    @PostMapping("/insight/add")
    public Result<?> addInsight(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getUserId();
        Long memoryId = body.get("memoryId") == null ? null : Long.valueOf(body.get("memoryId").toString());
        String content = body.get("content") == null ? null : body.get("content").toString();
        if (memoryId == null) {
            return Result.error(400, "memoryId 不能为空");
        }
        MemoryNote note = memoryNoteMapper.selectById(memoryId);
        if (note == null) {
            return Result.error(404, "记忆不存在");
        }
        boolean admin = SecurityUtils.isAdmin();
        if (!admin && !note.getUserId().equals(userId)) {
            return Result.error(403, "无权操作该记忆");
        }
        return Result.success(memoryInsightService.addInsight(note.getUserId(), memoryId, content));
    }

    /**
     * 删除一条记忆闪光点（仅本人/管理员）
     */
    @PostMapping("/insight/delete")
    public Result<?> deleteInsight(@RequestBody Map<String, Object> body) {
        Long userId = SecurityUtils.getUserId();
        Long insightId = body.get("insightId") == null ? null : Long.valueOf(body.get("insightId").toString());
        if (insightId == null) {
            return Result.error(400, "insightId 不能为空");
        }
        memoryInsightService.deleteInsight(userId, insightId, SecurityUtils.isAdmin());
        return Result.success("已删除");
    }

    // ==================== 日记转博客文章 ====================

    /**
     * 日记一键转为博客公开文章
     * 幂等：已发布则返回已有文章ID，不重复创建
     */
    @PostMapping("/publish")
    public Result<?> publish(@RequestBody Map<String, Long> body) {
        Long userId = SecurityUtils.getUserId();
        Long memoryId = body.get("memoryId");
        if (memoryId == null) {
            return Result.error(400, "memoryId 不能为空");
        }
        Long articleId = memoryPublishService.publish(memoryId, userId);
        return Result.success(articleId);
    }

    /**
     * 查询日记发布状态（已发布返回文章ID，未发布返回null）
     */
    @GetMapping("/publish-status")
    public Result<?> publishStatus(@RequestParam("memoryId") Long memoryId) {
        Long userId = SecurityUtils.getUserId();
        return Result.success(memoryPublishService.getPublishedArticleId(memoryId, userId));
    }

    // ==================== 通用防护 ====================

    /**
     * 图形验证码（防刷 AI 接口）
     */
    @GetMapping("/captcha")
    public Result<?> captcha() {
        return Result.success(captchaService.generate());
    }
}
