package com.blog.service.ai;

import com.blog.common.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 图像识别服务
 * 调用多模态 AI 识别照片内容，生成记忆标题与照片描述
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ImageRecognitionService {

    private final AgnesAiClient agnesAiClient;

    /**
     * 识别图片并生成记忆标题
     */
    public String recognizeTitle(String imageUrl) {
        String prompt = "请用不超过15个字概括这张照片的核心内容，直接输出标题，不要加引号和标点，不要解释。";
        try {
            String title = agnesAiClient.chat(
                    "你是一个温柔的记忆助手，擅长为照片起简洁有画面感的标题。",
                    List.of(AgnesAiClient.imageMessage(imageUrl, prompt)),
                    50, 0.7);
            return cleanTitle(title);
        } catch (BusinessException e) {
            log.warn("图片标题识别失败，使用默认标题: {}", e.getMessage());
            return "我的记忆";
        }
    }

    /**
     * 识别图片并生成照片描述（用于凝聚日记时作为素材）
     */
    public String describeImage(String imageUrl) {
        try {
            return agnesAiClient.chat(
                    "你是一个细心的观察者，请用2-3句话描述照片中的场景、人物、氛围，直接输出描述。",
                    List.of(AgnesAiClient.imageMessage(imageUrl,
                            "请详细描述这张照片：场景、人物、动作、氛围、可能的故事。")),
                    200, 0.7);
        } catch (BusinessException e) {
            log.warn("图片描述失败: {}", e.getMessage());
            return "";
        }
    }

    private String cleanTitle(String raw) {
        if (raw == null) return "我的记忆";
        String t = raw.replaceAll("[\"\"'']", "").trim();
        if (t.length() > 30) t = t.substring(0, 30);
        return t.isEmpty() ? "我的记忆" : t;
    }
}
