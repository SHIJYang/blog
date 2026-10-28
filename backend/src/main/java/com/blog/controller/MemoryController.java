package com.blog.controller;

import com.blog.common.Result;
import com.blog.dto.ChatSendDto;
import com.blog.dto.CondenseDto;
import com.blog.dto.MemoryFragmentDto;
import com.blog.dto.MemoryQueryDto;
import com.blog.dto.MemorySearchDto;
import com.blog.vo.ChatReplyVo;
import com.blog.entity.MemoryMessage;
import com.blog.entity.MemoryNote;
import com.blog.entity.MemoryPhoto;
import com.blog.entity.SysUser;
import com.blog.mapper.MemoryNoteMapper;
import com.blog.mapper.MemoryPhotoMapper;
import com.blog.mapper.SysUserMapper;
import com.blog.security.utils.JwtUtils;
import com.blog.service.CaptchaService;
import com.blog.service.MemoryProjectionService;
import com.blog.service.impl.MemoryCreateService;
import com.blog.service.impl.MemoryCoreService;
import com.blog.service.impl.MemoryEmotionService;
import com.blog.service.impl.MemoryInsightService;
import com.blog.service.impl.MemoryPhotoService;
import com.blog.service.impl.MemoryPinService;
import com.blog.service.impl.MemoryQueryService;
import com.blog.service.impl.MemoryVisibilityService;
import com.blog.security.utils.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
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
    private final MemoryProjectionService projectionService;
    private final MemoryEmotionService memoryEmotionService;
    private final MemoryInsightService memoryInsightService;
    private final MemoryNoteMapper memoryNoteMapper;
    private final MemoryPhotoMapper memoryPhotoMapper;
    private final MemoryPhotoService memoryPhotoService;
    private final MemoryPinService memoryPinService;
    private final MemoryVisibilityService visibilityService;
    private final SysUserMapper sysUserMapper;
    private final JwtUtils jwtUtils;

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
        // 先校验归属，避免为不存在的记忆落一张孤儿照片
        memoryCoreService.getOwnedNote(userId, memoryId);
        // 受控存储：图片不落公网，AI 识别走 data URL
        MemoryPhoto photo = memoryPhotoService.save(file, userId, memoryId, 0);
        String description = "";
        try {
            description = memoryCoreService.describeImageForMessage(userId, memoryId,
                    memoryPhotoService.dataUrl(photo));
        } catch (Exception e) {
            log.warn("图片描述失败: {}", e.getMessage());
        }
        MemoryMessage msg = memoryCoreService.addImageWithReturn(
                userId, memoryId, memoryPhotoService.accessUrl(photo), description);
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
        // 他人只能读公开记忆；封存需 PIN 解锁（统一收口）
        visibilityService.requireReadable(note, current);
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
        Long articleId = projectionService.publish(memoryId, userId);
        return Result.success(articleId);
    }

    /**
     * 查询日记发布状态（已发布返回文章ID，未发布返回null）
     */
    @GetMapping("/publish-status")
    public Result<?> publishStatus(@RequestParam("memoryId") Long memoryId) {
        Long userId = SecurityUtils.getUserId();
        return Result.success(projectionService.getPublishedArticleId(memoryId, userId));
    }

    // ==================== 纯文本碎念 / 时光轴 ====================

    /**
     * 写一条纯文本碎念（不再强制带图）
     */
    @PostMapping("/fragment")
    public Result<?> fragment(@Valid @RequestBody MemoryFragmentDto dto) {
        Long userId = SecurityUtils.getUserId();
        return Result.success(memoryCreateService.createFragment(userId, dto));
    }

    /**
     * 时光轴：按 finish_time 年月聚合
     */
    @GetMapping("/timeline")
    public Result<?> timeline(@RequestParam(value = "year", required = false) Integer year,
                              @RequestParam(value = "month", required = false) Integer month) {
        Long userId = SecurityUtils.getUserId();
        return Result.success(memoryQueryService.timeline(userId, year, month));
    }

    /**
     * 那年今日：按月-日匹配所有年份
     */
    @GetMapping("/on-this-day")
    public Result<?> onThisDay(@RequestParam(value = "date", required = false) String date) {
        Long userId = SecurityUtils.getUserId();
        LocalDate day = null;
        if (StringUtils.isNotBlank(date)) {
            try {
                day = LocalDate.parse(date);
            } catch (Exception e) {
                return Result.error(400, "date 格式应为 yyyy-MM-dd");
            }
        }
        return Result.success(memoryQueryService.onThisDay(userId, day));
    }

    // ==================== 封存 PIN ====================

    /** 设置 / 重置封存 PIN（4~6 位数字） */
    @PostMapping("/pin/set")
    public Result<?> setPin(@RequestBody Map<String, String> body) {
        Long userId = SecurityUtils.getUserId();
        memoryPinService.setPin(userId, body.get("pin"));
        return Result.success("PIN 已更新");
    }

    /** 校验 PIN，成功返回 5 分钟解锁令牌 */
    @PostMapping("/pin/verify")
    public Result<?> verifyPin(@RequestBody Map<String, String> body) {
        Long userId = SecurityUtils.getUserId();
        return Result.success(memoryPinService.verify(userId, body.get("pin")));
    }

    /** PIN 状态：是否已设置 / 是否锁定 / 当前请求是否已解锁 */
    @GetMapping("/pin/status")
    public Result<?> pinStatus() {
        Long userId = SecurityUtils.getUserId();
        return Result.success(memoryPinService.status(userId));
    }

    // ==================== 照片（受控读取） ====================

    /**
     * 记忆照片读取。
     * <p>
     * 该路径在白名单里（&lt;img&gt; 带不了 Authorization 头），由本方法自行鉴权：
     * public(0) 允许匿名；private(1) 仅本人/管理员；sealed(2) 还需 PIN 解锁令牌。
     * JWT 可走 header，也可走 {@code ?token=}。
     */
    @GetMapping("/photo/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable Long id,
                                        @RequestParam(value = "token", required = false) String token) {
        MemoryPhoto photo = memoryPhotoMapper.selectById(id);
        if (photo == null) {
            return ResponseEntity.notFound().build();
        }
        MemoryNote note = photo.getMemoryId() == null ? null : memoryNoteMapper.selectById(photo.getMemoryId());
        Long currentUserId = resolveUserId(token);

        int privacy = note == null || note.getPrivacy() == null
                ? MemoryVisibilityService.PRIVACY_PRIVATE : note.getPrivacy();
        if (note == null) {
            // 找不到所属记忆时，只允许照片上传者本人
            if (currentUserId == null || !currentUserId.equals(photo.getUserId())) {
                return ResponseEntity.status(403).build();
            }
        } else if (privacy != MemoryVisibilityService.PRIVACY_PUBLIC) {
            boolean owner = currentUserId != null && currentUserId.equals(note.getUserId());
            boolean admin = SecurityUtils.isAdmin();
            if (!owner && !admin) {
                return ResponseEntity.status(403).build();
            }
            if (privacy == MemoryVisibilityService.PRIVACY_SEALED
                    && !memoryPinService.isUnlocked(note.getUserId())) {
                return ResponseEntity.status(403).build();
            }
        }

        byte[] bytes = memoryPhotoService.readBytes(photo);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(memoryPhotoService.mimeType(photo)))
                .body(bytes);
    }

    /** 图片标签场景下用查询参数里的 JWT 反查用户 */
    private Long resolveUserId(String token) {
        Long fromContext = SecurityUtils.getUserId();
        if (fromContext != null) {
            return fromContext;
        }
        if (StringUtils.isBlank(token)) {
            return null;
        }
        try {
            if (jwtUtils.isTokenExpired(token)) {
                return null;
            }
            String username = jwtUtils.getUsernameFromToken(token);
            SysUser user = sysUserMapper.selectOne(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                            .eq(SysUser::getUsername, username));
            return user == null ? null : user.getId();
        } catch (Exception e) {
            return null;
        }
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
