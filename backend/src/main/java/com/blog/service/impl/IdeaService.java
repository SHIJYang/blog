package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.blog.common.BusinessException;
import com.blog.dto.IdeaSaveDto;
import com.blog.entity.Idea;
import com.blog.entity.IdeaAnalysis;
import com.blog.entity.IdeaState;
import com.blog.entity.IdeaTransition;
import com.blog.entity.MemoryInsight;
import com.blog.entity.SysUser;
import com.blog.mapper.IdeaAnalysisMapper;
import com.blog.mapper.IdeaMapper;
import com.blog.mapper.IdeaStateMapper;
import com.blog.mapper.IdeaTransitionMapper;
import com.blog.mapper.MemoryInsightMapper;
import com.blog.mapper.SysUserMapper;
import com.blog.security.utils.SecurityUtils;
import com.blog.service.ai.AgnesAiClient;
import com.blog.service.ai.AiProviderService;
import com.blog.service.ai.VectorUtil;
import com.blog.vo.IdeaVo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 念生：想法工作台。
 * <p>
 * 六态状态机由 {@link IdeaStateMachineService} 表驱动；AI 分析与质疑只追加、
 * 永不覆盖想法原文（{@code idea_analysis} 每次一行）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdeaService {

    private final IdeaMapper ideaMapper;
    private final IdeaStateMapper ideaStateMapper;
    private final IdeaTransitionMapper ideaTransitionMapper;
    private final IdeaAnalysisMapper ideaAnalysisMapper;
    private final MemoryInsightMapper memoryInsightMapper;
    private final SysUserMapper sysUserMapper;
    private final IdeaStateMachineService stateMachine;
    private final IdeaPromptService promptService;
    private final AgnesAiClient agnesAiClient;
    private final AiProviderService aiProviderService;
    /** Boot 4 下没有可注入的 ObjectMapper Bean，这里自带一个（与 PicuiClient 的做法一致） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    // ==================== 创建 / 编辑 ====================

    @Transactional(rollbackFor = Exception.class)
    public IdeaVo create(Long currentUserId, IdeaSaveDto dto) {
        String content = StringUtils.trimToEmpty(dto.getContent());
        if (content.isEmpty()) {
            throw new BusinessException(400, "想法内容不能为空");
        }
        Idea idea = new Idea();
        idea.setUserId(currentUserId);
        idea.setContent(content);
        idea.setState("seed");
        idea.setPriority(dto.getPriority() == null ? 0 : Math.max(0, Math.min(9, dto.getPriority())));
        idea.setTags(joinTags(dto.getTags()));
        idea.setSource(1);
        idea.setCreateTime(LocalDateTime.now());
        idea.setUpdateTime(LocalDateTime.now());
        ideaMapper.insert(idea);

        // 初始落库即写一条迁移流水（seed 是起点，from 为空）
        stateMachine.record(idea.getId(), currentUserId, null, "seed", "manual", "创建");
        embedAsyncSafe(idea.getId(), content);
        return detail(currentUserId, null, idea.getId());
    }

    /** 拾光闪光点 → 想法（复制非引用，留 source_insight_id 痕迹） */
    @Transactional(rollbackFor = Exception.class)
    public IdeaVo createFromInsight(Long currentUserId, Long insightId) {
        MemoryInsight insight = memoryInsightMapper.selectById(insightId);
        if (insight == null || (insight.getIsDeleted() != null && insight.getIsDeleted() == 1)) {
            throw new BusinessException(404, "闪光点不存在");
        }
        if (!SecurityUtils.isAdmin() && !insight.getUserId().equals(currentUserId)) {
            throw new BusinessException(403, "无权使用他人的闪光点");
        }
        Idea idea = new Idea();
        idea.setUserId(currentUserId);
        idea.setContent(insight.getContent());
        idea.setState("seed");
        idea.setPriority(0);
        idea.setTags(StringUtils.defaultIfBlank(insight.getEmotion(), ""));
        idea.setSource(2);
        idea.setSourceInsightId(insightId);
        idea.setCreateTime(LocalDateTime.now());
        idea.setUpdateTime(LocalDateTime.now());
        ideaMapper.insert(idea);
        stateMachine.record(idea.getId(), currentUserId, null, "seed", "manual", "由闪光点升级");
        embedAsyncSafe(idea.getId(), idea.getContent());
        return detail(currentUserId, null, idea.getId());
    }

    @Transactional(rollbackFor = Exception.class)
    public IdeaVo update(Long currentUserId, Long targetUserId, Long ideaId, IdeaSaveDto dto) {
        Idea idea = getOwned(currentUserId, targetUserId, ideaId);
        Idea update = new Idea();
        update.setId(ideaId);
        boolean contentChanged = false;
        if (dto.getContent() != null) {
            String content = dto.getContent().trim();
            if (content.isEmpty()) {
                throw new BusinessException(400, "想法内容不能为空");
            }
            contentChanged = !content.equals(idea.getContent());
            update.setContent(content);
        }
        if (dto.getTags() != null) {
            update.setTags(joinTags(dto.getTags()));
        }
        if (dto.getPriority() != null) {
            update.setPriority(Math.max(0, Math.min(9, dto.getPriority())));
        }
        ideaMapper.updateById(update);
        if (contentChanged) {
            embedAsyncSafe(ideaId, update.getContent());
        }
        return detail(currentUserId, targetUserId, ideaId);
    }

    public void delete(Long currentUserId, Long targetUserId, Long ideaId) {
        getOwned(currentUserId, targetUserId, ideaId);
        ideaMapper.deleteById(ideaId);
    }

    // ==================== 状态机 ====================

    @Transactional(rollbackFor = Exception.class)
    public IdeaVo move(Long currentUserId, Long targetUserId, Long ideaId,
                       String toState, String reason) {
        Idea idea = getOwned(currentUserId, targetUserId, ideaId);
        stateMachine.assertAllowed(idea.getState(), toState);

        Idea update = new Idea();
        update.setId(ideaId);
        update.setState(toState);
        ideaMapper.updateById(update);
        stateMachine.record(ideaId, idea.getUserId(), idea.getState(), toState, "manual", reason);
        return detail(currentUserId, targetUserId, ideaId);
    }

    public List<IdeaTransition> history(Long currentUserId, Long targetUserId, Long ideaId) {
        getOwned(currentUserId, targetUserId, ideaId);
        return ideaTransitionMapper.selectList(new LambdaQueryWrapper<IdeaTransition>()
                .eq(IdeaTransition::getIdeaId, ideaId)
                .orderByDesc(IdeaTransition::getCreateTime));
    }

    // ==================== 看板 / 详情 ====================

    @Transactional(transactionManager = "transactionManager", readOnly = true)
    public List<Map<String, Object>> board(Long currentUserId, Long targetUserId) {
        Long scope = resolveScope(currentUserId, targetUserId);
        LambdaQueryWrapper<Idea> wrapper = new LambdaQueryWrapper<Idea>()
                .orderByDesc(Idea::getPriority)
                .orderByDesc(Idea::getCreateTime);
        if (scope != null) {
            wrapper.eq(Idea::getUserId, scope);
        }
        List<Idea> ideas = ideaMapper.selectList(wrapper);

        List<IdeaState> states = states();
        Map<Long, String> usernames = loadUsernames(ideas);

        Map<String, List<IdeaVo>> grouped = new HashMap<>();
        for (Idea idea : ideas) {
            grouped.computeIfAbsent(idea.getState(), k -> new ArrayList<>())
                    .add(toVo(idea, stateMap(states), usernames.get(idea.getUserId())));
        }

        List<Map<String, Object>> columns = new ArrayList<>();
        for (IdeaState state : states) {
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("code", state.getCode());
            col.put("name", state.getName());
            col.put("sort", state.getSort());
            col.put("color", state.getColor());
            col.put("isTerminal", state.getIsTerminal());
            List<IdeaVo> list = grouped.getOrDefault(state.getCode(), new ArrayList<>());
            col.put("count", list.size());
            col.put("ideas", list);
            columns.add(col);
        }
        return columns;
    }

    public IdeaVo detail(Long currentUserId, Long targetUserId, Long ideaId) {
        Idea idea = getOwned(currentUserId, targetUserId, ideaId);
        String username = null;
        if (SecurityUtils.isAdmin() && !idea.getUserId().equals(currentUserId)) {
            SysUser user = sysUserMapper.selectById(idea.getUserId());
            username = user == null ? null : StringUtils.defaultIfBlank(user.getNickname(), user.getUsername());
        }
        return toVo(idea, stateMap(states()), username);
    }

    public List<IdeaState> states() {
        return ideaStateMapper.selectList(new LambdaQueryWrapper<IdeaState>()
                .orderByAsc(IdeaState::getSort));
    }

    // ==================== AI 分析 / 质疑 ====================

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> analyze(Long currentUserId, Long targetUserId, Long ideaId, String kind) {
        Idea idea = getOwned(currentUserId, targetUserId, ideaId);
        boolean challenge = IdeaAnalysis.KIND_CHALLENGE.equals(kind);
        String scene = challenge ? IdeaPromptService.SCENE_CHALLENGE : IdeaPromptService.SCENE_ANALYZE;
        String prompt = promptService.resolve(scene);

        String raw = null;
        Map<String, Object> parsed = null;
        int degraded = 0;
        try {
            raw = agnesAiClient.chat(prompt, List.of(buildUserMessage(idea)), 900, 0.6);
            parsed = parseJson(raw);
            if (parsed == null) {
                degraded = 1;
            }
        } catch (Exception e) {
            degraded = 1;
            log.warn("念生 AI 调用失败，降级记录: ideaId={}, kind={}, err={}", ideaId, kind, e.getMessage());
            parsed = null;
        }

        Map<String, Object> result = degraded == 1 ? degradedResult(raw) : parsed;

        IdeaAnalysis analysis = new IdeaAnalysis();
        analysis.setIdeaId(ideaId);
        analysis.setUserId(idea.getUserId());
        analysis.setKind(challenge ? IdeaAnalysis.KIND_CHALLENGE : IdeaAnalysis.KIND_ANALYZE);
        analysis.setModel(resolveModel());
        analysis.setPromptScene(scene);
        analysis.setResultJson(toJson(result));
        analysis.setDegraded(degraded);
        ideaAnalysisMapper.insertJson(analysis);

        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", analysis.getId());
        vo.put("kind", analysis.getKind());
        vo.put("model", analysis.getModel());
        vo.put("promptScene", analysis.getPromptScene());
        vo.put("degraded", degraded);
        vo.put("result", result);
        vo.put("createTime", analysis.getCreateTime() == null ? LocalDateTime.now() : analysis.getCreateTime());
        return vo;
    }

    public List<Map<String, Object>> analyses(Long currentUserId, Long targetUserId, Long ideaId, String kind) {
        getOwned(currentUserId, targetUserId, ideaId);
        LambdaQueryWrapper<IdeaAnalysis> wrapper = new LambdaQueryWrapper<IdeaAnalysis>()
                .eq(IdeaAnalysis::getIdeaId, ideaId)
                .orderByDesc(IdeaAnalysis::getCreateTime);
        if (StringUtils.isNotBlank(kind)) {
            wrapper.eq(IdeaAnalysis::getKind, kind);
        }
        return ideaAnalysisMapper.selectList(wrapper).stream().map(a -> {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("id", a.getId());
            vo.put("kind", a.getKind());
            vo.put("model", a.getModel());
            vo.put("promptScene", a.getPromptScene());
            vo.put("degraded", a.getDegraded());
            vo.put("result", parseJson(a.getResultJson()));
            vo.put("createTime", a.getCreateTime());
            return vo;
        }).collect(Collectors.toList());
    }

    // ==================== 内部 ====================

    private Long resolveScope(Long currentUserId, Long targetUserId) {
        if (!SecurityUtils.isAdmin()) {
            return currentUserId;
        }
        return targetUserId;
    }

    private Idea getOwned(Long currentUserId, Long targetUserId, Long ideaId) {
        Idea idea = ideaMapper.selectById(ideaId);
        if (idea == null) {
            throw new BusinessException(404, "想法不存在");
        }
        boolean admin = SecurityUtils.isAdmin();
        if (!admin && !idea.getUserId().equals(currentUserId)) {
            throw new BusinessException(403, "无权访问该想法");
        }
        if (admin && targetUserId != null && !idea.getUserId().equals(targetUserId)) {
            throw new BusinessException(403, "该想法不属于所选用户");
        }
        return idea;
    }

    private Map<String, IdeaState> stateMap(List<IdeaState> states) {
        return states.stream().collect(Collectors.toMap(IdeaState::getCode, s -> s));
    }

    private IdeaVo toVo(Idea idea, Map<String, IdeaState> states, String username) {
        IdeaState state = states.get(idea.getState());
        return IdeaVo.builder()
                .id(idea.getId())
                .userId(idea.getUserId())
                .username(username)
                .content(idea.getContent())
                .state(idea.getState())
                .stateName(state == null ? idea.getState() : state.getName())
                .stateColor(state == null ? "" : state.getColor())
                .isTerminal(state == null ? 0 : state.getIsTerminal())
                .priority(idea.getPriority())
                .tags(idea.getTags())
                .source(idea.getSource())
                .sourceInsightId(idea.getSourceInsightId())
                .createTime(idea.getCreateTime())
                .updateTime(idea.getUpdateTime())
                .allowedTargets(stateMachine.allowedTargets(idea.getState()))
                .build();
    }

    private Map<Long, String> loadUsernames(List<Idea> ideas) {
        Set<Long> ids = ideas.stream().map(Idea::getUserId).collect(Collectors.toSet());
        if (ids.isEmpty()) {
            return Map.of();
        }
        return sysUserMapper.selectBatchIds(ids).stream().collect(Collectors.toMap(
                SysUser::getId, u -> StringUtils.defaultIfBlank(u.getNickname(), u.getUsername())));
    }

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return "";
        }
        return tags.stream()
                .map(StringUtils::trimToNull)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .limit(8)
                .map(t -> t.length() > 24 ? t.substring(0, 24) : t)
                .collect(Collectors.joining(","));
    }

    private String buildUserMessage(Idea idea) {
        StringBuilder sb = new StringBuilder("想法内容：\n").append(idea.getContent());
        if (StringUtils.isNotBlank(idea.getTags())) {
            sb.append("\n\n标签：").append(idea.getTags());
        }
        sb.append("\n\n当前状态：").append(idea.getState());
        return sb.toString();
    }

    /** 只取第一个完整 JSON 对象，容忍模型加代码块或前后废话 */
    private Map<String, Object> parseJson(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        String s = raw.trim();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("```\\s*$", "").trim();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return objectMapper.readValue(s.substring(start, end + 1),
                    new TypeReference<Map<String, Object>>() {
                    });
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> degradedResult(String raw) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("degraded", true);
        result.put("raw", raw);
        result.put("message", StringUtils.isBlank(raw)
                ? "AI 暂不可用（供应商未配置或调用失败），已降级记录"
                : "模型输出无法解析为结构化 JSON，已降级为纯文本");
        return result;
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{\"degraded\":true}";
        }
    }

    private String resolveModel() {
        try {
            return aiProviderService.resolveActive().get("model");
        } catch (Exception e) {
            return "unavailable";
        }
    }

    /** 向量化失败不影响主流程（硅基流动未配置时静默跳过） */
    private void embedAsyncSafe(Long ideaId, String text) {
        try {
            float[] vec = agnesAiClient.embed(text);
            if (vec != null && vec.length > 0) {
                ideaMapper.updateEmbedding(ideaId, VectorUtil.toJson(VectorUtil.normalize(vec)));
            }
        } catch (Exception e) {
            log.warn("想法向量化失败（不阻断）: ideaId={}, err={}", ideaId, e.getMessage());
        }
    }
}
