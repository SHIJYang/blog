package com.blog.service;

import com.blog.common.BusinessException;
import com.blog.util.CaptchaUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 图形验证码服务（内存存储，单机部署够用）
 * <p>
 * 校验通过后立即删除，防重放；5 分钟过期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CaptchaService {

    /** 验证码过期秒数 */
    private static final long EXPIRE_SECONDS = 300;
    /** 验证通过后免验证时长（秒）：60 分钟 */
    private static final long VERIFY_WINDOW_SECONDS = 3600;

    private final Map<String, CaptchaEntry> store = new ConcurrentHashMap<>();
    /** 用户最近验证通过时间：userId -> 时间戳 */
    private final Map<Long, Long> verifiedUsers = new ConcurrentHashMap<>();

    public static class CaptchaEntry {
        public final String code;
        public final LocalDateTime expireAt;

        public CaptchaEntry(String code, LocalDateTime expireAt) {
            this.code = code;
            this.expireAt = expireAt;
        }
    }

    /**
     * 生成验证码
     *
     * @return { key, image }
     */
    public Map<String, String> generate() {
        String key = UUID.randomUUID().toString().replace("-", "");
        String code = CaptchaUtil.generateCode();
        store.put(key, new CaptchaEntry(code, LocalDateTime.now().plusSeconds(EXPIRE_SECONDS)));
        return Map.of(
                "key", key,
                "image", CaptchaUtil.generateImageBase64(code)
        );
    }

    /**
     * 校验验证码（校验后删除，一次性）
     */
    public void verify(String key, String code) {
        if (key == null || code == null) {
            throw new BusinessException(400, "验证码不能为空");
        }
        CaptchaEntry entry = store.get(key);
        if (entry == null) {
            throw new BusinessException(400, "验证码已失效，请重新获取");
        }
        store.remove(key); // 一次性
        if (entry.expireAt.isBefore(LocalDateTime.now())) {
            throw new BusinessException(400, "验证码已过期，请重新获取");
        }
        if (!entry.code.equalsIgnoreCase(code.trim())) {
            throw new BusinessException(400, "验证码错误");
        }
    }

    /**
     * 校验验证码，带用户免验证窗口（10 分钟内只需验证一次）
     * <p>
     * - 该用户 10 分钟内已通过验证 → 直接放行（免验证）
     * - 否则校验验证码，通过后记录该用户最近验证时间
     */
    public void verify(Long userId, String key, String code) {
        if (userId != null) {
            Long last = verifiedUsers.get(userId);
            if (last != null && (System.currentTimeMillis() - last) < VERIFY_WINDOW_SECONDS * 1000L) {
                return; // 10 分钟内已通过，免验证码
            }
        }
        // 需要验证
        verify(key, code);
        // 通过后记录（键为 null 时仍记录，避免下次再被要求）
        if (userId != null) {
            verifiedUsers.put(userId, System.currentTimeMillis());
            cleanup();
        }
    }

    /** 清理过期的免验证记录，防止内存膨胀 */
    private void cleanup() {
        long now = System.currentTimeMillis();
        verifiedUsers.entrySet().removeIf(e -> (now - e.getValue()) >= VERIFY_WINDOW_SECONDS * 1000L);
    }
}
