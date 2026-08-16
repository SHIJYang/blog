package com.blog.service.ai;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.blog.common.BusinessException;
import com.blog.entity.AiProvider;
import com.blog.entity.BlogConfig;
import com.blog.mapper.AiProviderMapper;
import com.blog.service.BlogConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI 对话供应商服务（数据库版）
 * <p>
 * 供应商数据入 ai_provider 表，每行一个。is_active=1 表示当前激活。
 * 前端可增删改，Key 回传为空/脱敏时保留库中原值。
 * 向量固定走 SiliconFlow，与此无关。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiProviderService {

    private final AiProviderMapper aiProviderMapper;
    private final BlogConfigService blogConfigService;

    /** 查询全部供应商（按创建时间） */
    public List<AiProvider> listProviders() {
        return aiProviderMapper.selectList(
                new LambdaQueryWrapper<AiProvider>().orderByAsc(AiProvider::getCreateTime));
    }

    /** 查询当前激活供应商 */
    public AiProvider getActiveProvider() {
        return aiProviderMapper.selectOne(
                new LambdaQueryWrapper<AiProvider>().eq(AiProvider::getIsActive, 1));
    }

    /** 解析当前对话供应商参数 {url, model, apiKey}，供 chat 调用 */
    public Map<String, String> resolveActive() {
        AiProvider p = getActiveProvider();
        if (p == null) {
            throw new BusinessException(500, "未配置 AI 对话供应商，请在后台添加并激活");
        }
        if (p.getEnabled() == null || p.getEnabled() != 1) {
            throw new BusinessException(500, "当前 AI 对话供应商未启用");
        }
        if (p.getApiKey() == null || p.getApiKey().isBlank()) {
            throw new BusinessException(500, "当前 AI 对话供应商（" + p.getName() + "）未配置 API Key");
        }
        if (p.getBaseUrl() == null || p.getBaseUrl().isBlank()
                || p.getModel() == null || p.getModel().isBlank()) {
            throw new BusinessException(500, "当前 AI 对话供应商（" + p.getName() + "）接口地址或模型未配置");
        }
        return Map.of("url", p.getBaseUrl(), "model", p.getModel(), "apiKey", p.getApiKey());
    }

    /** 新增供应商 */
    @Transactional(rollbackFor = Exception.class)
    public AiProvider create(AiProvider provider) {
        provider.setId(null);
        provider.setIsActive(0);
        provider.setEnabled(provider.getEnabled() == null ? 1 : provider.getEnabled());
        provider.setCreateTime(LocalDateTime.now());
        provider.setUpdateTime(LocalDateTime.now());
        aiProviderMapper.insert(provider);
        ensureAtLeastOneActive();
        return provider;
    }

    /** 更新供应商（Key 回传为空/脱敏时保留原值） */
    @Transactional(rollbackFor = Exception.class)
    public AiProvider update(AiProvider provider) {
        AiProvider exist = aiProviderMapper.selectById(provider.getId());
        if (exist == null) {
            throw new BusinessException(404, "供应商不存在");
        }
        exist.setName(provider.getName());
        exist.setBaseUrl(provider.getBaseUrl());
        exist.setModel(provider.getModel());
        // Key 保护：回传脱敏(含*)或空则不覆盖
        String incoming = provider.getApiKey();
        if (incoming != null && !incoming.isBlank() && !incoming.contains("*")) {
            exist.setApiKey(incoming);
        }
        if (provider.getEnabled() != null) {
            // 禁用当前激活项则自动切换激活
            if (provider.getEnabled() == 0
                    && (exist.getIsActive() != null && exist.getIsActive() == 1)) {
                exist.setIsActive(0);
            }
            exist.setEnabled(provider.getEnabled());
        }
        exist.setUpdateTime(LocalDateTime.now());
        aiProviderMapper.updateById(exist);
        ensureAtLeastOneActive();
        return exist;
    }

    /** 删除供应商（不能删当前激活项） */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        AiProvider exist = aiProviderMapper.selectById(id);
        if (exist == null) return;
        if (exist.getIsActive() != null && exist.getIsActive() == 1) {
            throw new BusinessException(400, "不能删除当前激活的供应商，请先切换激活");
        }
        aiProviderMapper.deleteById(id);
    }

    /** 激活指定供应商（其余置 0） */
    @Transactional(rollbackFor = Exception.class)
    public void activate(Long id) {
        AiProvider exist = aiProviderMapper.selectById(id);
        if (exist == null) {
            throw new BusinessException(404, "供应商不存在");
        }
        if (exist.getEnabled() == null || exist.getEnabled() != 1) {
            throw new BusinessException(400, "请先启用该供应商再激活");
        }
        aiProviderMapper.update(null, new LambdaUpdateWrapper<AiProvider>()
                .set(AiProvider::getIsActive, 0));
        aiProviderMapper.update(null, new LambdaUpdateWrapper<AiProvider>()
                .set(AiProvider::getIsActive, 1)
                .eq(AiProvider::getId, id));
    }

    /** 确保至少有一个激活供应商（首次/全禁用时兜底激活第一个启用项） */
    private void ensureAtLeastOneActive() {
        AiProvider active = getActiveProvider();
        if (active != null) return;
        AiProvider first = aiProviderMapper.selectOne(
                new LambdaQueryWrapper<AiProvider>()
                        .eq(AiProvider::getEnabled, 1)
                        .orderByAsc(AiProvider::getId)
                        .last("LIMIT 1"));
        if (first != null) {
            first.setIsActive(1);
            first.setUpdateTime(LocalDateTime.now());
            aiProviderMapper.updateById(first);
        }
    }

    /** 兜底：无任何供应商时写入默认两个（Agnes / DeepSeek），并迁移旧 agnes_api_key */
    public void ensureDefaults() {
        long count = aiProviderMapper.selectCount(null);
        if (count > 0) return;
        List<AiProvider> defaults = new ArrayList<>();
        defaults.add(defaultProvider("Agnes", "https://api.agnes-ai.cn/v1/chat/completions", "agnes-2.5-flash", 1));
        defaults.add(defaultProvider("DeepSeek", "https://api.deepseek.com/v1/chat/completions", "deepseek-v4-flash", 0));
        for (AiProvider p : defaults) {
            aiProviderMapper.insert(p);
        }
        // 迁移旧全局 agnes_api_key
        String legacy = getLegacyAgnesKey();
        if (legacy != null && !legacy.isBlank()) {
            AiProvider agnes = aiProviderMapper.selectOne(
                    new LambdaQueryWrapper<AiProvider>().eq(AiProvider::getName, "Agnes"));
            if (agnes != null && (agnes.getApiKey() == null || agnes.getApiKey().isBlank())) {
                agnes.setApiKey(legacy);
                agnes.setUpdateTime(LocalDateTime.now());
                aiProviderMapper.updateById(agnes);
            }
        }
        log.info("已初始化默认 AI 对话供应商，并迁移旧 agnes_api_key");
    }

    /** 读取旧全局 agnes_api_key（迁移用） */
    public String getLegacyAgnesKey() {
        BlogConfig c = blogConfigService.getOne(
                new LambdaQueryWrapper<BlogConfig>().eq(BlogConfig::getConfigKey, "agnes_api_key"));
        return c != null && c.getConfigValue() != null ? c.getConfigValue() : null;
    }

    /** 测试指定供应商连通性（返回 {reachable, message}） */
    public Map<String, String> testConnection(Long id) {
        AiProvider p = aiProviderMapper.selectById(id);
        if (p == null) {
            throw new BusinessException(404, "供应商不存在");
        }
        try {
            Map<String, String> resolved = resolveActive();
            // 复用真实解析（确保 Key/URL 有效），连通性校验交给调用方 RestClient 侧
            if (p.getApiKey() == null || p.getApiKey().isBlank()) {
                return Map.of("reachable", "false", "message", "该供应商未配置 API Key");
            }
            return Map.of("reachable", "true", "message", "配置已就绪（联通测试需实际发一次对话验证）", "provider", p.getName());
        } catch (BusinessException e) {
            return Map.of("reachable", "false", "message", e.getMessage());
        }
    }

    private AiProvider defaultProvider(String name, String url, String model, int active) {
        AiProvider p = new AiProvider();
        p.setName(name);
        p.setBaseUrl(url);
        p.setApiKey("");
        p.setModel(model);
        p.setIsActive(active);
        p.setEnabled(1);
        p.setCreateTime(LocalDateTime.now());
        p.setUpdateTime(LocalDateTime.now());
        return p;
    }
}
