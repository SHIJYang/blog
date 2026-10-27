package com.memory.ui.main

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.LinearLayout
import androidx.dynamicanimation.animation.DynamicAnimation
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import kotlin.math.abs


class DockView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    companion object {
        /** 默认基础尺寸（dp） */
        const val DEFAULT_BASE_DP = 64f
        /** 默认放大尺寸（dp） */
        const val DEFAULT_MAGNIFY_DP = 92f
        /** 影响距离（dp）：多近开始放大 */
        const val DEFAULT_DISTANCE_DP = 220f
    }

    private val density = resources.displayMetrics.density

    /** 基础尺寸（px） */
    var baseItemSize: Float = DEFAULT_BASE_DP * density

    /** 放大后尺寸（px） */
    var magnificationSize: Float = DEFAULT_MAGNIFY_DP * density

    /** 影响距离（px） */
    var distance: Float = DEFAULT_DISTANCE_DP * density

    // 弹簧参数（对应 Vue spring: { stiffness:150, damping:12 }）
    private val springStiffness = 150f
    private val springDamping = 12f

    private var isTouching = false
    private var touchX = 0f
    private var touchY = 0f

    // 每个子 View 的 scale 弹簧
    private val scaleXSprings = HashMap<View, SpringAnimation>()
    private val scaleYSprings = HashMap<View, SpringAnimation>()
    private val targetScales = HashMap<View, Float>()

    init {
        // 横向 Dock（macOS 风格）
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        // 默认无面板背景（纯按钮）；如需面板可调 setBackgroundResource
    }

    private fun springFor(view: View, property: DynamicAnimation.ViewProperty): SpringAnimation {
        val map = if (property == DynamicAnimation.SCALE_X) scaleXSprings else scaleYSprings
        return map.getOrPut(view) {
            SpringAnimation(view, property).apply {
                setSpring(
                    SpringForce(1f).apply {
                        stiffness = springStiffness
                        dampingRatio = springDamping
                        // SpringForce 无 mass 设置（默认 1），Vue 的 mass:0.1 用 stiffness/damping 近似
                    }
                )
            }
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        // 拦截所有触摸，自己处理放大 + 点击转发
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                isTouching = true
                touchX = event.x
                touchY = event.y
                updateMagnification()
            }
            MotionEvent.ACTION_UP -> {
                // 先复位动画，再转发点击
                resetAll()
                isTouching = false
                forwardClick(event.x, event.y)
            }
            MotionEvent.ACTION_CANCEL -> {
                resetAll()
                isTouching = false
            }
        }
        return true
    }

    /** 计算每个按钮的放大系数（对应 Vue 的 targetSize 变换，横向用 X 距离） */
    private fun updateMagnification() {
        if (!isTouching) return
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            val childCenterX = child.left + child.width / 2f
            val d = abs(touchX - childCenterX)
            // Vue: clamped = max(-distance, min(distance, dist)); t = 1 - |clamped|/distance
            val t = (1f - (d / distance).coerceIn(0f, 1f))
            // 完全靠近 → magnificationSize；远离 → baseItemSize
            val scale = 1f + (magnificationSize / baseItemSize - 1f) * t
            animateScale(child, scale)
        }
    }

    private fun animateScale(view: View, target: Float) {
        val current = targetScales[view] ?: 1f
        if (abs(current - target) < 0.01f) return
        targetScales[view] = target
        springFor(view, DynamicAnimation.SCALE_X).animateToFinalPosition(target)
        springFor(view, DynamicAnimation.SCALE_Y).animateToFinalPosition(target)
    }

    private fun resetAll() {
        for (i in 0 until childCount) {
            animateScale(getChildAt(i), 1f)
        }
    }

    /** 转发点击：找到触摸位置命中的子 View，执行其 click */
    private fun forwardClick(x: Float, y: Float) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child.visibility != View.VISIBLE) continue
            if (x >= child.left && x <= child.right && y >= child.top && y <= child.bottom) {
                child.performClick()
                return
            }
        }
    }
}
