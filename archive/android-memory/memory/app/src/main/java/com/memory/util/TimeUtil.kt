package com.memory.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TimeUtil {
    private val inputFmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    fun format(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return try {
            val d = inputFmt.parse(input) ?: return input
            val now = System.currentTimeMillis()
            val diff = now - d.time
            when {
                diff < 60 * 1000 -> "刚刚"
                diff < 60 * 60 * 1000 -> "${diff / 60000} 分钟前"
                diff < 24 * 60 * 60 * 1000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(d)
                diff < 7 * 24 * 60 * 60 * 1000 -> SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(d)
                else -> SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(d)
            }
        } catch (e: Exception) {
            input
        }
    }

    fun formatFull(input: String?): String {
        if (input.isNullOrBlank()) return ""
        return try {
            val d = inputFmt.parse(input) ?: return input
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(d)
        } catch (e: Exception) {
            input
        }
    }
}
