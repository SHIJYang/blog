package com.memory.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 无限球体菜单（Android 版，移植自 Vue Bits <InfiniteMenu />）
 *
 * 旋转引擎 1:1 复刻原版 ArcballControl（gl-matrix quat/vec3）：
 * - 指针坐标投影到球面（project：r=2 球面/双曲面，x 取反），
 *   a = normalize(project(mid))，b = normalize(project(prev))，
 *   quatFromVectors 绕 normalize(a×b) 轴转 acos(dot(a,b))*angleFactor 角，
 *   直接覆写 pointerRotation（angleFactor = 5，帧率无关放大）
 * - 每帧 orientation = (snapRotation * pointerRotation) * orientation（左乘）
 * - 松手后 pointerRotation 以 0.1*timeScale 强度 slerp 回单位四元数（减速滑行）
 * - 吸附：snapTargetDirection（当前最前顶点世界方向）→ snapDirection(0,0,-1)，
 *   每帧 0.2*distanceFactor 强度逼近，distanceFactor = max(0.1, 1 - sqrDist*10)
 * - _combinedQuat 以 0.8*timeScale slerp 低通滤波，提取旋转轴/速度
 *   （rotationAxis / rotationVelocity，速度再以 0.5 低通）
 * - 相机距离随速度变化：拖拽时 cameraZ 变大（球变小），松手回到 3
 * - findNearestVertexIndex：snapDirection 用 orientation 共轭转回模型空间找最近顶点
 * - isMoving = isPointerDown || |rotationVelocity| > 0.01
 *
 * 渲染用 Canvas 2D 透视投影（非 WebGL），图片预加载为 Bitmap 纹理。
 */
class SphereMenuView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    interface Listener {
        fun onItemClick(index: Int)

        /** 定格/静止时，正对屏幕的图片发生变化 */
        fun onActiveItemChange(index: Int) {}

