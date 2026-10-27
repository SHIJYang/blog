package com.memory.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.memory.R
import com.memory.data.Api
import com.memory.databinding.ActivityMainBinding
import com.memory.ui.LoginActivity
import com.memory.ui.chat.ChatFragment
import com.memory.util.ToastUtil
import java.io.File

/**
 * 主页：大屏滚动式欢迎页（非列表、非卡片）
 * - 只提供两个入口：拍照 / 从相册上传
 * - 其余记忆全部隐藏，不展示任何列表
 * - 拍照/上传创建成功后进入全屏对话页
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var currentMemoryId: Long? = null

    // 拍照临时文件
    private var cameraFile: File? = null

    // 相册选图（photo picker 官方推荐）
    private val pickImage =
        registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
            if (uri != null) uploadUri(uri)
        }

    // 系统相机拍照
    private val takePhoto =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val file = cameraFile
                if (file != null && file.exists()) {
                    uploadUri(Uri.fromFile(file))
                }
            }
        }

    // 相机权限请求
    private val requestCameraPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) launchCamera() else ToastUtil.show(this, "需要相机权限才能拍照")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!Api.isLoggedIn(this)) {
            LoginActivity.start(this)
            finish()
            return
        }

        // 顶部标题：显示已登录用户名（副标题保留 slogan）
        val username = Api.currentUsername(this)
        if (username.isNotBlank()) {
            binding.topTitle.text = username
        }

        binding.logoutBtn.setOnClickListener {
            Api.clearSession(this)
            ToastUtil.show(this, "已退出，再见")
            LoginActivity.start(this)
            finish()
        }

        binding.cameraBtn.setOnClickListener { handleCameraClick() }
        binding.uploadBtn.setOnClickListener {
            pickImage.launch(
                androidx.activity.result.PickVisualMediaRequest(
                    ActivityResultContracts.PickVisualMedia.ImageOnly
                )
            )
        }
        binding.galleryBtn.setOnClickListener { showGallery() }

        if (savedInstanceState != null) {
            currentMemoryId = savedInstanceState.getLong("currentMemoryId", -1L).takeIf { it > 0 }
        }
    }

    private fun handleCameraClick() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCamera() {
        val file = File(cacheDir, "memory_capture_${System.currentTimeMillis()}.jpg")
        cameraFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        takePhoto.launch(
            Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                putExtra(MediaStore.EXTRA_OUTPUT, uri)
                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        )
    }

    /** 上传照片创建记忆，成功后进入全屏对话 */
    private fun uploadUri(uri: Uri) {
        android.util.Log.d("MainActivity", "uploadUri called uri=$uri")
        // 上传进度反馈：转圈提示，避免用户以为卡死
        val progress = android.app.ProgressDialog(this).apply {
            setMessage("AI 正在收纳这一刻…")
            setCancelable(false)
            show()
        }
        Thread {
            try {
                val file = copyToCache(uri) ?: throw Exception("无法读取图片")
                android.util.Log.d("MainActivity", "file copied: ${file.absolutePath} size=${file.length()}")
                val note = Api.createMemory(this, file)
                android.util.Log.d("MainActivity", "createMemory returned: $note")
                val id = note.get("id")?.asLong ?: 0L
                val title = note.get("autoTitle")?.takeIf { !it.isJsonNull }?.asString ?: "未命名"
                val cover = Api.resolveUrl(
                    note.get("coverThumb")?.takeIf { !it.isJsonNull }?.asString
                        ?: note.get("coverPhoto")?.takeIf { !it.isJsonNull }?.asString
                )
                val emotions = note.get("emotions")?.takeIf { !it.isJsonNull }?.asString ?: ""
                runOnUiThread {
                    android.util.Log.d("MainActivity", "runOnUiThread success id=$id, calling showChat")
                    progress.dismiss()
                    ToastUtil.show(this, "时刻已收纳")
                    if (id > 0) {
                        // 本地压缩图路径一并传入：粒子背景直接用本地图，免等封面下载
                        showChat(id, title, cover, emotions, file.absolutePath)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "upload failed", e)
                runOnUiThread {
                    progress.dismiss()
                    ToastUtil.show(this, e.message ?: "上传失败")
                }
            }
        }.start()
    }

    private fun copyToCache(uri: Uri): File? {
        return try {
            val input = contentResolver.openInputStream(uri) ?: return null
            // 压缩：限最大边长 1600px + JPEG 85%，上传体积降 5-10 倍，加快上传
            val decoded = android.graphics.BitmapFactory.decodeStream(input)
            input.close()
            if (decoded == null) return null
            val w = decoded.width
            val h = decoded.height
            val maxSide = 1600
            val scaled = if (w <= maxSide && h <= maxSide) {
                decoded
            } else {
                val ratio = maxSide.toFloat() / maxOf(w, h)
                android.graphics.Bitmap.createScaledBitmap(
                    decoded, (w * ratio).toInt(), (h * ratio).toInt(), true
                )
            }
            val dir = File(cacheDir, "uploads").apply { mkdirs() }
            val out = File(dir, "memory_${System.currentTimeMillis()}.jpg")
            out.outputStream().use { os ->
                scaled.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, os)
            }
            if (scaled !== decoded) scaled.recycle()
            decoded.recycle()
            out
        } catch (e: Exception) {
            null
        }
    }

    /** 全屏显示对话页 */
    fun showChat(
        memoryId: Long?,
        title: String = "",
        cover: String = "",
        emotions: String = "",
        localImagePath: String = ""
    ) {
        currentMemoryId = memoryId
        android.util.Log.d("MainActivity", "showChat id=$memoryId local=$localImagePath")
        // 显示全屏容器并替换为对话页
        binding.contentPane.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.contentPane, ChatFragment.newInstance(memoryId, title, cover, emotions, localImagePath))
            .commit()
    }

    /** 显示回忆列表（图片网格） */
    fun showGallery() {
        binding.contentPane.visibility = View.VISIBLE
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.contentPane, com.memory.ui.gallery.GalleryFragment())
            .commit()
    }

    /** 对话页/回忆列表返回主页 */
    fun backToHome() {
        supportFragmentManager.findFragmentById(R.id.contentPane)?.let {
            supportFragmentManager.beginTransaction().remove(it).commit()
        }
        binding.contentPane.visibility = View.GONE
        currentMemoryId = null
    }

    override fun onBackPressed() {
        val top = supportFragmentManager.findFragmentById(R.id.contentPane)
        if (top is ChatFragment || top is com.memory.ui.gallery.GalleryFragment) {
            backToHome()
        } else {
            super.onBackPressed()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong("currentMemoryId", currentMemoryId ?: -1L)
    }
}
