package com.memory.data

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 后端接口封装（同步实现，配合线程池调用）
 */
object Api {
    private const val TAG = "MemoryApi"
    private const val BASE = "http://118.190.133.231:32658/api"

    // 会话缓存
    const val PREF_NAME = "memory_pref"
    const val KEY_TOKEN = "token"
    const val KEY_ROLE = "role"
    const val KEY_USERNAME = "username"
    const val KEY_CAPTCHA_FREE_UNTIL = "captcha_free_until"

    val gson = Gson()

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    // ==================== 基础 ====================

    class ApiException(val code: Int, message: String) : IOException(message)

    private fun tokenOf(ctx: Context): String =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_TOKEN, "") ?: ""

    fun saveSession(ctx: Context, token: String, role: String, username: String, remember: Boolean = true) {
        val edit = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
        if (remember) {
            // 记住我：持久保存 token，下次启动免登录（配合后端长有效期 token）
            edit.putString(KEY_TOKEN, token)
                .putString(KEY_ROLE, role)
                .putString(KEY_USERNAME, username)
        } else {
            // 不记住：不写持久存储，仅本次进程内存态
            edit.remove(KEY_TOKEN).remove(KEY_ROLE).remove(KEY_USERNAME)
        }
        edit.apply()
    }

    fun clearSession(ctx: Context) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_TOKEN).remove(KEY_ROLE).remove(KEY_USERNAME).apply()
    }

    fun isLoggedIn(ctx: Context): Boolean =
        tokenOf(ctx).isNotBlank()

    /** 当前登录用户名 */
    fun currentUsername(ctx: Context): String =
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).getString(KEY_USERNAME, "") ?: ""

    /** 解析后端统一 Result 包装，返回 data 字段的 JsonObject */
    private fun parseResult(body: String): JsonObject {
        val obj = gson.fromJson(body, JsonObject::class.java)
        val code = obj.get("code")?.asInt ?: -1
        if (code != 200) {
            val msg = obj.get("message")?.asString ?: "操作失败"
            throw ApiException(code, msg)
        }
        return obj.getAsJsonObject("data")
    }

    private fun <T> parseData(body: String, type: TypeToken<T>): T {
        val obj = gson.fromJson(body, JsonObject::class.java)
        val code = obj.get("code")?.asInt ?: -1
        if (code != 200) {
            throw ApiException(code, obj.get("message")?.asString ?: "操作失败")
        }
        val data = obj.get("data")
        return if (data == null || data.isJsonNull) {
            gson.fromJson("null", type.type)
        } else {
            gson.fromJson(data, type.type)
        }
    }

    private fun reqBuilder(ctx: Context, path: String, method: String = "GET", body: RequestBody? = null): Request.Builder {
        val b = Request.Builder()
            .url(BASE + path)
            .header("Authorization", "Bearer ${tokenOf(ctx)}")
        when (method) {
            "GET" -> b.get()
            "POST" -> b.post(body ?: "{}".toRequestBody(JSON))
            "DELETE" -> b.delete(body)
        }
        return b
    }

    private val JSON = "application/json; charset=utf-8".toMediaType()

    /** 图片 URL 归一化：相对路径补全后端域名 */
    fun resolveUrl(url: String?): String {
        if (url.isNullOrBlank()) return ""
        if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("data:")) return url
        return BASE.replace("/api", "") + url
    }

    private fun exec(req: Request): String {
        client.newCall(req).execute().use { resp ->
            val body = resp.body?.string() ?: ""
            if (!resp.isSuccessful && resp.code == 401) {
                throw ApiException(401, "登录已失效，请重新登录")
            }
            return body
        }
    }

    // ==================== 认证 ====================

    data class LoginResult(val token: String, val role: String)

    fun login(username: String, password: String): LoginResult {
        val json = gson.toJson(mapOf("username" to username, "password" to password))
        val req = Request.Builder().url(BASE + "/auth/login")
            .post(json.toRequestBody(JSON)).build()
        val body = exec(req)
        val obj = gson.fromJson(body, JsonObject::class.java)
        val code = obj.get("code")?.asInt ?: -1
        if (code != 200) throw ApiException(code, obj.get("message")?.asString ?: "登录失败")
        val data = obj.getAsJsonObject("data")
        return LoginResult(
            data.get("token")?.asString ?: "",
            data.get("role")?.asString ?: ""
        )
    }

    // ==================== 记忆 ====================

    data class MemoryItem(
        val id: Long,
        val userId: Long? = null,
        val username: String? = null,
        val coverPhoto: String? = null,
        val coverThumb: String? = null,
        val autoTitle: String? = null,
        val diaryPreview: String? = null,
        val status: Int? = null,
        val privacy: Int? = null,
        val emotions: String? = null,
        val messageCount: Long? = null,
        val createTime: String? = null,
        val finishTime: String? = null
    )

    data class PageResult<T>(val records: List<T>, val total: Long, val current: Int, val size: Int)

    fun listMemories(ctx: Context, current: Int = 1, size: Int = 50, status: Int? = null, order: String = "create_desc"): PageResult<MemoryItem> {
        val sb = StringBuilder("/memory/list?current=$current&size=$size&order=$order")
        if (status != null) sb.append("&status=$status")
        val req = reqBuilder(ctx, sb.toString()).build()
        return parseData(exec(req), object : TypeToken<PageResult<MemoryItem>>() {})
    }

    fun searchMemories(ctx: Context, keyword: String, current: Int = 1, size: Int = 50): PageResult<MemoryItem> {
        val req = reqBuilder(ctx, "/memory/search?keyword=${java.net.URLEncoder.encode(keyword, "UTF-8")}&current=$current&size=$size").build()
        return parseData(exec(req), object : TypeToken<PageResult<MemoryItem>>() {})
    }

    fun memoryDetail(ctx: Context, memoryId: Long): JsonObject =
        parseResult(exec(reqBuilder(ctx, "/memory/detail?memoryId=$memoryId").build()))

    fun memoryStat(ctx: Context): JsonObject =
        parseResult(exec(reqBuilder(ctx, "/memory/stat").build()))

    /** 上传照片创建记忆，返回完整 note（JsonObject） */
    fun createMemory(ctx: Context, file: File, mime: String = "image/*"): JsonObject {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType()))
            .build()
        val req = reqBuilder(ctx, "/memory/create", "POST", body).build()
        return parseResult(exec(req))
    }

    /** 对话中追加图片 */
    fun chatImage(ctx: Context, memoryId: Long, file: File, mime: String = "image/*"): JsonObject {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("file", file.name, file.asRequestBody(mime.toMediaType()))
            .build()
        val req = reqBuilder(ctx, "/memory/chat/img?memoryId=$memoryId", "POST", body).build()
        return parseResult(exec(req))
    }

    data class ChatReply(val reply: String, val emotion: String?, val status: Int?, val recalled: Boolean?, val relatedMemories: List<RecallVo>?)

    data class RecallVo(val memoryId: Long, val title: String?, val snippet: String?, val emotion: String?, val score: Double?)

    /** 发送对话消息（验证码窗口内可空） */
    fun chatSend(ctx: Context, memoryId: Long, content: String, captchaCode: String?, captchaKey: String?): ChatReply {
        val map = LinkedHashMap<String, Any?>()
        map["memoryId"] = memoryId
        map["content"] = content
        map["captchaCode"] = captchaCode ?: ""
        map["captchaKey"] = captchaKey ?: ""
        val req = reqBuilder(ctx, "/memory/chat/send", "POST", gson.toJson(map).toRequestBody(JSON)).build()
        val body = exec(req)
        val obj = gson.fromJson(body, JsonObject::class.java)
        val code = obj.get("code")?.asInt ?: -1
        if (code != 200) throw ApiException(code, obj.get("message")?.asString ?: "发送失败")
        val data = obj.getAsJsonObject("data")
        val reply = data.get("reply")?.asString ?: ""
        val emotion = data.get("emotion")?.takeIf { !it.isJsonNull }?.asString
        val status = data.get("status")?.takeIf { !it.isJsonNull }?.asInt
        val recalled = data.get("recalled")?.takeIf { !it.isJsonNull }?.asBoolean
        val related = try {
            gson.fromJson(data.get("relatedMemories"), object : TypeToken<List<RecallVo>>() {}.type)
        } catch (e: Exception) { null }
        return ChatReply(reply, emotion, status, recalled, related)
    }

    data class Captcha(val key: String, val image: String)

    fun captcha(ctx: Context): Captcha {
        val obj = parseResult(exec(reqBuilder(ctx, "/memory/captcha").build()))
        return Captcha(obj.get("key")?.asString ?: "", obj.get("image")?.asString ?: "")
    }

    fun opening(ctx: Context, memoryId: Long): String {
        val obj = parseResult(exec(reqBuilder(ctx, "/memory/ai/opening?memoryId=$memoryId").build()))
        return obj.get("opening")?.asString ?: ""
    }

    /** 凝聚记忆生成日记 */
    fun condense(ctx: Context, memoryId: Long, customTitle: String? = null): JsonObject {
        val map = mutableMapOf<String, Any>("memoryId" to memoryId)
        if (!customTitle.isNullOrBlank()) map["customTitle"] = customTitle
        val req = reqBuilder(ctx, "/memory/condense", "POST", gson.toJson(map).toRequestBody(JSON)).build()
        return parseResult(exec(req))
    }

    fun publish(ctx: Context, memoryId: Long): Long {
        val req = reqBuilder(ctx, "/memory/publish", "POST", gson.toJson(mapOf("memoryId" to memoryId)).toRequestBody(JSON)).build()
        val obj = gson.fromJson(exec(req), JsonObject::class.java)
        val code = obj.get("code")?.asInt ?: -1
        if (code != 200) throw ApiException(code, obj.get("message")?.asString ?: "发布失败")
        return obj.get("data")?.takeIf { !it.isJsonNull }?.asLong ?: 0L
    }

    fun publishStatus(ctx: Context, memoryId: Long): Long? {
        val req = reqBuilder(ctx, "/memory/publish-status?memoryId=$memoryId").build()
        val obj = gson.fromJson(exec(req), JsonObject::class.java)
        val code = obj.get("code")?.asInt ?: -1
        if (code != 200) throw ApiException(code, obj.get("message")?.asString ?: "查询失败")
        return obj.get("data")?.takeIf { !it.isJsonNull }?.asLong
    }

    fun abandon(ctx: Context, memoryId: Long) {
        exec(reqBuilder(ctx, "/memory/abandon", "POST", gson.toJson(mapOf("memoryId" to memoryId)).toRequestBody(JSON)).build())
    }

    fun setPrivacy(ctx: Context, memoryId: Long, privacy: Int) {
        exec(reqBuilder(ctx, "/memory/privacy", "POST", gson.toJson(mapOf("memoryId" to memoryId, "privacy" to privacy)).toRequestBody(JSON)).build())
    }

    fun updateMemory(ctx: Context, memoryId: Long, autoTitle: String?, emotions: List<String>?, finalDiary: String?) {
        val map = LinkedHashMap<String, Any?>()
        map["memoryId"] = memoryId
        if (autoTitle != null) map["autoTitle"] = autoTitle
        if (emotions != null) map["emotions"] = emotions
        if (finalDiary != null) map["finalDiary"] = finalDiary
        exec(reqBuilder(ctx, "/memory/update", "POST", gson.toJson(map).toRequestBody(JSON)).build())
    }

    data class Insight(val id: Long, val content: String?, val emotion: String?, val source: Int?)

    fun insights(ctx: Context, memoryId: Long): List<Insight> {
        val req = reqBuilder(ctx, "/memory/insights?memoryId=$memoryId").build()
        return parseData(exec(req), object : TypeToken<List<Insight>>() {})
    }

    fun addInsight(ctx: Context, memoryId: Long, content: String) {
        exec(reqBuilder(ctx, "/memory/insight/add", "POST", gson.toJson(mapOf("memoryId" to memoryId, "content" to content)).toRequestBody(JSON)).build())
    }

    fun deleteInsight(ctx: Context, insightId: Long) {
        exec(reqBuilder(ctx, "/memory/insight/delete", "POST", gson.toJson(mapOf("insightId" to insightId)).toRequestBody(JSON)).build())
    }

    // ==================== 验证码免验证窗口 ====================

    fun isCaptchaFree(ctx: Context): Boolean {
        val until = ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getLong(KEY_CAPTCHA_FREE_UNTIL, 0L)
        return System.currentTimeMillis() < until
    }

    fun markCaptchaFree(ctx: Context) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .putLong(KEY_CAPTCHA_FREE_UNTIL, System.currentTimeMillis() + 10 * 60 * 1000L)
            .apply()
    }

    fun clearCaptchaFree(ctx: Context) {
        ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE).edit()
            .remove(KEY_CAPTCHA_FREE_UNTIL).apply()
    }
}