        /** 滑动状态变化：true = 拖拽/惯性中，false = 已定格 */
        fun onMovingChange(moving: Boolean) {}
    }

    var listener: Listener? = null

    /** 图片 URL 列表 */
    private val imageUrls = mutableListOf<String>()

    /** 加载的位图 */
    private val bitmaps = mutableListOf<Bitmap?>()

    /** 球面上的单位向量（Icosahedron 细分顶点，对齐原版） */
    private val spherePoints = mutableListOf<FloatArray>() // [x, y, z]

    // ===== ArcballControl 状态（字段名对齐原版） =====

    /** 当前姿态：模型空间旋转（原版 orientation） */
    private var orientation = Quat.IDENTITY

    /** 指针旋转：拖拽时被 quatFromVectors 覆写，松手后 slerp 回单位（原版 pointerRotation） */
    private var pointerRotation = Quat.IDENTITY

    /** 平滑后的旋转速度（原版 rotationVelocity） */
    private var rotationVelocity = 0f

    /** 内部速度累积（低通滤波，原版 _rotationVelocity） */
    private var rawRotationVelocity = 0f

    /** 旋转轴（原版 rotationAxis） */
    private var rotationAxis = floatArrayOf(1f, 0f, 0f)

    /** 吸附基准方向：屏幕正中心 = 世界 +Z（相机在 +Z 看向原点，绘制时 z 越大越靠前） */
    private val snapDirection = floatArrayOf(0f, 0f, 1f)

    /** 世界空间吸附目标：每帧更新为当前最前顶点的方向（原版 snapTargetDirection） */
    private var snapTargetDirection: FloatArray? = null

    /** 低通滤波后的组合四元数，用于提取轴/速度（原版 _combinedQuat） */
    private var combinedQuat = Quat.IDENTITY

    /** 指针是否按下（原版 isPointerDown） */
    private var isPointerDown = false

    /** 当前指针位置（View 坐标，原版 pointerPos） */
    private var pointerPos = floatArrayOf(0f, 0f)

    /** 上一帧指针位置（原版 previousPointerPos） */
    private var previousPointerPos = floatArrayOf(0f, 0f)

    /** 移动量平方阈值（原版 EPSILON = 0.1） */
    private val EPSILON = 0.1f

    /** 相机距离（原版 camera.position[2]，默认 3），影响球的大小 */
    private var cameraZ = 3f

    /** 运动状态是否变化过（原版 movementActive） */
    private var movementActive = false

    /** 帧循环计时 */
    private var lastFrameTime = 0L

    /** 点击检测：按下位置 */
    private var downX = 0f
    private var downY = 0f

    /** 当前正对屏幕的 item 索引（上报去重） */
    private var activeIndex = -1

    /** 上一次上报的运动状态（去重） */
    private var lastMoving = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val exec = Executors.newSingleThreadExecutor()
    private var loadedCount = 0

    // 缩放（对应 scale prop）
    var scale: Float = 1.0f
        set(value) {
            field = value
            invalidate()
        }

    init {
        // 帧循环：模拟 requestAnimationFrame，计算 deltaTime 传给 update
        post(object : Runnable {
            override fun run() {
                val now = SystemClock.uptimeMillis()
                val dt = if (lastFrameTime == 0L) 16f else (now - lastFrameTime).toFloat()
                lastFrameTime = now
                update(dt)
                invalidate()
                postDelayed(this, 16L)
            }
        })
    }

    /** 设置图片列表并加载 */
    fun setImages(urls: List<String>) {
        imageUrls.clear()
        imageUrls.addAll(urls)
        bitmaps.clear()
        repeat(urls.size) { bitmaps.add(null) }
        loadedCount = 0

        // 球面分布：Icosahedron 细分顶点均匀铺满球面，点数随数据量自动提升
        // （≤42→42 点，≤162→162 点，否则 642 点），每张图都有独立位置
        spherePoints.clear()
        spherePoints.addAll(icosahedronPoints(subdivisionsFor(urls.size)))
        // 把最接近 +Z 的顶点精确转到屏幕正中，保证首帧正中心有图
        alignFrontToZ()

        // 异步加载图片（每张图只加载一次，绘制时按 discIndex % N 取）
        exec.execute {
            urls.forEachIndexed { index, url ->
                try {
                    val bmp = loadBitmap(url)
                    bitmaps[index] = bmp
                    loadedCount++
                    android.util.Log.d("SphereMenu", "loaded[$index] ${if (bmp != null) bmp.width else 0}x${if (bmp != null) bmp.height else 0} url=$url")
                } catch (e: Exception) {
                    android.util.Log.e("SphereMenu", "load fail[$index] $url", e)
                    bitmaps[index] = null
                }
            }
            android.util.Log.d("SphereMenu", "all loaded: $loadedCount/${urls.size}")
            // 全部加载完再统一触发一次重绘，避免逐张 postInvalidate 导致频繁重绘
            postInvalidate()
        }
    }

    /**
     * 生成均匀球面点：Icosahedron 顶点 + 边中点细分（subdivide），归一化到单位球。
     * 点数 = 10 * 4^subdivisions + 2：
     *   subdivide 1 → 42 点，subdivide 2 → 162 点，subdivide 3 → 642 点
     * 数据量超过当前点数时自动提高细分，保证每张图都有独立位置。
     */
    private fun icosahedronPoints(subdivisions: Int = 1): List<FloatArray> {
        val t = (1.0f + sqrt(5.0f)) / 2.0f
        val verts = mutableListOf(
            floatArrayOf(-1f, t, 0f), floatArrayOf(1f, t, 0f),
            floatArrayOf(-1f, -t, 0f), floatArrayOf(1f, -t, 0f),
            floatArrayOf(0f, -1f, t), floatArrayOf(0f, 1f, t),
            floatArrayOf(0f, -1f, -t), floatArrayOf(0f, 1f, -t),
            floatArrayOf(t, 0f, -1f), floatArrayOf(t, 0f, 1f),
            floatArrayOf(-t, 0f, -1f), floatArrayOf(-t, 0f, 1f)
        )
        val faces = mutableListOf(
            intArrayOf(0, 11, 5), intArrayOf(0, 5, 1), intArrayOf(0, 1, 7),
            intArrayOf(0, 7, 10), intArrayOf(0, 10, 11), intArrayOf(1, 5, 9),
            intArrayOf(5, 11, 4), intArrayOf(11, 10, 2), intArrayOf(10, 7, 6),
            intArrayOf(7, 1, 8), intArrayOf(3, 9, 4), intArrayOf(3, 4, 2),
            intArrayOf(3, 2, 6), intArrayOf(3, 6, 8), intArrayOf(3, 8, 9),
            intArrayOf(4, 9, 5), intArrayOf(2, 4, 11), intArrayOf(6, 2, 10),
            intArrayOf(8, 6, 7), intArrayOf(9, 8, 1)
        )

        // 逐级细分：每个三角形切成 4 个，每条边取中点（缓存避免重复）
        repeat(subdivisions) {
            val cache = HashMap<Long, Int>()
            fun mid(a: Int, b: Int): Int {
                val key = if (a < b) a * 100000L + b else b * 100000L + a
                cache[key]?.let { return it }
                val pa = verts[a]
                val pb = verts[b]
                verts.add(floatArrayOf((pa[0] + pb[0]) / 2f, (pa[1] + pb[1]) / 2f, (pa[2] + pb[2]) / 2f))
                val idx = verts.size - 1
                cache[key] = idx
                return idx
            }
            val newFaces = ArrayList<IntArray>(faces.size * 4)
            for (f in faces) {
                val mAB = mid(f[0], f[1])
                val mBC = mid(f[1], f[2])
                val mCA = mid(f[2], f[0])
                newFaces.add(intArrayOf(f[0], mAB, mCA))
                newFaces.add(intArrayOf(f[1], mBC, mAB))
                newFaces.add(intArrayOf(f[2], mCA, mBC))
                newFaces.add(intArrayOf(mAB, mBC, mCA))
            }
            faces.clear()
            faces.addAll(newFaces)
        }

        // 归一化到单位球面
        return verts.map { p ->
            val len = sqrt(p[0] * p[0] + p[1] * p[1] + p[2] * p[2])
            floatArrayOf(p[0] / len, p[1] / len, p[2] / len)
        }
    }

    /** 根据数据量选择细分次数：保证点数 ≥ 数据量（封顶 642） */
    private fun subdivisionsFor(count: Int): Int = when {
        count <= 42 -> 1
        count <= 162 -> 2
        else -> 3
    }

    /**
     * 把最接近 +Z（屏幕正前方）的顶点精确旋转到 +Z。
     * Icosahedron 顶点没有恰好落在 (0,0,1) 的，首帧 orientation=IDENTITY 时
     * 屏幕正中心是空洞（最近的顶点在 y≈±0.5 处）；对齐后首帧中心必有图。
     */
    private fun alignFrontToZ() {
        if (spherePoints.isEmpty()) return
        var best = spherePoints[0]
        var bestZ = best[2]
        for (p in spherePoints) {
            if (p[2] > bestZ) {
                bestZ = p[2]
                best = p
            }
        }
        if (bestZ > 0.9999f) return // 已有正前方顶点
        val q = quatFromVectors(best, floatArrayOf(0f, 0f, 1f), 1f)
        for (i in spherePoints.indices) {
            spherePoints[i] = q.rotate(spherePoints[i])
        }
    }

    private fun loadBitmap(url: String): Bitmap? {
        if (url.isBlank()) return null
        return try {
            if (url.startsWith("http")) {
                // 磁盘缓存：先查缓存文件，命中直接读，未命中下载后写盘
                val cacheFile = bitmapCacheFile(url)
                val cached = if (cacheFile.exists()) {
                    try { BitmapFactory.decodeFile(cacheFile.absolutePath) } catch (e: Exception) { null }
                } else null

                if (cached != null) {
                    // 缩放到 256px 内，控制内存
                    return cached.let { scaleDown(it, 256) }
                }

                val conn = java.net.URL(url).openConnection()
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.setRequestProperty("Referer", "http://118.190.133.231:32658")
                val stream = conn.getInputStream()
                val bmp = BitmapFactory.decodeStream(stream)
                stream.close()
                if (bmp != null) {
                    try {
                        cacheFile.parentFile?.mkdirs()
                        val out = cacheFile.outputStream()
                        out.use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
                    } catch (e: Exception) {
                        // 缓存写失败不影响显示
                    }
                }
                // 缩放到 256px 内，控制内存
                bmp?.let { scaleDown(it, 256) }
            } else {
                BitmapFactory.decodeFile(url)
            }
        } catch (e: Exception) {
            null
        }
    }

    /** 图片 URL → 磁盘缓存文件（cacheDir/sphere_cache/<md5>.jpg） */
    private fun bitmapCacheFile(url: String): File {
        val dir = File(context.cacheDir, "sphere_cache")
        val md5 = java.security.MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(dir, "$md5.jpg")
    }

    private fun scaleDown(bmp: Bitmap, maxSize: Int): Bitmap {
        val w = bmp.width
        val h = bmp.height
        if (w <= maxSize && h <= maxSize) return bmp
        val ratio = maxSize.toFloat() / max(w, h)
        return Bitmap.createScaledBitmap(bmp, (w * ratio).toInt(), (h * ratio).toInt(), true)
    }

    /**
     * 每帧更新：1:1 复刻原版 ArcballControl.update(deltaTime, targetFrameDuration=16)。
     */
    private fun update(deltaTime: Float) {
        // 球面点尚未就绪（setImages 未调用）时不更新，避免越界
        if (spherePoints.isEmpty()) return

        val TARGET_FRAME_DURATION = 16f
        val timeScale = deltaTime / TARGET_FRAME_DURATION + 0.00001f
        var angleFactor = timeScale
        var snapRot = Quat.IDENTITY

        if (isPointerDown) {
            val intensity = 0.3f * timeScale
            val angleAmplification = 5f / timeScale

            // midPointerPos = (cur - prev) * INTENSITY（只移动 30% 距离，平滑）
            val midDx = (pointerPos[0] - previousPointerPos[0]) * intensity
            val midDy = (pointerPos[1] - previousPointerPos[1]) * intensity

            if (midDx * midDx + midDy * midDy > EPSILON) {
                // mid = prev + (cur - prev) * INTENSITY
                val midX = previousPointerPos[0] + midDx
                val midY = previousPointerPos[1] + midDy

                // a = 新球面点，b = 旧球面点
                val p = project(midX, midY)
                val q = project(previousPointerPos[0], previousPointerPos[1])
                val a = normalize3(p)
                val b = normalize3(q)

                previousPointerPos = floatArrayOf(midX, midY)

                // angleFactor = timeScale * (5 / timeScale) = 5，帧率无关
                angleFactor *= angleAmplification

                // 覆写 pointerRotation（原版 quatFromVectors(a, b, this.pointerRotation, angleFactor)）
                pointerRotation = quatFromVectors(a, b, angleFactor)
            } else {
                // 几乎没动：指针旋转缓慢回弹
                pointerRotation = Quat.slerp(pointerRotation, Quat.IDENTITY, intensity)
            }
        } else {
            // 松手：指针旋转以 0.1*timeScale 强度回弹 → 减速滑行（原版惯性）
            val intensity = 0.1f * timeScale
            pointerRotation = Quat.slerp(pointerRotation, Quat.IDENTITY, intensity)

            // 吸附：把当前最前顶点方向（snapTargetDirection）转到 snapDirection(0,0,-1)
            snapTargetDirection?.let { target ->
                val snappingIntensity = 0.2f
                val a = target
                val b = snapDirection
                val sqrDist = squaredDistance(a, b)
                val distanceFactor = max(0.1f, 1f - sqrDist * 10f)
                angleFactor *= snappingIntensity * distanceFactor
                snapRot = quatFromVectors(a, b, angleFactor)
            }
        }

        // combined = snapRotation * pointerRotation；orientation = combined * orientation
        val combined = snapRot * pointerRotation
        orientation = (combined * orientation).normalized()

        // _combinedQuat 低通滤波（用于提取轴/速度）
        val raIntensity = 0.8f * timeScale
        combinedQuat = Quat.slerp(combinedQuat, combined, raIntensity).normalized()

        // 从 _combinedQuat 提取旋转轴/速度（对应原版 rad / s / rv 段）
        val rad = 2f * acos(combinedQuat.w.coerceIn(-1f, 1f)).toFloat()
        val s = sin(rad / 2f)
        var rv = 0f
        if (s > 0.000001f) {
            rv = rad / (2f * PI.toFloat())
            rotationAxis = floatArrayOf(combinedQuat.x / s, combinedQuat.y / s, combinedQuat.z / s)
        }

        // 速度低通滤波（原版 RV_INTENSITY = 0.5 * timeScale）
        val rvIntensity = 0.5f * timeScale
        rawRotationVelocity += (rv - rawRotationVelocity) * rvIntensity
        rotationVelocity = rawRotationVelocity / timeScale

        // 相机距离 + 活动项/吸附目标更新（对应原版 onControlUpdate）
        onControlUpdate(timeScale)
    }

    /** 对应原版 InfiniteGridMenu.onControlUpdate(deltaTime) */
    private fun onControlUpdate(timeScale: Float) {
        var damping = 5f / timeScale
        // 定格静止时相机稍拉近（原版 3 → 2.4），球体放大 ~7%，拖动时再按速度拉远
        var cameraTargetZ = 2.4f

        // isMoving = isPointerDown || |rotationVelocity| > 0.01
        val isMoving = isPointerDown || abs(rotationVelocity) > 0.01f
        if (isMoving != movementActive) {
            movementActive = isMoving
            notifyMoving(isMoving)
        }

        if (!isPointerDown) {
            // 松手：找最前顶点，上报活动项，设置吸附目标
            val nearest = findNearestVertexIndex()
            val itemIndex = nearest % max(1, imageUrls.size)
            if (itemIndex != activeIndex) {
                activeIndex = itemIndex
                listener?.onActiveItemChange(itemIndex)
            }
            // snapTargetDirection = normalize(orientation * vertexPos)
            val worldPos = getVertexWorldPosition(nearest)
            snapTargetDirection = normalize3(worldPos)
        } else {
            // 拖拽：相机随旋转速度拉远（原版 cameraTargetZ += rotationVelocity * 80 + 2.5）
            cameraTargetZ += rotationVelocity * 80f + 2.5f
            damping = 7f / timeScale
        }

        cameraZ += (cameraTargetZ - cameraZ) / damping
    }

    /**
     * 对应原版 ArcballControl.project(pos)：屏幕坐标 → 球面/双曲面投影点。
     * r=2；xySq <= rSq/2 时 z = sqrt(rSq - xySq)（球面），否则 z = rSq/sqrt(xySq)（双曲面）。
     * 归一化用 s = max(w,h) - 1；返回 (-x, -y, z) —— x、y 都取反（对齐 Canvas 坐标：屏幕 y 向下）
     */
    private fun project(px: Float, py: Float): FloatArray {
        val r = 2f
        val w = width.toFloat()
        val h = height.toFloat()
        val s = max(w, h) - 1f

        val x = (2f * px - w - 1f) / s
        val y = (2f * py - h - 1f) / s
        val xySq = x * x + y * y
        val rSq = r * r
        val z = if (xySq <= rSq / 2f) {
            sqrt(rSq - xySq)
        } else {
            rSq / sqrt(xySq)
        }
        return floatArrayOf(-x, -y, z) // x、y 取反（对齐 Canvas 屏幕坐标）
    }

    /**
     * 对应原版 ArcballControl.quatFromVectors(a, b, out, angleFactor)：
     * axis = normalize(a × b)，angle = acos(dot(a,b)) * angleFactor，
     * 返回绕 axis 转 angle 的四元数（覆写式，非累积）。
     */
    private fun quatFromVectors(a: FloatArray, b: FloatArray, angleFactor: Float): Quat {
        val axis = cross3(a, b)
        val axisLen = length3(axis)
        if (axisLen < 1e-6f) return Quat.IDENTITY // a、b 平行：无旋转
        val ax = axis[0] / axisLen
        val ay = axis[1] / axisLen
        val az = axis[2] / axisLen
        val d = dot3(a, b).coerceIn(-1f, 1f)
        val angle = acos(d) * angleFactor
        return Quat.fromAxisAngle(floatArrayOf(ax, ay, az), angle)
    }

    /**
     * 对应原版 InfiniteGridMenu.findNearestVertexIndex()：
     * 把 snapDirection(0,0,-1) 用 orientation 的共轭转回模型空间，找 dot 最大的顶点。
     */
    /**
     * 屏幕正中心的顶点索引：旋转后世界 z 最大（最靠近相机、绘制在最前）的点。
     * 原版用 snapDirection(0,0,-1) 的共轭找最近顶点，但相机在 +Z 看向原点，
     * 屏幕中心 = 世界 z 最大的顶点（与 onDraw 的 z 排序、smoothstepAlpha 一致）。
     */
    private fun findNearestVertexIndex(): Int {
        if (spherePoints.isEmpty()) return 0
        var best = 0
        var bestZ = -2f
        for (i in spherePoints.indices) {
            val p = orientation.rotate(spherePoints[i])
            if (p[2] > bestZ) {
                bestZ = p[2]
                best = i
            }
        }
        return best
    }

    /** 对应原版 InfiniteGridMenu.getVertexWorldPosition(index) */
    private fun getVertexWorldPosition(index: Int): FloatArray {
        if (index !in spherePoints.indices) return floatArrayOf(0f, 0f, 1f)
        return orientation.rotate(spherePoints[index])
    }

    /** 用当前姿态四元数旋转球面点（供绘制/点击检测） */
    private fun rotatePoint(p: FloatArray): FloatArray = orientation.rotate(p)

    /** 当前正对屏幕的 item 索引（外部查询用） */
    fun frontItemIndex(): Int {
        if (spherePoints.isEmpty()) return 0
        val itemCount = max(1, imageUrls.size)
        return findNearestVertexIndex() % itemCount
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                isPointerDown = true
                pointerPos = floatArrayOf(event.x, event.y)
                previousPointerPos = floatArrayOf(event.x, event.y)
                downX = event.x
                downY = event.y
            }
            MotionEvent.ACTION_MOVE -> {
                // 原版 pointermove：只更新 pointerPos，旋转在 update() 里每帧算
                if (isPointerDown) {
                    pointerPos = floatArrayOf(event.x, event.y)
                }
            }
            MotionEvent.ACTION_UP -> {
                isPointerDown = false
                // 位移很小视为点击（原版无点击检测，此为本 Android 版扩展）
                val dx = event.x - downX
                val dy = event.y - downY
                if (dx * dx + dy * dy < 15f * 15f) {
                    checkClick(event.x, event.y)
                }
            }
            MotionEvent.ACTION_CANCEL -> isPointerDown = false
        }
        return true
    }

    /** 点击检测：找到最前面（z 最大）且命中点击位置的图 */
    private fun checkClick(tapX: Float, tapY: Float) {
        val w = width.toFloat()
        val h = height.toFloat()
        val radius = minOf(w, h) * 0.84f * scale * (3f / cameraZ)
        val centerX = w / 2
        val centerY = h / 2

        var bestIndex = -1
        var bestZ = -1f
        val itemCount = max(1, imageUrls.size)
        for (i in spherePoints.indices) {
            val p = rotatePoint(spherePoints[i])
            if (p[2] <= 0.2f) continue // 背面不响应
            // 与 onDraw 相同的尺寸算法
            val depthScale = abs(p[2]) * 0.6f + 0.4f
            val itemSize = radius * 0.45f * depthScale
            val sx = centerX + p[0] * radius * 1.15f
            val sy = centerY + p[1] * radius * 1.15f
            if (abs(tapX - sx) < itemSize / 2 && abs(tapY - sy) < itemSize / 2) {
                if (p[2] > bestZ) {
                    bestZ = p[2]
                    bestIndex = i
                }
            }
        }
        if (bestIndex >= 0) {
            listener?.onItemClick(bestIndex % itemCount)
        }
    }

    private fun notifyMoving(moving: Boolean) {
        if (moving != lastMoving) {
            lastMoving = moving
            listener?.onMovingChange(moving)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 透明背景，露出下层纸张纹理
        if (spherePoints.isEmpty()) return

        val w = width.toFloat()
        val h = height.toFloat()
        // 相机距离影响球的大小：cameraZ 越大球越小（原版 camera.position[2]）
        val radius = minOf(w, h) * 0.84f * scale * (3f / cameraZ)
        val centerX = w / 2
        val centerY = h / 2

        // 计算所有点的投影，按深度排序（远的先画）
        val projected = mutableListOf<Triple<Int, FloatArray, Float>>()
        for (i in spherePoints.indices) {
            val p = rotatePoint(spherePoints[i])
            val z = p[2]
            if (z < 0f) continue // 背面剔除
            val sx = centerX + p[0] * radius * 1.15f
            val sy = centerY + p[1] * radius * 1.15f
            projected.add(Triple(i, floatArrayOf(sx, sy, z), z))
        }
        projected.sortBy { it.third } // z 从小到大（远→近）

        val itemCount = max(1, imageUrls.size)
        for ((index, pos, z) in projected) {
            val imgIndex = index % itemCount
            val bmp = if (imgIndex < bitmaps.size) bitmaps[imgIndex] else null

            // 深度缩放：z 越大越近越大
            val depthScale = abs(z) * 0.6f + 0.4f
            val itemSize = radius * 0.45f * depthScale
            val alpha = smoothstepAlpha(z)

            // 法线方向（用于旋转补偿保持图片正立）
            val dxr = pos[0] - centerX
            val dyr = pos[1] - centerY
            val radLen = sqrt(dxr * dxr + dyr * dyr).toDouble()
            val rx = (dxr / radLen).toFloat()
            val ry = (dyr / radLen).toFloat()
            val rotAngle = atan2(ry, rx) // 径向角度（度）

            if (bmp != null) {
                paint.alpha = (alpha * 255).toInt()
                val half = itemSize / 2f

                canvas.save()
                // 平移到图片中心
                canvas.translate(pos[0], pos[1])
                // 旋转让图片正立（反向补偿径向旋转）
                canvas.rotate(-Math.toDegrees(rotAngle.toDouble()).toFloat())
                // 圆形裁切
                val clipPath = android.graphics.Path()
                clipPath.addCircle(0f, 0f, half, android.graphics.Path.Direction.CW)
                canvas.clipPath(clipPath)
                // CENTER_CROP：从 bitmap 中心裁正方形，等比缩放到圆形直径
                val minDim = minOf(bmp.width, bmp.height).toFloat()
                val srcLeft = (bmp.width - minDim) / 2f
                val srcTop = (bmp.height - minDim) / 2f
                val srcRight = (bmp.width + minDim) / 2f
                val srcBottom = (bmp.height + minDim) / 2f
                val targetRect = android.graphics.RectF(-half, -half, half, half)
                val matrix = android.graphics.Matrix()
                matrix.setRectToRect(
                    android.graphics.RectF(srcLeft, srcTop, srcRight, srcBottom),
                    targetRect,
                    android.graphics.Matrix.ScaleToFit.CENTER
                )
                canvas.drawBitmap(bmp, matrix, paint)
                canvas.restore()
                paint.alpha = 255
            } else {
                // 加载中：灰色圆形占位
                paint.alpha = (alpha * 255).toInt()
                paint.color = 0xFFD8CFC0.toInt()
                val half = itemSize / 2f
                canvas.drawCircle(pos[0], pos[1], half, paint)
                paint.alpha = 255
            }
        }
    }

    /** 对齐原版 shader：vAlpha = smoothstep(0.5, 1, z) * .9 + .1 */
    private fun smoothstepAlpha(z: Float): Float {
        val t = ((z - 0.5f) / (1f - 0.5f)).coerceIn(0f, 1f)
        val s = t * t * (3f - 2f * t)
        return s * 0.9f + 0.1f
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        exec.shutdownNow()
    }
}

