package com.blog.service.ai;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 向量工具类（纯本地实现）
 * 提供归一化、余弦相似度、JSON 序列化/反序列化
 */
@Service
@RequiredArgsConstructor
public class VectorUtil {

    /**
     * 向量归一化（L2）
     */
    public static float[] normalize(float[] vec) {
        double sum = 0;
        for (float v : vec) sum += (double) v * v;
        double norm = Math.sqrt(sum);
        if (norm == 0) return vec;
        float[] out = new float[vec.length];
        for (int i = 0; i < vec.length; i++) out[i] = (float) (vec[i] / norm);
        return out;
    }

    /**
     * 余弦相似度（向量均已归一化时即为点积）
     */
    public static double cosine(float[] a, float[] b) {
        if (a == null || b == null || a.length != b.length || a.length == 0) return 0;
        double dot = 0;
        for (int i = 0; i < a.length; i++) dot += (double) a[i] * b[i];
        return dot;
    }

    /**
     * 向量 → JSON
     */
    public static String toJson(float[] vec) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vec.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vec[i]);
        }
        return sb.append("]").toString();
    }

    /**
     * JSON → 向量
     */
    public static float[] fromJson(String json) {
        if (json == null || json.isBlank()) return new float[0];
        String inner = json.trim();
        if (inner.startsWith("[")) inner = inner.substring(1);
        if (inner.endsWith("]")) inner = inner.substring(0, inner.length() - 1);
        if (inner.isBlank()) return new float[0];
        String[] parts = inner.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i].trim());
        }
        return out;
    }
}
