package com.blog.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 日记文体格式化工具
 * 将 AI 生成的日记正文整理为「念念」风格：
 * 自动标注日期、第一人称治愈系碎碎念
 */
public class DiaryFormatter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy年M月d日");

    /**
     * 格式化日记正文（在 AI 生成内容前补充日期抬头）
     */
    public static String format(String aiText, String title) {
        StringBuilder sb = new StringBuilder();
        sb.append(LocalDate.now().format(DATE)).append("\n\n");
        if (title != null && !title.isBlank()) {
            sb.append("「").append(title).append("」").append("\n\n");
        }
        sb.append(aiText == null ? "" : aiText.trim());
        return sb.toString();
    }

    /**
     * 生成日记摘要（列表展示用）
     */
    public static String preview(String diary, int maxLen) {
        if (diary == null || diary.isBlank()) return "";
        String plain = diary.replaceAll("#+\\s*", "")
                .replaceAll("\\*\\*", "")
                .replaceAll("[\\r\\n]+", " ")
                .trim();
        return plain.length() <= maxLen ? plain : plain.substring(0, maxLen) + "…";
    }
}
