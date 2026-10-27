package com.blog.common;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

public class JsoupUtils {

    /**
     * 清洗 HTML，防止 XSS 攻击
     * 允许富文本常用的安全 HTML 标签 (例如: p, br, strong, a, img, pre, code 等)
     */
    public static String cleanHtml(String content) {
        if (content == null) {
            return null;
        }
        // Safelist.relaxed() 允许安全格式化标签并添加自定义许可
        Safelist safelist = Safelist.relaxed()
                .addTags("pre", "code", "span")
                .addAttributes("span", "style")
                .addAttributes("code", "class");
        return Jsoup.clean(content, safelist);
    }

    /**
     * 完全过滤 HTML 标签，只保留纯文本（用于自动提取摘要）
     */
    public static String cleanToPlainText(String content) {
        if (content == null) {
            return null;
        }
        return Jsoup.clean(content, Safelist.none());
    }
}