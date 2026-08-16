package com.memory.ui.chat

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.memory.R
import com.memory.data.Api
import com.memory.databinding.FragmentChatBinding
import com.memory.databinding.ItemChatAiBinding
import com.memory.databinding.ItemChatMeBinding
import com.memory.ui.main.MainActivity
import com.memory.util.ToastUtil
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

class ChatFragment : Fragment() {

    private var _binding: FragmentChatBinding? = null
    private val binding get() = _binding!!

    private val exec = Executors.newSingleThreadExecutor()

    private var memoryId: Long? = null
    private var title = ""
    private var cover = ""
    private var emotions = ""

    /** 刚上传的本地压缩图路径（优先用于粒子背景，免网络下载） */
    private var localImagePath = ""

    // 对话状态
    private val messages = mutableListOf<ChatMsg>()
    private var opening = ""
    private var sending = false
    private var imgSending = false

    // 验证码
    private var captchaKey = ""
    private var captchaImage: String? = null
    private var needCaptcha = true

    data class ChatMsg(
        val role: String, // "me" | "ai"
        val content: String,
        val imgUrl: String? = null,
        val emotion: String? = null
    )

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null && memoryId != null) sendImage(uri)
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentChatBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setup()
    }

    private fun setup() {
        binding.messageList.layoutManager = LinearLayoutManager(requireContext()).apply {
            stackFromEnd = true
        }
        binding.messageList.adapter = ChatAdapter()

        binding.sendBtn.setOnClickListener { sendText() }
        // 输入法回车键发送
        binding.messageInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == android.view.inputmethod.EditorInfo.IME_ACTION_DONE ||
                actionId == android.view.inputmethod.EditorInfo.IME_ACTION_SEND
            ) {
                sendText()
                true
            } else false
        }
        binding.backBtn.setOnClickListener {
            (requireActivity() as? MainActivity)?.backToHome()
        }
        binding.imgBtn.setOnClickListener {
            pickImage.launch(Intent(Intent.ACTION_GET_CONTENT).apply {
                type = "image/*"
                addCategory(Intent.CATEGORY_OPENABLE)
            })
        }
        binding.publishBtn.setOnClickListener { doPublish() }

        needCaptcha = !Api.isCaptchaFree(requireContext())

        if (memoryId == null) {
            binding.emptyState.visibility = View.VISIBLE
            binding.chatArea.visibility = View.GONE
            return
        }

        binding.emptyState.visibility = View.GONE
        binding.chatArea.visibility = View.VISIBLE
        binding.headerTitle.text = title.ifBlank { "我的记忆" }
        // 先让粒子层可见（GLSurfaceView 需要 surface 才能渲染），再设置粒子数据
        binding.particleView.visibility = View.VISIBLE
        // 记忆图片作为背景 + 3D 旋转动画 + 粒子效果
        setupBgRotate()

        loadHistoryAndOpening()
    }

    /** 记忆图片作为背景 + 3D 旋转动画（Y 轴 360° 无限循环） */
    private var bgAnimator: android.animation.ObjectAnimator? = null

    private fun setupBgRotate() {
        // 背景 = 仅 3D 粒子旋转动画（记忆图片粒子云），不再显示居中旋转照片
        binding.bgRotateImage.visibility = View.GONE
        // 优先用刚上传的本地压缩图（省一次封面网络下载），否则用封面 URL
        val imageSource = if (localImagePath.isNotBlank() && File(localImagePath).exists()) {
            localImagePath
        } else {
            Api.resolveUrl(cover)
        }
        android.util.Log.d("Particle3D", "setupBgRotate source=$imageSource")
        if (imageSource.isBlank()) {
            binding.particleView.visibility = View.GONE
            return
        }
        // 3D 照片粒子背景：用记忆封面生成粒子云，Y 轴 360° 无限旋转
        binding.particleView.setImage(imageSource)
    }

    private fun loadHistoryAndOpening() {
        val id = memoryId ?: return
        exec.execute {
            try {
                val detail = Api.memoryDetail(requireContext(), id)
                val arr = detail.getAsJsonArray("messages")
                val history = mutableListOf<ChatMsg>()
                arr?.forEach { el ->
                    val obj = el.asJsonObject
                    val type = obj.get("msgType")?.asInt ?: 1
                    val content = obj.get("content")?.takeIf { !it.isJsonNull }?.asString ?: ""
                    val img = obj.get("imgUrl")?.takeIf { !it.isJsonNull }?.asString
                    val emotion = obj.get("emotion")?.takeIf { !it.isJsonNull }?.asString
                    history.add(
                        ChatMsg(
                            role = if (type == 2) "ai" else "me",
                            content = if (type == 3 && content.isBlank()) "(图片)" else content,
                            imgUrl = if (type == 3) img else null,
                            emotion = emotion
                        )
                    )
                }
                requireActivity().runOnUiThread {
                    messages.clear()
                    messages.addAll(history)
                    (binding.messageList.adapter as? ChatAdapter)?.notifyDataSetChanged()
                    scrollToBottom()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    ToastUtil.show(requireContext(), e.message ?: "加载对话失败")
                }
            }
        }
        // opening 并行请求：独立线程，不等 memoryDetail，加载完回主线程
        Thread {
            val open = try { Api.opening(requireContext(), id) } catch (e: Exception) { "" }
            requireActivity().runOnUiThread { opening = open }
        }.start()
    }

    // ==================== 验证码（发送时弹窗） ====================

    /** 加载验证码到变量（弹窗展示用），成功后回调 */
    private fun loadCaptcha(onLoaded: (() -> Unit)? = null) {
        exec.execute {
            try {
                val c = Api.captcha(requireContext())
                requireActivity().runOnUiThread {
                    captchaKey = c.key
                    captchaImage = c.image
                    onLoaded?.invoke()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    ToastUtil.show(requireContext(), "验证码加载失败：${e.message}")
                }
            }
        }
    }

    /** 弹验证码对话框（发送前需验证时调用） */
    private var captchaDialog: android.app.AlertDialog? = null
    private var captchaDialogInput: android.widget.EditText? = null
    private var captchaDialogImage: android.widget.ImageView? = null

    private fun showCaptchaDialog(onConfirm: (code: String, key: String) -> Unit) {
        val ctx = requireContext()
        val input = android.widget.EditText(ctx).apply {
            hint = "输入验证码"
            inputType = android.text.InputType.TYPE_CLASS_TEXT
            maxLines = 1
            setPadding(24, 12, 24, 12)
        }
        val img = android.widget.ImageView(ctx)
        img.layoutParams = android.widget.LinearLayout.LayoutParams(
            android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
            120.dp()
        ).apply { bottomMargin = 16.dp() }
        img.scaleType = android.widget.ImageView.ScaleType.FIT_XY
        img.background = ctx.getDrawable(R.drawable.bg_upload)
        img.isClickable = true
        img.setOnClickListener { refreshDialogCaptcha(img, input) }
        captchaDialogInput = input
        captchaDialogImage = img

        val dialog = android.app.AlertDialog.Builder(ctx)
            .setTitle("输入验证码")
            .setMessage("点击图片可刷新验证码")
            .setView(
                android.widget.LinearLayout(ctx).apply {
                    orientation = android.widget.LinearLayout.VERTICAL
                    setPadding(40, 8, 40, 8)
                    addView(img)
                    addView(input)
                }
            )
            .setPositiveButton("确认", null) // 手动控制避免自动 dismiss
            .setNegativeButton("取消", null)
            .create()
        captchaDialog = dialog

        dialog.setOnShowListener {
            dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val code = input.text?.toString()?.trim().orEmpty()
                if (code.isEmpty()) {
                    ToastUtil.show(ctx, "请输入验证码")
                    return@setOnClickListener
                }
                onConfirm(code, captchaKey)
            }
        }

        refreshDialogCaptcha(img, input)
        dialog.show()
    }

    private fun refreshDialogCaptcha(img: android.widget.ImageView, input: android.widget.EditText) {
        input.text?.clear()
        loadCaptcha {
            val imgData = captchaImage
            if (imgData.isNullOrBlank()) return@loadCaptcha
            val b64 = imgData.substringAfter(",", imgData)
            val bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            val bmp = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bmp != null) img.setImageBitmap(bmp)
        }
    }

    private fun Int.dp(): Int = (this * resources.displayMetrics.density).toInt()

    // ==================== 发送文字 ====================

    private fun sendText() {
        val content = binding.messageInput.text?.toString()?.trim().orEmpty()
        val id = memoryId ?: return
        if (content.isEmpty()) return
        if (sending) return

        // 需要验证码：先弹窗，确认后带验证码发送
        if (needCaptcha) {
            showCaptchaDialog { code, key ->
                captchaDialog?.dismiss()
                captchaDialog = null
                performSend(content, code, key)
            }
            return
        }

        performSend(content, null, null)
    }

    private fun performSend(content: String, code: String?, key: String?) {
        val id = memoryId ?: return
        messages.add(ChatMsg("me", content))
        binding.messageInput.text?.clear()
        (binding.messageList.adapter as? ChatAdapter)?.notifyDataSetChanged()
        scrollToBottom()

        sending = true
        binding.sendBtn.isEnabled = false

        exec.execute {
            try {
                val reply = Api.chatSend(requireContext(), id, content, code, key)
                requireActivity().runOnUiThread {
                    sending = false
                    binding.sendBtn.isEnabled = true
                    // 打字机效果显示 AI 回复（自然感）
                    typewriterAppend(reply.reply)
                    // 记住情绪（如返回）
                    if (!reply.emotion.isNullOrBlank()) {
                        emotions = reply.emotion
                    }
                    // 成功后 10 分钟免验证
                    Api.markCaptchaFree(requireContext())
                    needCaptcha = false
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    sending = false
                    binding.sendBtn.isEnabled = true
                    val msg = e.message ?: "发送失败"
                    ToastUtil.show(requireContext(), msg)
                    // 验证码错误 → 清除免验证并重新弹窗
                    if (msg.contains("验证码") || msg.contains("captcha") || e is Api.ApiException && e.code == 400) {
                        Api.clearCaptchaFree(requireContext())
                        needCaptcha = true
                        // 重新弹窗让用户再输一次
                        if (content.isNotBlank()) {
                            sendText()
                        }
                    }
                }
            }
        }
    }

    // ==================== 发送图片 ====================

    private fun sendImage(uri: Uri) {
        val id = memoryId ?: return
        if (imgSending) return
        imgSending = true
        binding.imgBtn.isEnabled = false
        messages.add(ChatMsg("me", "(发送了一张图片)"))
        (binding.messageList.adapter as? ChatAdapter)?.notifyDataSetChanged()
        scrollToBottom()

        exec.execute {
            try {
                val file = copyToCache(uri) ?: throw Exception("无法读取图片")
                Api.chatImage(requireContext(), id, file)
                requireActivity().runOnUiThread {
                    imgSending = false
                    binding.imgBtn.isEnabled = true
                    ToastUtil.show(requireContext(), "图片已发送")
                    // 刷新历史以拿到 AI 对图片的描述
                    loadHistoryAndOpening()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    imgSending = false
                    binding.imgBtn.isEnabled = true
                    ToastUtil.show(requireContext(), e.message ?: "图片发送失败")
                }
            }
        }
    }

    private fun copyToCache(uri: Uri): File? {
        return try {
            val input = requireContext().contentResolver.openInputStream(uri) ?: return null
            val dir = File(requireContext().cacheDir, "uploads").apply { mkdirs() }
            val out = File(dir, "chat_${System.currentTimeMillis()}.jpg")
            input.use { ins ->
                out.outputStream().use { os -> ins.copyTo(os) }
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    // ==================== 语音输出（TextToSpeech） ====================

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    private fun initTts() {
        if (tts != null) return
        tts = TextToSpeech(requireContext()) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINA
                ttsReady = true
            }
        }
    }

    /** 朗读 AI 回复（语音输出） */
    private fun speak(text: String) {
        if (text.isBlank()) return
        initTts()
        if (!ttsReady) {
            // TTS 未就绪，稍后重试
            Handler(Looper.getMainLooper()).postDelayed({ speak(text) }, 500)
            return
        }
        try {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_${System.currentTimeMillis()}")
        } catch (e: Exception) {
            // 忽略
        }
    }

    // ==================== 打字机效果（对话自然） ====================

    private val typeHandler = Handler(Looper.getMainLooper())
    private var typingRunnable: Runnable? = null

    /** 打字机显示 AI 回复：逐字追加，自然感 */
    private fun typewriterAppend(content: String) {
        val full = content
        val holder = (binding.messageList.adapter as? ChatAdapter)
        // 先加一条空 AI 消息，然后逐字填充
        messages.add(ChatMsg("ai", ""))
        holder?.notifyDataSetChanged()
        scrollToBottom()

        val index = intArrayOf(0)
        typingRunnable?.let { typeHandler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                val i = index[0]
                if (i >= full.length) {
                    // 打字完成，朗读
                    speak(full)
                    return
                }
                // 每次追加 1-2 字
                val step = if (i % 3 == 0) 2 else 1
                val end = (i + step).coerceAtMost(full.length)
                val idx = messages.size - 1
                if (idx >= 0 && messages[idx].role == "ai") {
                    messages[idx] = ChatMsg("ai", full.substring(0, end))
                }
                holder?.notifyItemChanged(idx)
                scrollToBottom()
                index[0] = end
                typeHandler.postDelayed(this, 30L)
            }
        }
        typingRunnable = runnable
        typeHandler.post(runnable)
    }

    // ==================== 凝聚并发布 ====================

    private fun doPublish() {
        val id = memoryId ?: return
        val displayName = title.ifBlank { "这条记忆" }
        ToastUtil.show(requireContext(), "正在发布：$displayName …")
        binding.publishBtn.isEnabled = false

        exec.execute {
            try {
                // 第一步：凝聚
                val note = Api.condense(requireContext(), id)
                val newTitle = note.get("autoTitle")?.asString ?: ""
                val newEmotions = note.get("emotions")?.asString ?: ""
                // 第二步：发布（凝聚完成后立即发布）
                val publishedId = Api.publish(requireContext(), id)
                requireActivity().runOnUiThread {
                    ToastUtil.show(requireContext(), if (publishedId > 0) "发布成功 ✓" else "发布完成")
                    binding.publishBtn.isEnabled = true
                    if (newTitle.isNotBlank()) {
                        title = newTitle
                        binding.headerTitle.text = newTitle
                    }
                    if (newEmotions.isNotBlank()) {
                        emotions = newEmotions
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    binding.publishBtn.isEnabled = true
                    ToastUtil.show(requireContext(), e.message ?: "发布失败")
                }
            }
        }
    }

    private fun scrollToBottom() {
        binding.messageList.post {
            binding.messageList.scrollToPosition(binding.messageList.adapter?.itemCount?.minus(1) ?: 0)
        }
    }

    // ==================== Adapter ====================

    inner class ChatAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        override fun getItemCount(): Int = messages.size + (if (sending) 1 else 0)

        override fun getItemViewType(position: Int): Int =
            if (position < messages.size) {
                if (messages[position].role == "ai") 0 else 1
            } else 2 // typing

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                0 -> AiVH(ItemChatAiBinding.inflate(inflater, parent, false))
                1 -> MeVH(ItemChatMeBinding.inflate(inflater, parent, false))
                else -> TypingVH(ItemChatAiBinding.inflate(inflater, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (holder) {
                is AiVH -> {
                    val m = messages[position]
                    holder.b.aiText.text = m.content
                    holder.b.aiEmotion.text = m.emotion
                    holder.b.aiEmotion.visibility = if (m.emotion.isNullOrBlank()) View.GONE else View.VISIBLE
                    if (m.imgUrl.isNullOrBlank()) {
                        holder.b.aiImage.visibility = View.GONE
                    } else {
                        holder.b.aiImage.visibility = View.VISIBLE
                        holder.b.aiImage.load(m.imgUrl) { error(R.drawable.bg_sun_soft) }
                    }
                }
                is MeVH -> {
                    val m = messages[position]
                    holder.b.meText.text = m.content
                    holder.b.meEmotion.text = m.emotion
                    holder.b.meEmotion.visibility = if (m.emotion.isNullOrBlank()) View.GONE else View.VISIBLE
                    if (m.imgUrl.isNullOrBlank()) {
                        holder.b.meImage.visibility = View.GONE
                    } else {
                        holder.b.meImage.visibility = View.VISIBLE
                        holder.b.meImage.load(m.imgUrl) { error(R.drawable.bg_sun_soft) }
                    }
                }
                is TypingVH -> {
                    holder.b.aiText.text = getString(R.string.ai_thinking)
                }
            }
        }

        inner class AiVH(val b: ItemChatAiBinding) : RecyclerView.ViewHolder(b.root)
        inner class MeVH(val b: ItemChatMeBinding) : RecyclerView.ViewHolder(b.root)
        inner class TypingVH(val b: ItemChatAiBinding) : RecyclerView.ViewHolder(b.root)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bgAnimator?.cancel()
        bgAnimator = null
        typingRunnable?.let { typeHandler.removeCallbacks(it) }
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        exec.shutdownNow()
        try {
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (e: Exception) {}
    }

    companion object {
        /** 调试开关：true=3D 旋转动画（默认），false=静态背景（便于自动化验证） */
        var ENABLE_BG_ROTATE: Boolean = true

        fun newInstance(
            memoryId: Long?,
            title: String = "",
            cover: String = "",
            emotions: String = "",
            localImagePath: String = ""
        ): ChatFragment {
            val f = ChatFragment()
            f.memoryId = memoryId
            f.title = title
            f.cover = cover
            f.emotions = emotions
            f.localImagePath = localImagePath
            return f
        }
    }
}
