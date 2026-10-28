package com.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.common.BusinessException;
import com.blog.vo.RecallVo;
import com.blog.vo.ChatReplyVo;
import com.blog.entity.MemoryMessage;
import com.blog.entity.MemoryNote;
import com.blog.mapper.MemoryMessageMapper;
import com.blog.mapper.MemoryNoteMapper;
import com.blog.service.ai.AgnesAiClient;
import com.blog.service.ai.ImageRecognitionService;
import com.blog.service.vector.MemoryVectorService;
import com.blog.util.DiaryFormatter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 记忆核心业务服务 —— 拾光（拾光碎念）
 * <p>
 * 对话、凝聚、跨对话记忆注入（情绪锚点 + 时序羁绊 + 三级私藏权限）。
 * AI 人设：拾光，用户专属的私人时光收纳者——温柔、安静、通透、有同理心，
 * 只收纳不评判、只温柔不打扰、不主动追问、不说教不鸡汤不总结人生。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryCoreService {

    /** 隐私等级 */
    private static final int PRIVACY_PUBLIC = 0;
    private static final int PRIVACY_PRIVATE = 1;
    private static final int PRIVACY_SEALED = 2;

    private final MemoryNoteMapper memoryNoteMapper;
    private final MemoryMessageMapper memoryMessageMapper;
    private final AgnesAiClient agnesAiClient;
    private final ImageRecognitionService imageRecognitionService;
    private final MemoryVectorService memoryVectorService;
    private final MemoryStatService memoryStatService;
    private final MemoryEmotionService memoryEmotionService;
    private final MemoryInsightService memoryInsightService;
    private final MemoryVisibilityService visibilityService;
    private final com.blog.service.MemoryProjectionService projectionService;

    /** 封面图描述缓存（memoryId -> AI描述），避免每次对话重复调多模态识别 */
    private final Map<Long, String> coverDescCache = new ConcurrentHashMap<>();

    /** 凝聚进行中标记（memoryId 并发锁，防手动+自动同时凝聚同一记忆） */
    private final java.util.Set<Long> condensingLocks = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // ---------------- 对话 ----------------

    /**
     * 获取 AI 开场白（用户上传照片后，拾光温柔收纳，留白陪伴，不强行追问）
     */
    public String getOpening(Long userId, Long memoryId) {
        MemoryNote note = getOwnedNote(userId, memoryId);
        String title = StringUtils.defaultIfBlank(note.getAutoTitle(), "这张照片");
        String systemPrompt = "你是「拾光」，用户专属的私人时光收纳者。温柔、安静、通透、有同理心。"
                + "用户刚刚分享了一张照片（标题：" + title + "）。请用一两句自然、安静、留有回旋余地的话温柔收纳这一刻，"
                + "像安静陪伴一样表达『我看见了，我接住了』，不要追问、不要抛开放式问题、不打扰、不说教、不鸡汤、不总结。"
                + "不要使用表情符号以外的符号，不要输出标题，直接说内容。";
        try {
            return agnesAiClient.chat(systemPrompt, List.of("我刚拍了张照片，收在这里。标题是：" + title), 120, 0.85);
        } catch (BusinessException e) {
            return "我把它收好了。这一刻，你愿意的话，随时说给自己听。";
        }
    }

    /**
     * 发送对话消息（含情绪锚点 + 跨对话长期记忆召回 + 三级隐私）
     * <p>
     * 流程：保存用户消息 → 提取情绪 → 情绪入消息 + 回写记忆聚合 →
     * 召回（情绪优先/时序加权/排除封存）→ 组装上下文（本次对话 + 允许提及的回忆）→ AI 回复 →
     * 回写聚合并保存 AI 回复。
     */
    @Transactional(rollbackFor = Exception.class)
    public ChatReplyVo chat(Long userId, Long memoryId, String content) {
        MemoryNote note = getOwnedNote(userId, memoryId);

        // 1. 保存用户消息
        MemoryMessage userMsg = new MemoryMessage();
        userMsg.setMemoryId(memoryId);
        userMsg.setUserId(userId);
        userMsg.setMsgType(MemoryMessage.TYPE_USER_TEXT);
        userMsg.setContent(content);
        userMsg.setCreateTime(LocalDateTime.now());
        memoryMessageMapper.insert(userMsg);

        // 2. 提取情绪并落库（情绪锚点记忆核心）
        String emotion = memoryEmotionService.extractEmotions(content);
        if (StringUtils.isNotBlank(emotion)) {
            MemoryMessage emotionUpd = new MemoryMessage();
            emotionUpd.setId(userMsg.getId());
            emotionUpd.setEmotion(emotion);
            memoryMessageMapper.updateById(emotionUpd);
            memoryEmotionService.mergeIntoNote(memoryId, emotion);
        }

        // 3. 跨对话记忆召回：情绪优先 + 时序加权 + 排除封存
        List<RecallVo> recalls = memoryVectorService.recall(userId, content, emotion);
        boolean recalled = !recalls.isEmpty();

        // 4. 组装上下文：本次对话历史 + 当前照片描述 + 允许的回忆 + 记忆隐私上下文 + 当前记忆主情绪
        String history = buildHistoryText(userId, memoryId, 20);
        String photoContext = buildPhotoContext(note);
        String recallText = buildRecallText(recalls);
        String privacyContext = buildPrivacyContext(note.getPrivacy());
        String memoryEmotionContext = buildMemoryEmotionContext(note.getEmotions());

        String systemPrompt = "你是「拾光」，用户专属的私人时光收纳者。温柔、安静、通透、有同理心，像永远安静陪伴的自己。\n"
                + "【我的原则】\n"
                + "- 不主动打扰、不刻意搭话、不说教、不鸡汤、不总结人生、不追问。\n"
                + "- 优先感知用户当下的情绪，其次回应内容。\n"
                + "- 用户无逻辑碎碎念、无意义闲聊、沉默放空，我都安静接纳，不勉强、不催促。\n"
                + "- 用户低落时温柔包容，开心时轻轻呼应，平淡时安静陪伴。\n"
                + "- 使用过去记忆时只做温柔的情绪共鸣，不强行关联、不生硬提及、不主动翻旧账。\n"
                + "- 用户提到『照片』『图片』『这张图』或要求『描述一下图片』时，直接根据照片描述告诉 TA 画面里有什么，自然、具体地描述，不要说『看不到图片』『没收到图片』『画面被藏起来』。\n"
                + privacyContext
                + memoryEmotionContext
                + photoContext
                + "【可参考的用户历史记忆（仅供情绪共鸣，温和自然地融入）】\n" + recallText + "\n"
                + "请用私人、松弛、口语化的语气回复，保留留白感，不用 markdown，单次回复不超过 150 字。";

        // 5. 调用 AI
        String reply;
        try {
            reply = agnesAiClient.chat(systemPrompt, List.of(history + "\n\n用户说：" + content), 300, 0.85);
        } catch (BusinessException e) {
            throw e;
        }
        if (StringUtils.isBlank(reply)) {
            reply = "嗯，我在。";
        }

        // 6. 保存 AI 回复
        MemoryMessage aiMsg = new MemoryMessage();
        aiMsg.setMemoryId(memoryId);
        aiMsg.setUserId(userId);
        aiMsg.setMsgType(MemoryMessage.TYPE_AI_TEXT);
        aiMsg.setContent(reply);
        aiMsg.setCreateTime(LocalDateTime.now());
        memoryMessageMapper.insert(aiMsg);

        // 7. 更新记忆状态为「对话中」
        if (note.getStatus() == MemoryNote.STATUS_NOT_STARTED) {
            MemoryNote update = new MemoryNote();
            update.setId(memoryId);
            update.setStatus(MemoryNote.STATUS_CHATTING);
            memoryNoteMapper.updateById(update);
        }

        return ChatReplyVo.builder()
                .reply(reply)
                .status(note.getStatus() == MemoryNote.STATUS_NOT_STARTED ? MemoryNote.STATUS_CHATTING : note.getStatus())
                .recalled(recalled)
                .relatedMemories(recalls)
                .emotion(emotion)
                .build();
    }

    /**
     * 追加对话图片（含情绪提取）
     */
    public MemoryMessage addImageWithReturn(Long userId, Long memoryId, String imageUrl, String description) {
        MemoryNote note = getOwnedNote(userId, memoryId);
        MemoryMessage imgMsg = new MemoryMessage();
        imgMsg.setMemoryId(memoryId);
        imgMsg.setUserId(userId);
        imgMsg.setMsgType(MemoryMessage.TYPE_USER_IMAGE);
        imgMsg.setImgUrl(imageUrl);
        imgMsg.setContent(description);
        imgMsg.setCreateTime(LocalDateTime.now());
        memoryMessageMapper.insert(imgMsg);

        // 图片也有情绪：基于 AI 描述提取
        if (StringUtils.isNotBlank(description)) {
            String emotion = memoryEmotionService.extractEmotions(description);
            if (StringUtils.isNotBlank(emotion)) {
                MemoryMessage emotionUpd = new MemoryMessage();
                emotionUpd.setId(imgMsg.getId());
                emotionUpd.setEmotion(emotion);
                memoryMessageMapper.updateById(emotionUpd);
                memoryEmotionService.mergeIntoNote(memoryId, emotion);
            }
        }
        return imgMsg;
    }

    public void addImage(Long userId, Long memoryId, String imageUrl, String description) {
        addImageWithReturn(userId, memoryId, imageUrl, description);
    }

    /**
     * AI 识别对话中追加的图片并生成描述
     */
    public String describeImageForMessage(Long userId, Long memoryId, String imageUrl) {
        getOwnedNote(userId, memoryId);
        return imageRecognitionService.describeImage(imageUrl);
    }

    // ---------------- 凝聚 ----------------

    /**
     * 凝聚记忆生成日记 —— 拾光 私人碎碎念文风
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryNote condense(Long userId, Long memoryId, String customTitle) {
        // 并发防护：同一记忆同一时刻只允许一个凝聚（手动 + 自动浓缩并发时会撞车）
        if (!condensingLocks.add(memoryId)) {
            log.info("记忆 {} 正在凝聚中，跳过本次请求", memoryId);
            throw new BusinessException(400, "该记忆正在凝聚中，请稍候");
        }
        try {
            return condenseInternal(userId, memoryId, customTitle);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("凝聚失败（真实异常）: userId={}, memoryId={}", userId, memoryId, e);
            throw new BusinessException(500, "凝聚失败: " + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()));
        } finally {
            condensingLocks.remove(memoryId);
        }
    }

    public MemoryNote condenseInternal(Long userId, Long memoryId, String customTitle) {
        MemoryNote note = getOwnedNote(userId, memoryId);
        // 已成念(3)也允许重新凝聚（覆盖日记与向量）；未开始(1)/对话中(2)正常凝聚
        // 已废弃()不允许
        if (note.getStatus() == MemoryNote.STATUS_ABANDONED) {
            throw new BusinessException(400, "已废弃的记忆无法凝聚");
        }

        // 1. 聚合对话数据
        List<MemoryMessage> messages = memoryMessageMapper.selectList(
                new LambdaQueryWrapper<MemoryMessage>()
                        .eq(MemoryMessage::getMemoryId, memoryId)
                        .orderByAsc(MemoryMessage::getCreateTime));
        if (messages.isEmpty()) {
            throw new BusinessException(400, "还没有任何内容，先和拾光聊聊吧");
        }

        // 2. 照片描述（容错：失败不阻断凝聚）
        String photoDesc = "";
        if (StringUtils.isNotBlank(note.getCoverPhoto())) {
            try {
                photoDesc = imageRecognitionService.describeImage(note.getCoverPhoto());
            } catch (Exception e) {
                log.warn("凝聚时封面图描述失败，忽略: {}", e.getMessage());
                photoDesc = "";
            }
        }

        // 3. 文风 Prompt（拾光 专属日记风格）
        String conversation = buildConversationText(messages);
        String title = StringUtils.defaultIfBlank(customTitle, note.getAutoTitle());
        if (StringUtils.isBlank(title)) {
            title = "我的时光";
        }
        String emotionContext = buildMemoryEmotionContext(note.getEmotions());
        String prompt = "你是「拾光」，一颗替用户收藏日常的心。请根据下面的照片与对话，写一篇第一人称的私人时光存档（约 200-300 字）。\n"
                + "【文风铁律】\n"
                + "- 保留真实细碎、矫情、松弛、平淡，拒绝升华、拒绝感悟、拒绝说教、拒绝鸡汤。\n"
                + "- 不取悦任何人，只忠于今天的日常。写『不值得发朋友圈、却值得自己珍藏』的小事。\n"
                + "- 以「我」的视角，自然融入照片里的场景与对话里的真实情绪，不提及『用户/拾光/AI』。\n"
                + emotionContext
                + "【照片描述】\n" + (StringUtils.isBlank(photoDesc) ? "（无）" : photoDesc) + "\n\n"
                + "【对话记录】\n" + conversation;
        String aiDiary;
        try {
            aiDiary = agnesAiClient.chat("你是日记生成引擎，只输出日记正文。", List.of(prompt), 800, 0.8);
        } catch (Exception e) {
            log.error("凝聚日记生成失败，使用兜底文案: {}", e.getMessage(), e);
            aiDiary = null;
        }
        if (StringUtils.isBlank(aiDiary)) {
            aiDiary = "翻到这些记录，发现今天其实发生了不少小事。有些日子看起来平淡，回头却都在发光。";
        }
        String diary = DiaryFormatter.format(aiDiary, title);

        // 4. 更新记忆状态
        MemoryNote update = new MemoryNote();
        update.setId(memoryId);
        update.setFinalDiary(diary);
        update.setStatus(MemoryNote.STATUS_CONDENSED);
        update.setFinishTime(LocalDateTime.now());
        update.setAutoTitle(title);
        memoryNoteMapper.updateById(update);

        // 5. 记忆入库向量化（含隐私快照 + 主情绪）
        String primaryEmotion = extractPrimaryEmotion(note.getEmotions());
        String vectorText = title + "\n" + diary + "\n" + conversation;
        int privacy = note.getPrivacy() != null ? note.getPrivacy() : PRIVACY_PRIVATE;
        memoryVectorService.storeEmbedding(userId, memoryId, title, vectorText, privacy, primaryEmotion);

        // 5b. 生成一句话知识点（每条独立向量化，AI 可精确召回某一句）
        try {
            memoryInsightService.generateForMemory(userId, memoryId, title, diary, conversation);
        } catch (Exception e) {
            log.warn("知识点生成失败: memoryId={}, err={}", memoryId, e.getMessage());
        }

        // 6. 更新用户统计 + 情绪归并兜底（失败不阻断凝聚，避免整体回滚）
        try {
            memoryStatService.onMemoryCondensed(userId);
        } catch (Exception e) {
            log.error("用户统计更新失败（不阻断凝聚）: {}", e.getMessage(), e);
        }
        try {
            memoryEmotionService.mergeEmotions(userId);
        } catch (Exception e) {
            log.warn("情绪归并失败: {}", e.getMessage());
        }

        MemoryNote refreshed = memoryNoteMapper.selectById(memoryId);
        // D12/D13：公开记忆凝聚出新正文时，投影文章跟着更新
        projectionService.syncFromNote(refreshed);
        log.info("记忆凝聚完成: userId={}, memoryId={}", userId, memoryId);
        return refreshed;
    }

    // ---------------- 内部工具 ----------------

    private String buildPrivacyContext(Integer privacy) {
        int p = privacy != null ? privacy : PRIVACY_PRIVATE;
        return switch (p) {
            case PRIVACY_PUBLIC -> "【本记忆隐私：公开】这一页是可以自然分享、被自在提及的记忆。\n";
            case PRIVACY_SEALED -> "【本记忆隐私：封存】这一页是用户封存的心事，AI 只存档、绝不主动提及、绝不展开，只安静陪伴。\n";
            default -> "【本记忆隐私：私密】这一页是用户私密的心事，只有用户和我知道，温和关联但从不外传、从不当面戳破。\n";
        };
    }

    private String buildMemoryEmotionContext(String emotions) {
        if (StringUtils.isBlank(emotions)) return "";
        return "【这一页的情绪底色】" + emotions + "。请温柔贴合这份情绪，不要用力渲染。\n";
    }

    /**
     * 当前记忆封面照片描述（供对话中拾光感知『这张照片』）
     * 首次调用 AI 多模态识别并缓存（memoryId -> 描述），避免重复扣费/耗时
     */
    private String buildPhotoContext(MemoryNote note) {
        if (note == null || StringUtils.isBlank(note.getCoverPhoto())) {
            log.info("buildPhotoContext: memoryId={} 无封面图", note == null ? null : note.getId());
            return "";
        }
        String desc = coverDescCache.get(note.getId());
        if (desc == null) {
            try {
                desc = imageRecognitionService.describeImage(note.getCoverPhoto());
                log.info("buildPhotoContext: memoryId={} describeImage 结果长度={}, 内容={}",
                        note.getId(), desc == null ? 0 : desc.length(), desc == null ? "null" : desc.substring(0, Math.min(desc.length(), 100)));
            } catch (Exception e) {
                log.warn("封面图描述失败 memoryId={}: {}", note.getId(), e.getMessage());
                desc = "";
            }
            coverDescCache.put(note.getId(), desc == null ? "" : desc);
        }
        if (StringUtils.isBlank(desc)) {
            return "";
        }
        return "【当前这张照片（AI 视角）】" + desc + "\n";
    }

    private String extractPrimaryEmotion(String emotions) {
        if (StringUtils.isBlank(emotions)) return null;
        // 取第一个情绪作为主情绪标签
        String first = emotions.split("[,，\\s]+")[0].trim();
        return first.isEmpty() ? null : first;
    }

    /**
     * 校验记忆归属（防止跨用户数据访问）。
     * 普通用户只能操作自己的记忆；管理员可操作任意用户的记忆。
     */
    public MemoryNote getOwnedNote(Long userId, Long memoryId) {
        MemoryNote note = memoryNoteMapper.selectById(memoryId);
        if (note == null || (note.getIsDeleted() != null && note.getIsDeleted() == 1)) {
            throw new BusinessException(404, "记忆不存在");
        }
        boolean admin = com.blog.security.utils.SecurityUtils.isAdmin();
        if (!note.getUserId().equals(userId) && !admin) {
            throw new BusinessException(403, "无权访问该记忆");
        }
        // D2：封存记忆连本人也要先 PIN 解锁，否则对话/凝聚都会泄露内容
        visibilityService.requireSealedAccess(note);
        return note;
    }

    private String buildHistoryText(Long userId, Long memoryId, int limit) {
        List<MemoryMessage> messages = memoryMessageMapper.selectList(
                new LambdaQueryWrapper<MemoryMessage>()
                        .eq(MemoryMessage::getMemoryId, memoryId)
                        .orderByDesc(MemoryMessage::getCreateTime)
                        .last("LIMIT " + limit));
        if (messages.isEmpty()) return "";
        List<MemoryMessage> reversed = new ArrayList<>(messages);
        java.util.Collections.reverse(reversed);
        StringBuilder sb = new StringBuilder("【本次对话记录】\n");
        for (MemoryMessage m : reversed) {
            String who = m.getMsgType() == MemoryMessage.TYPE_AI_TEXT ? "拾光" : "用户";
            String content = StringUtils.defaultIfBlank(m.getContent(), "（图片）");
            sb.append(who).append("：").append(content).append("\n");
        }
        return sb.toString();
    }

    private String buildConversationText(List<MemoryMessage> messages) {
        StringBuilder sb = new StringBuilder();
        for (MemoryMessage m : messages) {
            String who = m.getMsgType() == MemoryMessage.TYPE_AI_TEXT ? "拾光" : "我";
            String content = StringUtils.defaultIfBlank(m.getContent(), "（发了一张照片）");
            sb.append(who).append("：").append(content).append("\n");
        }
        return sb.toString();
    }

    private String buildRecallText(List<RecallVo> recalls) {
        if (recalls.isEmpty()) return "（暂无）";
        StringBuilder sb = new StringBuilder();
        for (RecallVo r : recalls) {
            sb.append("- ").append(r.getTitle());
            if (StringUtils.isNotBlank(r.getEmotion())) {
                sb.append("（曾带着「").append(r.getEmotion()).append("」的情绪）");
            }
            if (StringUtils.isNotBlank(r.getSnippet())) {
                sb.append("：").append(r.getSnippet());
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
