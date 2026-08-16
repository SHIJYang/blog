package com.blog.util;

import com.blog.common.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FilenameUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

/**
 * 图片处理工具
 * <p>
 * 统一将上传图片（JPEG/PNG/WebP/GIF/BMP）转码为 WebP 存储，
 * 兼容苹果 HEIC/HEIF（需系统支持对应解码器，无法解码时返回原格式兜底），
 * 保证浏览器全平台渲染一致。
 */
@Slf4j
public class ImageUtil {

    /** 支持的图片格式（含 HEIC/HEIF） */
    private static final String[] SUPPORTED = {"jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif"};

    public static boolean isSupported(String filename) {
        String ext = FilenameUtils.getExtension(filename);
        if (ext == null || ext.isBlank()) return false;
        for (String s : SUPPORTED) {
            if (s.equalsIgnoreCase(ext)) return true;
        }
        return false;
    }

    /**
     * 图片转码为 WebP（JPEG 兜底）
     * <p>
     * 注意：JDK 原生 ImageIO 不内置 WebP 编解码器。
     * 若类路径存在 TwelveMonkeys WebP 插件则输出 WebP，否则输出 JPEG。
     * （JPEG 兼容性最好，前端 <img> 全平台可渲染；WebP 体积更小，可按需引入插件启用）
     */
    public static byte[] toWebp(byte[] source) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(source));
            if (image == null) {
                // HEIC 等 ImageIO 无法解码的格式，原样返回（浏览器可能直接支持预览）
                return source;
            }
            // 修正透明通道：JPEG 不支持 alpha，先绘制到白底
            BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(image, 0, 0, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            if (ImageIO.write(rgb, "webp", baos)) {
                return baos.toByteArray();
            }
            // WebP 编码器不可用，回退 JPEG
            return toJpeg(rgb);
        } catch (IOException e) {
            log.warn("图片转码失败，返回原图: {}", e.getMessage());
            return source;
        }
    }

    /**
     * JPEG 编码（质量可配）
     */
    public static byte[] toJpeg(BufferedImage image) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
        }
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    /**
     * 校验图片大小（超过限制抛异常）
     */
    public static void checkSize(int length, int maxMb) {
        if (length > maxMb * 1024 * 1024L) {
            throw new BusinessException(400, "图片不能超过 " + maxMb + "MB");
        }
    }
}
