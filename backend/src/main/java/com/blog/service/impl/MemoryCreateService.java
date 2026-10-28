package com.blog.service.impl;

import com.blog.common.BusinessException;
import com.blog.dto.MemoryFragmentDto;
import com.blog.entity.MemoryNote;
import com.blog.entity.MemoryPhoto;
import com.blog.mapper.MemoryNoteMapper;
import com.blog.service.MemoryProjectionService;
import com.blog.service.ai.ImageRecognitionService;
import com.blog.service.vector.MemoryVectorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 记忆创建服务。
 * <p>
 * 照片记忆走**受控存储**（本地目录 + 鉴权读接口，D5）；
 * 纯文本碎念走 {@link #createFragment}，不再强制带图。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryCreateService {

    private final MemoryNoteMapper memoryNoteMapper;
    private final ImageRecognitionService imageRecognitionService;
    private final MemoryStatService memoryStatService;
    private final MemoryEmotionService memoryEmotionService;
    private final MemoryVectorService memoryVectorService;
    private final MemoryPhotoService memoryPhotoService;
    private final MemoryProjectionService projectionService;

    /**
     * 创建照片记忆（默认私密；新照片写入受控存储，不再走公网图床）。
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryNote create(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请上传照片");
        }
        MemoryNote note = new MemoryNote();
        note.setUserId(userId);
        note.setKind(MemoryNote.KIND_PHOTO);
        note.setStatus(MemoryNote.STATUS_NOT_STARTED);
        note.setPrivacy(1);
        note.setCoverPhoto("");
        note.setCoverThumb("");
        note.setCreateTime(LocalDateTime.now());
        note.setUpdateTime(LocalDateTime.now());
        memoryNoteMapper.insert(note);

        MemoryPhoto photo = memoryPhotoService.save(file, userId, note.getId(), 0);
        String accessUrl = memoryPhotoService.accessUrl(photo);

        MemoryNote coverUpdate = new MemoryNote();
        coverUpdate.setId(note.getId());
        coverUpdate.setCoverPhoto(accessUrl);
        coverUpdate.setCoverThumb(accessUrl);
        memoryNoteMapper.updateById(coverUpdate);
        note.setCoverPhoto(accessUrl);
        note.setCoverThumb(accessUrl);

        // AI 识别：用 data URL，照片不必公网可达
        try {
            String title = imageRecognitionService.recognizeTitle(memoryPhotoService.dataUrl(photo));
            MemoryNote update = new MemoryNote();
            update.setId(note.getId());
            update.setAutoTitle(title);
            memoryNoteMapper.updateById(update);
            note.setAutoTitle(title);

            try {
                String emotion = memoryEmotionService.extractEmotions(title);
                if (StringUtils.isNotBlank(emotion)) {
                    memoryEmotionService.mergeIntoNote(note.getId(), emotion);
                }
            } catch (Exception ex) {
                log.warn("照片情绪提取失败: {}", ex.getMessage());
            }
        } catch (Exception e) {
            log.warn("AI 标题识别失败，使用默认标题: {}", e.getMessage());
            MemoryNote update = new MemoryNote();
            update.setId(note.getId());
            update.setAutoTitle("我的记忆");
            memoryNoteMapper.updateById(update);
            note.setAutoTitle("我的记忆");
        }

        memoryStatService.ensureStat(userId);
        return note;
    }

    /**
     * 纯文本碎念：kind=2，一次输入即「已成念」，默认为私密。
     */
    @Transactional(rollbackFor = Exception.class)
    public MemoryNote createFragment(Long userId, MemoryFragmentDto dto) {
        String content = StringUtils.trimToEmpty(dto.getContent());
        if (content.isEmpty()) {
            throw new BusinessException(400, "碎念内容不能为空");
        }
        int privacy = dto.getPrivacy() == null ? 1 : dto.getPrivacy();
        if (privacy != 0 && privacy != 1) {
            throw new BusinessException(400, "碎念只支持 0(公开) 或 1(私密)");
        }
        String title = StringUtils.defaultIfBlank(dto.getTitle(), defaultTitle(content));
        String emotions = dto.getEmotions() == null ? null : dto.getEmotions().stream()
                .map(StringUtils::trimToNull)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .limit(12)
                .collect(Collectors.joining(","));

        LocalDateTime now = LocalDateTime.now();
        MemoryNote note = new MemoryNote();
        note.setUserId(userId);
        note.setKind(MemoryNote.KIND_FRAGMENT);
        note.setStatus(MemoryNote.STATUS_CONDENSED);
        note.setPrivacy(privacy);
        note.setAutoTitle(title.length() > 128 ? title.substring(0, 128) : title);
        note.setFinalDiary(content);
        note.setEmotions(StringUtils.defaultIfBlank(emotions, ""));
        note.setFinishTime(now);
        note.setCreateTime(now);
        note.setUpdateTime(now);
        memoryNoteMapper.insert(note);

        try {
            String primaryEmotion = StringUtils.isBlank(emotions) ? null : emotions.split(",")[0];
            memoryVectorService.storeEmbedding(userId, note.getId(), note.getAutoTitle(),
                    note.getAutoTitle() + "\n" + content, privacy, primaryEmotion);
        } catch (Exception e) {
            log.warn("碎念向量入库失败: memoryId={}, err={}", note.getId(), e.getMessage());
        }

        memoryStatService.ensureStat(userId);
        // D12：直接设为公开也立即投影
        projectionService.syncFromNote(note);
        return note;
    }

    private String defaultTitle(String content) {
        String firstLine = content.replace("\r\n", "\n").split("\n")[0].trim();
        return firstLine.length() <= 16 ? firstLine : firstLine.substring(0, 16);
    }
}
