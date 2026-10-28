package com.blog.service.impl;

import com.blog.common.BusinessException;
import com.blog.entity.IdeaTransition;
import com.blog.mapper.IdeaTransitionMapper;
import com.blog.mapper.IdeaTransitionRuleMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 念生六态状态机（D8）：迁移是否允许由 {@code idea_transition_rule} 表决定，不写死在代码里。
 * <p>
 * 非法迁移统一返回 409，并拒绝落库；合法迁移同时写 {@code idea_transition} 流水。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdeaStateMachineService {

    private final IdeaTransitionRuleMapper ruleMapper;
    private final IdeaTransitionMapper transitionMapper;

    public List<String> allowedTargets(String fromState) {
        if (fromState == null || fromState.isBlank()) {
            return List.of();
        }
        return ruleMapper.selectTargets(fromState);
    }

    public void assertAllowed(String fromState, String toState) {
        if (toState == null || toState.isBlank()) {
            throw new BusinessException(400, "目标状态不能为空");
        }
        if (toState.equals(fromState)) {
            throw new BusinessException(400, "状态未变化");
        }
        if (ruleMapper.countRule(fromState, toState) == 0) {
            throw new BusinessException(409, "不允许从 " + fromState + " 迁移到 " + toState);
        }
    }

    public void record(Long ideaId, Long userId, String fromState, String toState,
                       String trigger, String reason) {
        IdeaTransition transition = new IdeaTransition();
        transition.setIdeaId(ideaId);
        transition.setUserId(userId);
        transition.setFromState(fromState);
        transition.setToState(toState);
        transition.setTrigger(trigger == null ? "manual" : trigger);
        transition.setReason(reason);
        transition.setCreateTime(LocalDateTime.now());
        transitionMapper.insert(transition);
    }
}
