package com.blog.service.ai;

import com.blog.common.BusinessException;
import com.blog.entity.BlogConfig;
import com.blog.service.BlogConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Agnes AI 网关客户端
 * <p>
 * 统一封装 Chat 补全（含多模态图像输入）与 Embedding 调用。
 * API Key 从博客系统配置表 blog_config(key=agnes_api_key) 实时读取，
 * 管理后台可随时修改，无需重启。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgnesAiClient {

    /** 硅基流动 Embedding Key（唯一向量通道，bge-m3 稳定可用） */
    public static final String SILICONFLOW_API_KEY_CONFIG = "siliconflow_api_key";

    private final BlogConfigService blogConfigService;
    private final AiProviderService aiProviderService;
    private final RestClient restClient;

    @Value("${agnes.api.chat-url:https://api.agnes-ai.cn/v1/chat/completions}")
    private String chatUrl;

    @Value("${agnes.api.embedding-url:https://api.siliconflow.cn/v1/embeddings}")
    private String embeddingUrl;

    @Value("${agnes.api.embedding-model:BAAI/bge-m3}")
    private String embeddingModel;

    @Value("${agnes.api.chat-model:agnes-2.5-flash}")
    private String chatModel;

    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("[AgnesAiClient] chatUrl={}, embeddingUrl={}, chatModel='{}'", chatUrl, embeddingUrl, chatModel);
        log.info("[AgnesAiClient] 向量仅走硅基流动，Agnes 官方无向量模型");
    }

    /** 通用：从系统配置表读取任意配置值（实时读库，后台可改） */
    private String getConfigValue(String key) {
        BlogConfig config = blogConfigService.getOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<BlogConfig>()
                        .eq(BlogConfig::getConfigKey, key));
        return config != null ? config.getConfigValue() : null;
    }

    /**
     * Chat 补全（纯文本）
     *
     * @param systemPrompt 系统提示词
     * @param userMessages 用户消息（String 或 image_url Map 混合）
     */
    public String chat(String systemPrompt, List<Object> userMessages) {
        return chat(systemPrompt, userMessages, 1024, 0.8);
    }

    /**
     * Chat 补全（多模态/可调参数）
     */
    public String chat(String systemPrompt, List<Object> userMessages, int maxTokens, double temperature) {
        try {
            // 实时解析当前激活的对话供应商（数据库版）
            Map<String, String> provider = aiProviderService.resolveActive();
            String url     = provider.get("url");
            String model   = provider.get("model");
            String apiKey  = provider.get("apiKey");
            if (apiKey == null || apiKey.isBlank()) {
                throw new BusinessException(500, "当前 AI 对话供应商未配置 API Key");
            }
            if (url == null || url.isBlank() || model == null || model.isBlank()) {
                throw new BusinessException(500, "当前 AI 对话供应商配置不完整");
            }

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);

            List<Map<String, Object>> messages = new ArrayList<>();
            if (systemPrompt != null && !systemPrompt.isBlank()) {
                Map<String, Object> sys = new HashMap<>();
                sys.put("role", "system");
                sys.put("content", systemPrompt);
                messages.add(sys);
            }
            for (Object content : userMessages) {
                Map<String, Object> msg = new HashMap<>();
                msg.put("role", "user");
                if (content instanceof Map<?, ?> mapContent && mapContent.containsKey("content")) {
                    // 多模态消息：imageMessage() 返回 {content: [...]}，直接展开为消息体
                    for (Map.Entry<?, ?> e : mapContent.entrySet()) {
                        if (e.getKey() instanceof String k) {
                            msg.put(k, e.getValue());
                        }
                    }
                } else {
                    // 纯文本消息
                    msg.put("content", content);
                }
                messages.add(msg);
            }
            body.put("messages", messages);
            body.put("max_tokens", maxTokens);
            body.put("temperature", temperature);

            ResponseEntity<Map> resp = restClient.post()
                    .uri(url)
                    .headers(h -> {
                        h.setBearerAuth(apiKey);
                        h.setContentType(MediaType.APPLICATION_JSON);
                    })
                    .body(body)
                    .retrieve()
                    .toEntity(Map.class);

            Map choices = (Map) ((List<?>) resp.getBody().get("choices")).get(0);
            Map message = (Map) choices.get("message");
            String content = (String) message.get("content");
            return content != null ? content.trim() : "";
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Chat 调用失败，真实异常栈:", e);
            throw new BusinessException(500, "AI 服务暂时不可用，请稍后再试");
        }
    }

    /**
     * 纯文本消息构造（String 形式，OpenAI 兼容接口可直接传字符串）
     */
    public static String textMessage(String text) {
        return text;
    }

    /**
     * 图像消息构造（多模态，data URL 或 http(s) URL 均可）
     */
    public static Map<String, Object> imageMessage(String imageUrl, String prompt) {
        List<Object> content = new ArrayList<>();
        content.add(Map.of("type", "text", "text", prompt));
        content.add(Map.of("type", "image_url", "image_url", Map.of("url", imageUrl)));
        return Map.of("content", content);
    }

    /**
     * 生成文本向量（Embedding）
     * <p>
     * 仅走硅基流动（blog_config.siliconflow_api_key），bge-m3 免费 1024 维。
     * Agnes 官方无向量模型，不支持回退。
     *
     * @return 浮点向量数组；硅基流动不可用时返回 null（上层走关键词匹配降级）
     */
    public float[] embed(String text) {
        String sfKey = getConfigValue(SILICONFLOW_API_KEY_CONFIG);
        if (sfKey == null || sfKey.isBlank()) {
            log.warn("未配置 siliconflow_api_key，向量检索降级为关键词匹配");
            return null;
        }
        try {
            return embedWith(embeddingUrl, sfKey, embeddingModel, text);
        } catch (Exception e) {
            log.warn("硅基流动 Embedding 失败，降级为关键词匹配: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 通用 Embedding 调用（OpenAI 兼容接口）
     */
    private float[] embedWith(String url, String apiKey, String model, String text) {
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("input", text);

        ResponseEntity<Map> resp = restClient.post()
                .uri(url)
                .headers(h -> {
                    h.setBearerAuth(apiKey);
                    h.setContentType(MediaType.APPLICATION_JSON);
                })
                .body(body)
                .retrieve()
                .toEntity(Map.class);

        List<?> data = (List<?>) resp.getBody().get("data");
        if (data == null || data.isEmpty()) return null;
        Map first = (Map) data.get(0);
        List<?> emb = (List<?>) first.get("embedding");
        float[] result = new float[emb.size()];
        for (int i = 0; i < emb.size(); i++) {
            result[i] = ((Number) emb.get(i)).floatValue();
        }
        return result;
    }
}
