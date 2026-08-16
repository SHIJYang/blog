package com.memory.ui.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import kotlin.random.Random

/**
 * 金色噪点纸张背景（移植自 CSS/SVG 纹理方案）
 *
 * 三层合成：
 * 1. 点阵层：深灰小点(2px) + 浅灰大点(10px)，间距 8rem(=128dp)，模拟 CSS radial-gradient
 * 2. 金色渐变层：上 rgba(194,167,87,0.241) → 下 rgba(162,125,55,0.255)
 * 3. 噪点层：预生成随机微粒位图，模拟 SVG feTurbulence 纹理（低透明度，纸张颗粒感）
 *
 * 用作所有页面根布局背景：主页 / 登录页 / 回忆长廊 / 对话页底层
 */
class PaperBgView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // ===== 点阵（预渲染到 Bitmap，每帧只 drawBitmap 一次）=====
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bigDotPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grid = 128f * resources.displayMetrics.density // 8rem = 128dp
    private var dotGridBitmap: Bitmap? = null

    // ===== 金色渐变 =====
    private val goldPaint = Paint()

    // ===== 噪点 =====
    private var noiseBitmap: Bitmap? = null
    private val noisePaint = Paint(Paint.FILTER_BITMAP_FLAG)

    init {
        // 深灰小点 #777777，2px（更淡）
        dotPaint.color = Color.rgb(0x77, 0x77, 0x77)
        dotPaint.alpha = 90

        // 浅灰大点 #eaeaea，10px（更淡）
        bigDotPaint.color = Color.rgb(0xEA, 0xEA, 0xEA)
        bigDotPaint.alpha = 70
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        // 金色渐变更浅：上 rgba(194,167,87,0.10) → 下 rgba(162,125,55,0.12)
        goldPaint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            Color.argb(26, 194, 167, 87),   // 0.10 * 255 ≈ 26
            Color.argb(31, 162, 125, 55),   // 0.12 * 255 ≈ 31
            Shader.TileMode.CLAMP
        )
        buildDotGrid(w, h)
        buildNoise(w, h)
    }

    /** 预渲染点阵为 Bitmap，避免每帧双重 while 循环 */
    private fun buildDotGrid(w: Int, h: Int) {
        dotGridBitmap?.recycle()
        if (w <= 0 || h <= 0) return
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val dpr = resources.displayMetrics.density
        var y = 0f
        while (y < h) {
            var x = 0f
            while (x < w) {
                c.drawCircle(x, y, 2f * dpr, dotPaint)
                c.drawCircle(x + grid / 2, y + grid / 2, 10f * dpr, bigDotPaint)
                x += grid
            }
            y += grid
        }
        dotGridBitmap = bmp
    }

    /** 预生成噪点位图：随机半透明微粒（更稀疏更淡） */
    private fun buildNoise(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val scale = 3 // 降采样更大，噪点更稀疏
        val bw = w / scale
        val bh = h / scale
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val p = Paint()
        val rnd = Random(5) // seed=5 对齐 CSS
        // 稀疏噪点：每像素约 5% 概率画 1px 灰点，透明度很低
        for (y in 0 until bh) {
            for (x in 0 until bw) {
                if (rnd.nextFloat() < 0.05f) {
                    val v = 140 + rnd.nextInt(80)   // 灰阶 140-220
                    p.color = Color.argb(14 + rnd.nextInt(14), v, v, v)  // alpha 14-27
                    canvas.drawPoint(x.toFloat(), y.toFloat(), p)
                }
            }
        }
        noiseBitmap = bmp
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // 1. 底色（更浅的暖白）
        canvas.drawColor(Color.rgb(0xFD, 0xFC, 0xF9))

        // 2. 点阵层（预渲染，直接 drawBitmap）
        dotGridBitmap?.let {
            canvas.drawBitmap(it, 0f, 0f, null)
        }

        // 3. 金色渐变层
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), goldPaint)

        // 4. 噪点层（铺满）
        noiseBitmap?.let { bmp ->
            canvas.drawBitmap(bmp, null, android.graphics.Rect(0, 0, width, height), noisePaint)
        }
    }
}
