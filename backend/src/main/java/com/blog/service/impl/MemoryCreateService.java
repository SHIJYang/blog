package com.blog.service.impl;

import com.blog.common.BusinessException;
import com.blog.entity.MemoryNote;
import com.blog.mapper.MemoryNoteMapper;
import com.blog.service.PicuiClient;
import com.blog.service.ai.ImageRecognitionService;
import com.blog.util.ImageUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 记忆创建服务
 * 上传照片 → 校验/转码存储 → AI 识别生成标题 → 初始化记忆记录
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MemoryCreateService {

    private final MemoryNoteMapper memoryNoteMapper;
    private final ImageRecognitionService imageRecognitionService;
    private final MemoryStatService memoryStatService;
    private final PicuiClient picuiClient;
    private final MemoryEmotionService memoryEmotionService;

    @Value("${memory.upload-dir:uploads/memory}")
    private String uploadDir;

    @Value("${memory.access-prefix:/uploads/memory}")
    private String accessPrefix;

    /**
     * 创建记忆
     *
     * @return 新记忆记录（含 id）
     */
    public MemoryNote create(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请上传照片");
        }
        String originalName = StringUtils.defaultIfBlank(file.getOriginalFilename(), "photo.jpg");
        if (!ImageUtil.isSupported(originalName)) {
            throw new BusinessException(400, "不支持的图片格式，支持 JPEG/PNG/WebP/HEIC 等");
        }
        ImageUtil.checkSize((int) file.getSize(), 10);

        // 1. 存储图片（统一转码）
        StorageResult storage = saveImage(file);

        // 2. 创建记忆记录（默认私密：拾光碎念 以隐私为默认）
        MemoryNote note = new MemoryNote();
        note.setUserId(userId);
        note.setCoverPhoto(storage.url);
        note.setCoverThumb(storage.thumbnailUrl);
        note.setStatus(MemoryNote.STATUS_NOT_STARTED);
        note.setPrivacy(1); // 0-公开 1-私密 2-封存，默认私密
        note.setCreateTime(LocalDateTime.now());
        note.setUpdateTime(LocalDateTime.now());
        memoryNoteMapper.insert(note);

        // 3. AI 识别生成标题（失败不阻断创建）
        try {
            String title = imageRecognitionService.recognizeTitle(storage.url);
            MemoryNote update = new MemoryNote();
            update.setId(note.getId());
            update.setAutoTitle(title);
            memoryNoteMapper.updateById(update);
            note.setAutoTitle(title);

            // 照片情绪锚点：基于 AI 标题提取初始情绪（丰富「只记忆内容 → 也记忆情绪」）
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

        // 4. 统计兜底
        memoryStatService.ensureStat(userId);

        return note;
    }

    /** 图片存储结果（原图 URL + 缩略图 URL） */
    public static class StorageResult {
        public String url;
        public String thumbnailUrl;
    }

    /**
     * 保存图片：优先上传 PicUI 图床（返回公网 URL + 缩略图），失败回落本地磁盘
     */
    public StorageResult saveImage(MultipartFile file) {
        try {
            String ext = FilenameUtils.getExtension(file.getOriginalFilename());
            String safeExt = (ext != null && !ext.isBlank()) ? ext.toLowerCase() : "jpg";
            String fileName = UUID.randomUUID().toString().replace("-", "") + "." + safeExt;

            byte[] processed = ImageUtil.toWebp(file.getBytes());
            String storedExt = "webp";
            if (processed == file.getBytes() && !"webp".equalsIgnoreCase(safeExt)) {
                storedExt = safeExt;
            }
            byte[] uploadBytes = processed;
            String uploadName = fileName.substring(0, fileName.lastIndexOf('.')) + "." + storedExt;

            // 优先 PicUI 图床
            try {
                PicuiClient.UploadResult up = picuiClient.upload(uploadName, uploadBytes, false);
                StorageResult sr = new StorageResult();
                sr.url = up.getUrl();
                sr.thumbnailUrl = up.getThumbnailUrl() != null ? up.getThumbnailUrl() : up.getUrl();
                return sr;
            } catch (Exception e) {
                log.warn("PicUI 上传失败，回落本地存储: {}", e.getMessage());
            }

            // 回落：本地磁盘存储
            String relativeDir = Paths.get(uploadDir).resolve(String.valueOf(System.currentTimeMillis() / 1000L / 86400L)).toString();
            String localFilename = UUID.randomUUID().toString().replace("-", "") + "." + storedExt;
            Path dir = Paths.get(relativeDir);
            Files.createDirectories(dir);
            Path target = dir.resolve(localFilename);
            Files.write(target, uploadBytes);

            StorageResult sr = new StorageResult();
            sr.url = accessPrefix + "/" + Paths.get(relativeDir).getFileName() + "/" + localFilename;
            sr.thumbnailUrl = sr.url; // 本地无缩略图，用原图兜底
            return sr;
        } catch (IOException e) {
            log.error("图片存储失败: {}", e.getMessage());
            throw new BusinessException(500, "图片保存失败");
        }
    }
}
