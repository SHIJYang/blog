package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.entity.MemoryAccessLog;
import com.blog.entity.MemoryNote;
import com.blog.mapper.MemoryAccessLogMapper;
import com.blog.security.utils.SecurityUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;

/**
 * 记忆可见性统一收口（M5 的服务层版本）。
 * <p>
 * 规则只有三条，任何 memory 查询都必须经过这里，业务代码不再手写 privacy 条件：
 * <ul>
 *   <li>封存(2)：本人也需 PIN 解锁，列表/搜索默认排除，详情/聊天需令牌</li>
 *   <li>私密(1)：仅本人（管理员可运维查看）</li>
 *   <li>公开(0)：注册用户均可读，且会投影成博客文章</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryVisibilityService {

    public static final int PRIVACY_PUBLIC = 0;
    public static final int PRIVACY_PRIVATE = 1;
    public static final int PRIVACY_SEALED = 2;

    private final MemoryPinService pinService;
    private final MemoryAccessLogMapper accessLogMapper;

    /**
     * 查询范围：普通用户强制查自己；管理员传 targetUserId 查指定用户，不传查全部。
     */
    public Long resolveScope(Long currentUserId, Long targetUserId) {
        if (!SecurityUtils.isAdmin()) {
            return currentUserId;
        }
        return targetUserId;
    }

    /**
     * 给列表/搜索类查询统一追加可见性条件。
     * 封存条目仅当「查询范围就是自己」且已解锁时返回。
     */
    public void applyVisibility(LambdaQueryWrapper<MemoryNote> wrapper,
                                Long currentUserId, Long targetUserId) {
        Long scope = resolveScope(currentUserId, targetUserId);
        if (scope != null) {
            wrapper.eq(MemoryNote::getUserId, scope);
        }
        boolean unlocked = scope != null && scope.equals(currentUserId) && pinService.isUnlocked(currentUserId);
        if (!unlocked) {
            wrapper.ne(MemoryNote::getPrivacy, PRIVACY_SEALED);
        }
    }

    /** 单条记忆是否可读 */
    public boolean canRead(MemoryNote note, Long currentUserId) {
        if (note == null) {
            return false;
        }
        int privacy = note.getPrivacy() == null ? PRIVACY_PRIVATE : note.getPrivacy();
        boolean owner = currentUserId != null && currentUserId.equals(note.getUserId());
        if (privacy == PRIVACY_SEALED) {
            return owner && pinService.isUnlocked(note.getUserId());
        }
        if (owner || SecurityUtils.isAdmin()) {
            return true;
        }
        return privacy == PRIVACY_PUBLIC;
    }

    /** 校验可读，封存条目会记一条访问审计 */
    public void requireReadable(MemoryNote note, Long currentUserId) {
        if (!canRead(note, currentUserId)) {
            if (note != null && note.getPrivacy() != null && note.getPrivacy() == PRIVACY_SEALED
                    && currentUserId != null && currentUserId.equals(note.getUserId())) {
                throw new BusinessException(403, "该记忆已封存，请先输入 PIN 解锁");
            }
            throw new BusinessException(403, "无权访问该记忆");
        }
        if (note.getPrivacy() != null && note.getPrivacy() == PRIVACY_SEALED) {
            audit(currentUserId, note.getId(), note.getPrivacy(), MemoryAccessLog.ACTION_VIEW_SEALED);
        }
    }

    /** 是否需要 PIN 才能操作（供聊天/凝聚等内容型操作复用） */
    public void requireSealedAccess(MemoryNote note) {
        if (note != null && note.getPrivacy() != null && note.getPrivacy() == PRIVACY_SEALED
                && !pinService.isUnlocked(note.getUserId())) {
            throw new BusinessException(403, "该记忆已封存，请先输入 PIN 解锁");
        }
    }

    public boolean isSealedUnlocked(Long ownerUserId) {
        return pinService.isUnlocked(ownerUserId);
    }

    public void audit(Long userId, Long memoryId, Integer privacy, String action) {
        try {
            MemoryAccessLog row = new MemoryAccessLog();
            row.setUserId(userId);
            row.setMemoryId(memoryId);
            row.setPrivacy(privacy);
            row.setAction(action);
            HttpServletRequest request = currentRequest();
            if (request != null) {
                row.setIpAddress(StringUtils.abbreviate(request.getRemoteAddr(), 50));
                row.setUserAgent(StringUtils.abbreviate(request.getHeader("User-Agent"), 255));
            }
            row.setCreateTime(LocalDateTime.now());
            accessLogMapper.insert(row);
        } catch (Exception e) {
            log.warn("写访问审计失败（不阻断）: action={}, err={}", action, e.getMessage());
        }
    }

    private HttpServletRequest currentRequest() {
        var attrs = RequestContextHolder.getRequestAttributes();
        return attrs instanceof ServletRequestAttributes sra ? sra.getRequest() : null;
    }
}
