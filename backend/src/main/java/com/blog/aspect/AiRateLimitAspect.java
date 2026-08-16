package com.blog.aspect;

import com.blog.common.BusinessException;
import com.blog.entity.MemoryNote;
import com.blog.mapper.MemoryNoteMapper;
import com.blog.service.impl.MemoryCoreService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 接口限流 AOP
 * <p>
 * 基于内存滑动窗口：单用户每分钟 AI 调用次数限制（默认 10 次/分钟），
 * 控制 token 成本与服务器压力。单机部署够用；集群部署可换 Redis。
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AiRateLimitAspect {

    /** 时间窗口（毫秒） */
    private static final long WINDOW_MS = 60_000L;

    private final MemoryNoteMapper memoryNoteMapper;
    private final MemoryCoreService memoryCoreService;

    private final ConcurrentHashMap<String, WindowCounter> counters = new ConcurrentHashMap<>();

    @Value("${memory.rate-limit-per-minute:10}")
    private int limitPerMinute;

    @Before("@annotation(com.blog.aspect.AiRateLimit)")
    public void rateLimit(JoinPoint joinPoint) {
        Long userId = resolveUserId();
        if (userId == null) {
            throw new BusinessException(401, "未登录");
        }

        // 每分钟桶
        long bucket = System.currentTimeMillis() / WINDOW_MS;
        String key = "ai:" + userId + ":" + bucket;
        WindowCounter counter = counters.computeIfAbsent(key, k -> new WindowCounter());
        int count = counter.increment();
        if (count > limitPerMinute) {
            throw new BusinessException(429, "操作太频繁，请稍后再试");
        }

        // 清理过期桶（简单防内存膨胀）
        if (counters.size() > 10_000) {
            counters.entrySet().removeIf(e ->
                    !e.getKey().startsWith("ai:" + userId) &&
                            e.getKey().endsWith(":" + (bucket - 2)));
        }
    }

    private Long resolveUserId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest request = attrs.getRequest();
        Long userId = (Long) request.getAttribute("memoryUserId");
        if (userId != null) return userId;
        // 兜底：从 Security 上下文获取
        return com.blog.security.utils.SecurityUtils.getUserId();
    }

    private static class WindowCounter {
        private final AtomicInteger count = new AtomicInteger(0);

        int increment() {
            return count.incrementAndGet();
        }
    }
}