/**
 * 最小四元数实现（对齐 gl-matrix quat：[x, y, z, w]）。
 * 对应原版 ArcballControl 用到的操作：multiply、normalize、slerp、setAxisAngle、
 * conjugate、transformQuat（rotate）。
 */
private class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {

    /** 四元数乘法（对应 gl-matrix quat.multiply(out, a, b)） */
    operator fun times(o: Quat): Quat = Quat(
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
        w * o.w - x * o.x - y * o.y - z * o.z
    )

    /** 归一化（对应 gl-matrix quat.normalize） */
    fun normalized(): Quat {
        val n = sqrt(x * x + y * y + z * z + w * w)
        return if (n < 1e-6f) IDENTITY else Quat(x / n, y / n, z / n, w / n)
    }

    /** 共轭（对应 gl-matrix quat.conjugate） */
    fun conjugate(): Quat = Quat(-x, -y, -z, w)

    /** 旋转向量 v（对应 gl-matrix vec3.transformQuat，展开为无矩阵形式） */
    fun rotate(v: FloatArray): FloatArray {
        // t = 2 * (q.xyz × v)
        val tx = 2f * (y * v[2] - z * v[1])
        val ty = 2f * (z * v[0] - x * v[2])
        val tz = 2f * (x * v[1] - y * v[0])
        // v' = v + w * t + (q.xyz × t)
        return floatArrayOf(
            v[0] + w * tx + (y * tz - z * ty),
            v[1] + w * ty + (z * tx - x * tz),
            v[2] + w * tz + (x * ty - y * tx)
        )
    }

