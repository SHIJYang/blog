package com.memory.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.util.Log
import android.view.View
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 3D 照片粒子展示（Canvas 3D 投影，无黑底、天然透明、垫底装饰层）
 *
 * 用 Canvas 而非 OpenGL：
 * - 普通 View，透明背景可靠，聊天内容浮在粒子之上

 * - 3D 旋转自动慢旋转
 *
 * 【核心算法】
 * 1. 图片解码限 800px；2. 像素提取抠像（alpha<128 / 亮度>240 剔除）；
 * 3. 坐标映射 -1~1 保持宽高比；4. 亮度映射 Z（-1~1）；
 * 5. 粒子 = xyz + rgb；6. 每帧四元数旋转 → 透视投影 → 深度排序绘制
 */
class ParticleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private data class Particle(
        var x: Float, var y: Float, var z: Float,
        var r: Float, var g: Float, var b: Float
    )

    private val particles = mutableListOf<Particle>()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    private var running = false
    private var lastTime = 0L
    private var rotationAngle = 0f
    private val io = Executors.newSingleThreadExecutor()

    init {
        // 透明背景（默认），粒子浮在纸纹上
    }

    /** 设置照片 URL：后台解码 → 像素提取 → 粒子列表 */
    fun setImage(url: String) {
        Log.d("Particle3D", "setImage called url=$url")
        if (url.isBlank()) return
        io.execute {
            try {
                val bmp = loadBitmap(url)
                if (bmp == null) {
                    Log.e("Particle3D", "loadBitmap returned null")
                    return@execute
                }
                val extracted = extractParticles(bmp)
                Log.d("Particle3D", "particles=${extracted.size}")
                post {
                    particles.clear()
                    particles.addAll(extracted)
                    // 触发一次绘制
                    invalidate()
                }
            } catch (e: Exception) {
                Log.e("Particle3D", "setImage failed", e)
            }
        }
    }

    /** 加载并缩放图片（限最大边长 800px）。先查磁盘缓存，未命中下载后写盘 */
    private fun loadBitmap(url: String): Bitmap? {
        val decoded = if (url.startsWith("http")) {
            val cacheFile = cacheFileFor(url)
            val cached = if (cacheFile.exists()) {
                try { BitmapFactory.decodeFile(cacheFile.absolutePath) } catch (e: Exception) { null }
            } else null
            if (cached != null) {
                cached
            } else {
                try {
                    val conn = java.net.URL(url).openConnection()
                    conn.connectTimeout = 8000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("Referer", "http://118.190.133.231:32658")
                    val stream = conn.getInputStream()
                    val bmp = BitmapFactory.decodeStream(stream)
                    stream.close()
                    if (bmp != null) {
                        try {
                            cacheFile.parentFile?.mkdirs()
                            cacheFile.outputStream().use { out ->
                                bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
                            }
                        } catch (e: Exception) { /* 缓存写失败不影响 */ }
                    }
                    bmp
                } catch (e: Exception) {
                    null
                }
            }
        } else {
            BitmapFactory.decodeFile(url)
        } ?: return null

        val w = decoded.width
        val h = decoded.height
        val maxSide = 800
        return if (w <= maxSide && h <= maxSide) {
            decoded
        } else {
            val ratio = maxSide.toFloat() / maxOf(w, h)
            Bitmap.createScaledBitmap(decoded, (w * ratio).toInt(), (h * ratio).toInt(), true)
        }
    }

    private fun cacheFileFor(url: String): File {
        val dir = File(context.cacheDir, "sphere_cache")
        val md5 = java.security.MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$md5.jpg")
    }

    /** 像素提取：剔除透明/过亮像素，映射 3D 坐标 */
    private fun extractParticles(bmp: Bitmap): List<Particle> {
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)

        val aspect = w.toFloat() / h.toFloat()
        val scaleX = if (aspect >= 1f) aspect else 1f
        val scaleY = if (aspect >= 1f) 1f else 1f / aspect

        val totalPixels = w * h
        var step = 1
        // 提高密度：z 轴压扁后需要更多粒子填充平面，阈值放宽到 20 万
        if (totalPixels > 200_000) {
            step = ceil(totalPixels / 200_000f).toInt()
        }

        val result = mutableListOf<Particle>()
        for (py in 0 until h step step) {
            for (px in 0 until w step step) {
                val idx = py * w + px
                val color = pixels[idx]
                val a = (color ushr 24) and 0xFF
                val r = (color ushr 16) and 0xFF
                val g = (color ushr 8) and 0xFF
                val b = color and 0xFF
                val brightness = (r * 0.299f + g * 0.587f + b * 0.114f)
                if (a < 128 || brightness > 240f) continue

                val x = (px / (w - 1f) * 2f - 1f) * scaleX
                val y = (1f - py / (h - 1f) * 2f) * scaleY
                // 深度：亮度高 → 靠近相机（z 大）。范围 [-0.3, 0.3] 扁平化，
                // 粒子云更贴近平面，旋转时轮廓清晰（跨度太大会散开模糊）
                val z = (brightness / 255f - 0.5f) * 0.6f

                result.add(Particle(x, y, z, r / 255f, g / 255f, b / 255f))
            }
        }
        return result
    }

    private fun ceil(v: Float): Float = kotlin.math.ceil(v).toFloat()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        running = true
        lastTime = System.currentTimeMillis()
        post(animationRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        running = false
        removeCallbacks(animationRunnable)
        io.shutdownNow()
    }

    private val animationRunnable = object : Runnable {
        override fun run() {
            if (!running) return
            val now = System.currentTimeMillis()
            val dt = ((now - lastTime).coerceAtLeast(1L) / 1000f)
            lastTime = now
            // 自动慢旋转：Y 轴 360° 无限循环（12°/s，30s 一圈）
            rotationAngle += dt * 12f
            invalidate()
            postDelayed(this, 16L)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (particles.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        // 相机距离（透视）：粒子投影大小
        val cameraZ = 2.6f
        val baseRadius = minOf(w, h) * 0.34f

        val cosA = cos(rotationAngle * PI / 180f).toFloat()
        val sinA = sin(rotationAngle * PI / 180f).toFloat()

        // 计算所有粒子的投影 + 深度 + 颜色，排序绘制
        val projected = ArrayList<FloatArray>(particles.size)
        for (i in particles.indices) {
            val p = particles[i]
            // 绕 Y 轴旋转
            val rx = p.x * cosA - p.z * sinA
            val rz = p.x * sinA + p.z * cosA
            // 透视投影：z 越大越近
            val scale = cameraZ / (cameraZ - rz)
            if (scale <= 0f) continue // 在相机后面
            val sx = cx + rx * baseRadius * scale
            // Canvas y 向下，模型 y 向上 → 取反（正立）
            val sy = cy - p.y * baseRadius * scale
            // [sx, sy, rz, r, g, b]
            projected.add(floatArrayOf(sx, sy, rz, p.r, p.g, p.b))
        }
        // 深度排序：z 大（近）后画
        projected.sortBy { it[2] }

        for (arr in projected) {
            val sx = arr[0]
            val sy = arr[1]
            val rz = arr[2]
            val depth = ((rz + 1f) / 2f).coerceIn(0f, 1f) // 0 远 1 近
            val radius = (2f + depth * 4f)
            // 原始照片颜色 + 深度透明度
            paint.color = android.graphics.Color.rgb(
                (arr[3] * 255).toInt(),
                (arr[4] * 255).toInt(),
                (arr[5] * 255).toInt()
            )
            paint.alpha = (80 + depth * 150).toInt()
            canvas.drawCircle(sx, sy, radius, paint)
        }
        paint.alpha = 255
    }
}
