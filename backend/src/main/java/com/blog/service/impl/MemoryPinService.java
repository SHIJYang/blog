package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.entity.MemoryAccessLog;
import com.blog.entity.SysUser;
import com.blog.mapper.MemoryAccessLogMapper;
import com.blog.mapper.SysUserMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 封存解锁服务（D2 / D11）。
 * <p>
 * 全局单一 PIN，解锁成功签发**用户级短时令牌**（5 分钟无操作失效，访问即续期）。
 * 令牌只放内存：进程重启即全体失效，安全侧是可接受的。改 PIN 时以 pin_updated_at
 * 作吊销水位——早于该时间签发的令牌一律作废。
 * 连续失败 5 次锁定 30 分钟（A1 的默认取值）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryPinService {

    /** 解锁令牌请求头 / 查询参数（<img> 无法加 header 时用查询参数） */
    public static final String TOKEN_HEADER = "X-Sealed-Token";
    public static final String TOKEN_PARAM = "sealedToken";

    private static final int MAX_FAILED = 5;
    private static final Duration LOCK_DURATION = Duration.ofMinutes(30);
    private static final Duration TOKEN_TTL = Duration.ofMinutes(5);

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final MemoryAccessLogMapper accessLogMapper;

    private final Map<String, TokenEntry> tokens = new ConcurrentHashMap<>();

    private record TokenEntry(Long userId, LocalDateTime issuedAt, LocalDateTime expiresAt) {
    }

    /** 设置 / 重置 PIN（4~6 位数字），并吊销该用户所有已签发令牌 */
    @Transactional(rollbackFor = Exception.class)
    public void setPin(Long userId, String pin) {
        if (pin == null || !pin.matches("\\d{4,6}")) {
            throw new BusinessException(400, "PIN 需为 4~6 位数字");
        }
        SysUser user = requireUser(userId);
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPrivacyPinHash(passwordEncoder.encode(pin));
        update.setPinFailedCount(0);
        update.setPinLockedUntil(null);
        update.setPinUpdatedAt(LocalDateTime.now());
        sysUserMapper.updateById(update);
        revokeAll(userId);
    }

    /**
     * 校验 PIN，成功返回解锁令牌。
     * <p>
     * 刻意**不加 {@code @Transactional}**：失败分支要"先写失败计数再抛业务异常"，
     * 若包在事务里，抛异常会把计数一起回滚，连续失败永远累不上去（实测踩过）。
     */
    public Map<String, Object> verify(Long userId, String pin) {
        SysUser user = requireUser(userId);
        if (StringUtils.isBlank(user.getPrivacyPinHash())) {
            throw new BusinessException(400, "尚未设置封存 PIN，请先设置");
        }
        LocalDateTime now = LocalDateTime.now();
        if (user.getPinLockedUntil() != null && user.getPinLockedUntil().isAfter(now)) {
            long minutes = Math.max(1, Duration.between(now, user.getPinLockedUntil()).toMinutes() + 1);
            throw new BusinessException(423, "PIN 连续错误已锁定，请 " + minutes + " 分钟后再试");
        }
        if (pin == null || !passwordEncoder.matches(pin, user.getPrivacyPinHash())) {
            int failed = (user.getPinFailedCount() == null ? 0 : user.getPinFailedCount()) + 1;
            SysUser update = new SysUser();
            update.setId(user.getId());
            update.setPinFailedCount(failed);
            boolean locked = failed >= MAX_FAILED;
            if (locked) {
                update.setPinLockedUntil(now.plus(LOCK_DURATION));
                update.setPinFailedCount(0);
            }
            sysUserMapper.updateById(update);
            audit(userId, null, 2, MemoryAccessLog.ACTION_UNLOCK_FAIL);
            throw new BusinessException(403, locked ? "PIN 连续错误 5 次，已锁定 30 分钟" : "PIN 错误");
        }

        // 成功：清零失败计数，签发令牌
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setPinFailedCount(0);
        update.setPinLockedUntil(null);
        sysUserMapper.updateById(update);

        String token = UUID.randomUUID().toString().replace("-", "");
        tokens.put(token, new TokenEntry(userId, now, now.plus(TOKEN_TTL)));
        audit(userId, null, 2, MemoryAccessLog.ACTION_UNLOCK_SUCCESS);
        return Map.of("token", token, "expiresIn", TOKEN_TTL.toSeconds());
    }

    /** PIN 状态（前端决定显示「设置 PIN」还是「输入 PIN」） */
    public Map<String, Object> status(Long userId) {
        SysUser user = requireUser(userId);
        LocalDateTime now = LocalDateTime.now();
        boolean locked = user.getPinLockedUntil() != null && user.getPinLockedUntil().isAfter(now);
        return Map.of(
                "hasPin", StringUtils.isNotBlank(user.getPrivacyPinHash()),
                "locked", locked,
                "lockedUntil", locked ? user.getPinLockedUntil() : "",
                "unlocked", isUnlocked(userId)
        );
    }

    /** 当前请求是否持有有效的封存解锁令牌 */
    public boolean isUnlocked(Long userId) {
        String token = currentToken();
        return token != null && verifyToken(userId, token);
    }

    /** 校验令牌并滑动续期（5 分钟无操作失效） */
    public boolean verifyToken(Long userId, String token) {
        if (StringUtils.isBlank(token) || userId == null) {
            return false;
        }
        TokenEntry entry = tokens.get(token);
        if (entry == null) {
            return false;
        }
        LocalDateTime now = LocalDateTime.now();
        if (entry.expiresAt().isBefore(now) || !entry.userId().equals(userId)) {
            tokens.remove(token);
            return false;
        }
        // 吊销水位：改 PIN 之后，旧令牌全部失效
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || (user.getPinUpdatedAt() != null && user.getPinUpdatedAt().isAfter(entry.issuedAt()))) {
            tokens.remove(token);
            return false;
        }
        tokens.put(token, new TokenEntry(userId, entry.issuedAt(), now.plus(TOKEN_TTL)));
        return true;
    }

    public void revokeAll(Long userId) {
        tokens.entrySet().removeIf(e -> e.getValue().userId().equals(userId));
    }

    /** 从当前请求取令牌（header 优先，其次查询参数） */
    public String currentToken() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String token = request.getHeader(TOKEN_HEADER);
        if (StringUtils.isBlank(token)) {
            token = request.getParameter(TOKEN_PARAM);
        }
        return StringUtils.trimToNull(token);
    }

    private SysUser requireUser(Long userId) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }
        return user;
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }

    private void audit(Long userId, Long memoryId, Integer privacy, String action) {
        try {
            MemoryAccessLog logRow = new MemoryAccessLog();
            logRow.setUserId(userId);
            logRow.setMemoryId(memoryId);
            logRow.setPrivacy(privacy);
            logRow.setAction(action);
            HttpServletRequest request = currentRequest();
            if (request != null) {
                logRow.setIpAddress(StringUtils.abbreviate(request.getRemoteAddr(), 50));
                logRow.setUserAgent(StringUtils.abbreviate(request.getHeader("User-Agent"), 255));
            }
            logRow.setCreateTime(LocalDateTime.now());
            accessLogMapper.insert(logRow);
        } catch (Exception e) {
            log.warn("写访问审计失败（不阻断）: action={}, err={}", action, e.getMessage());
        }
    }
}
