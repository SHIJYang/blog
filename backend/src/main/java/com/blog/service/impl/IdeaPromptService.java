package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blog.common.BusinessException;
import com.blog.dto.AiPromptSaveDto;
import com.blog.entity.AiPrompt;
import com.blog.mapper.AiPromptMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Prompt 治理：按 scene 版本化，支持 A/B 与回滚。
 * <p>
 * 表里没有可用版本时用代码内置默认值兜底，保证念生在任何部署状态下都能跑。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IdeaPromptService {

    public static final String SCENE_ANALYZE = "niansheng.analyze";
    public static final String SCENE_CHALLENGE = "niansheng.challenge";

    private final AiPromptMapper aiPromptMapper;

    /** 启动期补默认 Prompt（幂等：已有激活版本则跳过） */
    @Transactional(rollbackFor = Exception.class)
    public void ensureDefaults() {
        seedIfMissing(SCENE_ANALYZE, defaultAnalyzePrompt());
        seedIfMissing(SCENE_CHALLENGE, defaultChallengePrompt());
    }

    private void seedIfMissing(String scene, String content) {
        Long count = aiPromptMapper.selectCount(new LambdaQueryWrapper<AiPrompt>()
                .eq(AiPrompt::getScene, scene)
                .eq(AiPrompt::getIsActive, 1));
        if (count != null && count > 0) {
            return;
        }
        AiPrompt prompt = new AiPrompt();
        prompt.setScene(scene);
        prompt.setVersion(nextVersion(scene));
        prompt.setContent(content);
        prompt.setIsActive(1);
        prompt.setRemark("内置默认");
        prompt.setCreateTime(LocalDateTime.now());
        aiPromptMapper.insert(prompt);
    }

    /** 取当前生效提示词；没有则用内置默认 */
    public String resolve(String scene) {
        AiPrompt prompt = activePrompt(scene);
        if (prompt != null && prompt.getContent() != null && !prompt.getContent().isBlank()) {
            return prompt.getContent();
        }
        return SCENE_CHALLENGE.equals(scene) ? defaultChallengePrompt() : defaultAnalyzePrompt();
    }

    public AiPrompt activePrompt(String scene) {
        return aiPromptMapper.selectOne(new LambdaQueryWrapper<AiPrompt>()
                .eq(AiPrompt::getScene, scene)
                .eq(AiPrompt::getIsActive, 1)
                .last("LIMIT 1"));
    }

    public List<AiPrompt> listAll() {
        return aiPromptMapper.selectList(new LambdaQueryWrapper<AiPrompt>()
                .orderByAsc(AiPrompt::getScene)
                .orderByDesc(AiPrompt::getVersion));
    }

    public List<AiPrompt> listByScene(String scene) {
        return aiPromptMapper.selectList(new LambdaQueryWrapper<AiPrompt>()
                .eq(AiPrompt::getScene, scene)
                .orderByDesc(AiPrompt::getVersion));
    }

    @Transactional(rollbackFor = Exception.class)
    public AiPrompt create(AiPromptSaveDto dto) {
        AiPrompt prompt = new AiPrompt();
        prompt.setScene(dto.getScene().trim());
        prompt.setVersion(nextVersion(dto.getScene().trim()));
        prompt.setContent(dto.getContent());
        prompt.setIsActive(0);
        prompt.setRemark(dto.getRemark());
        prompt.setCreateTime(LocalDateTime.now());
        aiPromptMapper.insert(prompt);
        if (Boolean.TRUE.equals(dto.getActivate())) {
            activate(prompt.getId());
        }
        return prompt;
    }

    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id) {
        AiPrompt prompt = aiPromptMapper.selectById(id);
        if (prompt == null) {
            throw new BusinessException(404, "Prompt 不存在");
        }
        // 同一 scene 只允许一条激活（DB 也有部分唯一索引兜底）
        aiPromptMapper.update(null, new LambdaUpdateWrapper<AiPrompt>()
                .eq(AiPrompt::getScene, prompt.getScene())
                .set(AiPrompt::getIsActive, 0));
        AiPrompt update = new AiPrompt();
        update.setId(id);
        update.setIsActive(1);
        aiPromptMapper.updateById(update);
    }

    private int nextVersion(String scene) {
        List<AiPrompt> list = listByScene(scene);
        return list.isEmpty() ? 1 : list.get(0).getVersion() + 1;
    }

    private String defaultAnalyzePrompt() {
        return """
                你是「念生」，一个帮用户把模糊想法拆开看的思考助手。你只分析想法本身，不评价提出想法的人。
                请严格输出 JSON（不要 Markdown、不要代码块、不要多余文字），结构如下：
                {"claims":["想法里的核心主张"],"assumptions":["支撑它的隐含假设"],
                 "risks":["可能踩的坑"],"questions":["值得继续追问的问题"]}
                每条 1~2 句，中文，克制、具体、不空泛；每类 2~5 条。
                """;
    }

    private String defaultChallengePrompt() {
        return """
                你是「念生」，负责温和但坚定地质疑一个想法。质疑的对象是假设与推理，不是提出想法的人。
                语气对事不对人，不嘲讽、不否定人格，不使用"你错了"这类表述。
                请严格输出 JSON（不要 Markdown、不要代码块、不要多余文字），结构如下：
                {"counterpoints":["最站得住的反面论据"],"blindSpots":["可能被忽略的视角"],
                 "falsify":["能证伪这个想法的观察或实验"],"questions":["反问自己的问题"]}
                每条 1~2 句，中文，具体、可验证；每类 2~4 条。
                """;
    }
}