    companion object {
        val IDENTITY = Quat(0f, 0f, 0f, 1f)

        /** 绕单位轴 axis 旋转 angle（弧度），对应 gl-matrix quat.setAxisAngle */
        fun fromAxisAngle(axis: FloatArray, angle: Float): Quat {
            val half = angle / 2f
            val s = sin(half)
            return Quat(axis[0] * s, axis[1] * s, axis[2] * s, cos(half)).normalized()
        }

        /** 四元数球面线性插值（对应 gl-matrix quat.slerp），t ∈ [0, 1] */
        fun slerp(a: Quat, b: Quat, t: Float): Quat {
            var dot = a.x * b.x + a.y * b.y + a.z * b.z + a.w * b.w
            var b2 = b
            if (dot < 0f) {
                dot = -dot
                b2 = Quat(-b.x, -b.y, -b.z, -b.w)
            }
            if (dot > 0.9995f) {
                // 夹角太小：用 nlerp 近似避免除零
                return Quat(
                    a.x + (b2.x - a.x) * t,
                    a.y + (b2.y - a.y) * t,
                    a.z + (b2.z - a.z) * t,
                    a.w + (b2.w - a.w) * t
                ).normalized()
            }
            val theta0 = acos(dot)
            val theta = theta0 * t
            val sinTheta = sin(theta)
            val sinTheta0 = sin(theta0)
            val s0 = cos(theta) - dot * sinTheta / sinTheta0
            val s1 = sinTheta / sinTheta0
            return Quat(
                a.x * s0 + b2.x * s1,
                a.y * s0 + b2.y * s1,
                a.z * s0 + b2.z * s1,
                a.w * s0 + b2.w * s1
            ).normalized()
        }
    }
}

// ===== vec3 辅助函数（对应 gl-matrix vec3 操作） =====

private fun normalize3(v: FloatArray): FloatArray {
    val l = length3(v)
    return if (l < 1e-6f) floatArrayOf(0f, 0f, 1f) else floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
}

private fun cross3(a: FloatArray, b: FloatArray): FloatArray = floatArrayOf(
    a[1] * b[2] - a[2] * b[1],
    a[2] * b[0] - a[0] * b[2],
    a[0] * b[1] - a[1] * b[0]
)

private fun dot3(a: FloatArray, b: FloatArray): Float =
    a[0] * b[0] + a[1] * b[1] + a[2] * b[2]

private fun length3(v: FloatArray): Float = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])

/** 对应 gl-matrix vec3.squaredDistance(a, b) */
private fun squaredDistance(a: FloatArray, b: FloatArray): Float {
    val dx = a[0] - b[0]
    val dy = a[1] - b[1]
    val dz = a[2] - b[2]
    return dx * dx + dy * dy + dz * dz
}
