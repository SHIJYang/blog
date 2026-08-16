package com.memory.ui.list

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.memory.R
import com.memory.data.Api
import com.memory.databinding.FragmentMemoryListBinding
import com.memory.databinding.ItemInsightBinding
import com.memory.databinding.ItemMemoryBinding
import com.memory.ui.main.MainActivity
import com.memory.util.TimeUtil
import com.memory.util.ToastUtil
import java.io.File
import java.util.concurrent.Executors

class MemoryListFragment : Fragment() {

    private var _binding: FragmentMemoryListBinding? = null
    private val binding get() = _binding!!

    private val exec = Executors.newSingleThreadExecutor()
    private lateinit var adapter: MemoryAdapter

    private var items = mutableListOf<MemoryItem>()
    private var currentKeyword = ""

    data class MemoryItem(
        val id: Long,
        val coverThumb: String? = null,
        val coverPhoto: String? = null,
        val title: String? = null,
        val preview: String? = null,
        val status: Int? = null,
        val privacy: Int? = null,
        val emotions: String? = null,
        val publishedArticleId: Long? = null,
        val createTime: String? = null,
        var insights: List<Api.Insight>? = null,
        var insightsExpanded: Boolean = false,
        var insightInput: String = ""
    )

    private val pickImage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri != null) uploadImage(uri)
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMemoryListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = MemoryAdapter()
        // 主页改为卡片网格（2 列），不再是列表
        binding.memoryList.layoutManager = GridLayoutManager(requireContext(), 2)
        binding.memoryList.adapter = adapter

        binding.uploadArea.setOnClickListener { pickImage.launch(galleryIntent()) }
        binding.searchInput.setOnEditorActionListener { _, _, _ ->
            doSearch()
            true
        }

        loadList()
    }

    private fun galleryIntent(): Intent =
        Intent(Intent.ACTION_GET_CONTENT).apply {
            type = "image/*"
            addCategory(Intent.CATEGORY_OPENABLE)
        }

    private fun loadList() {
        binding.emptyView.visibility = View.GONE
        exec.execute {
            try {
                val page = if (currentKeyword.isBlank()) {
                    Api.listMemories(requireContext(), 1, 100)
                } else {
                    Api.searchMemories(requireContext(), currentKeyword, 1, 100)
                }
                val list = page.records.map { m ->
                    MemoryItem(
                        id = m.id,
                        coverThumb = m.coverThumb ?: m.coverPhoto,
                        coverPhoto = m.coverPhoto,
                        title = m.autoTitle ?: "未命名",
                        preview = m.diaryPreview,
                        status = m.status,
                        privacy = m.privacy,
                        emotions = m.emotions,
                        createTime = m.createTime
                    )
                }
                // 已废弃（status=4）不展示
                val visible = list.filter { it.status != 4 }
                // 已凝聚的记忆补齐发布状态
                val withPublish = visible.map { item ->
                    if (item.status == 3) {
                        val aid = try { Api.publishStatus(requireContext(), item.id) } catch (e: Exception) { null }
                        item.copy(publishedArticleId = aid)
                    } else item
                }
                requireActivity().runOnUiThread {
                    items = withPublish.toMutableList()
                    adapter.notifyDataSetChanged()
                    binding.emptyView.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.emptyView.text = if (currentKeyword.isBlank())
                        "还没有被拾光记住的事\n\n上传一张照片，拾光会温柔开口"
                    else "没有找到「$currentKeyword」相关的记忆"
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.emptyView.text = e.message ?: "加载失败"
                }
            }
        }
    }

    private fun doSearch() {
        val kw = binding.searchInput.text?.toString()?.trim().orEmpty()
        currentKeyword = kw
        loadList()
    }

    /** 外部（对话发送成功等）刷新 */
    fun refresh() {
        _binding?.let { loadList() }
    }

    // ==================== 上传 ====================

    private fun uploadImage(uri: Uri) {
        binding.uploadText.text = getString(R.string.collecting)
        binding.uploadSub.text = "拾光正在收纳这一刻…"
        exec.execute {
            try {
                val file = copyToCache(uri) ?: throw Exception("无法读取图片")
                val note = Api.createMemory(requireContext(), file)
                val id = note.get("id")?.asLong ?: 0L
                val title = note.get("autoTitle")?.asString ?: "未命名"
                val cover = note.get("coverThumb")?.asString ?: note.get("coverPhoto")?.asString ?: ""
                val emotions = note.get("emotions")?.asString ?: ""
                requireActivity().runOnUiThread {
                    binding.uploadText.text = getString(R.string.upload_hint)
                    binding.uploadSub.text = getString(R.string.upload_sub)
                    ToastUtil.show(requireContext(), "时刻已收纳")
                    if (id > 0) {
                        (requireActivity() as MainActivity).showChat(id, title, cover, emotions)
                    }
                    // 列表刷新：先显示新记忆再异步补全发布状态
                    val newItem = MemoryItem(
                        id = id,
                        coverThumb = cover,
                        coverPhoto = note.get("coverPhoto")?.asString,
                        title = title,
                        status = note.get("status")?.takeIf { !it.isJsonNull }?.asInt ?: 1,
                        privacy = note.get("privacy")?.takeIf { !it.isJsonNull }?.asInt ?: 1,
                        emotions = emotions,
                        createTime = note.get("createTime")?.asString
                    )
                    items.add(0, newItem)
                    adapter.notifyDataSetChanged()
                    binding.emptyView.visibility = View.GONE
                }
            } catch (e: Exception) {
                android.util.Log.e("MemoryUpload", "上传失败", e)
                requireActivity().runOnUiThread {
                    binding.uploadText.text = getString(R.string.upload_hint)
                    binding.uploadSub.text = getString(R.string.upload_sub)
                    ToastUtil.show(requireContext(), e.message ?: "上传失败")
                }
            }
        }
    }

    private fun copyToCache(uri: Uri): File? {
        return try {
            val input = requireContext().contentResolver.openInputStream(uri) ?: return null
            val dir = File(requireContext().cacheDir, "uploads").apply { mkdirs() }
            val out = File(dir, "memory_${System.currentTimeMillis()}.jpg")
            input.use { ins ->
                out.outputStream().use { os -> ins.copyTo(os) }
            }
            out
        } catch (e: Exception) {
            null
        }
    }

    // ==================== 操作 ====================

    private fun onEdit(item: MemoryItem) {
        EditDialog.show(requireContext(), item, exec) { loadList() }
    }

    private fun onCondense(item: MemoryItem) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("凝聚确认")
            .setMessage("将把「${item.title}」凝聚为日记，是否继续？")
            .setPositiveButton("凝聚") { _, _ ->
                exec.execute {
                    try {
                        Api.condense(requireContext(), item.id)
                        requireActivity().runOnUiThread {
                            ToastUtil.show(requireContext(), "凝聚完成")
                            loadList()
                            (requireActivity() as MainActivity).notifyListChanged()
                        }
                    } catch (e: Exception) {
                        requireActivity().runOnUiThread { ToastUtil.show(requireContext(), e.message ?: "凝聚失败") }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun onPublish(item: MemoryItem) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("发布确认")
            .setMessage("把「${item.title}」发布为博客文章？")
            .setPositiveButton("发布") { _, _ ->
                exec.execute {
                    try {
                        val aid = Api.publish(requireContext(), item.id)
                        requireActivity().runOnUiThread {
                            ToastUtil.show(requireContext(), if (aid > 0) "已发布为文章 #$aid" else "已发布")
                            loadList()
                        }
                    } catch (e: Exception) {
                        requireActivity().runOnUiThread { ToastUtil.show(requireContext(), e.message ?: "发布失败") }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun onPrivacy(item: MemoryItem) {
        PrivacyDialog.show(requireContext(), item.id, item.privacy ?: 1, exec) { loadList() }
    }

    private fun onAbandon(item: MemoryItem) {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle("废弃确认")
            .setMessage("确定废弃「${item.title}」吗？废弃后不可恢复。")
            .setPositiveButton("确定废弃") { _, _ ->
                exec.execute {
                    try {
                        Api.abandon(requireContext(), item.id)
                        requireActivity().runOnUiThread {
                            ToastUtil.show(requireContext(), "已废弃")
                            loadList()
                        }
                    } catch (e: Exception) {
                        requireActivity().runOnUiThread { ToastUtil.show(requireContext(), e.message ?: "操作失败") }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toggleInsights(item: MemoryItem) {
        if (item.insightsExpanded) {
            item.insightsExpanded = false
            adapter.notifyDataSetChanged()
            return
        }
        item.insightsExpanded = true
        adapter.notifyDataSetChanged()
        if (item.insights == null) {
            exec.execute {
                try {
                    val list = Api.insights(requireContext(), item.id)
                    requireActivity().runOnUiThread {
                        item.insights = list
                        adapter.notifyDataSetChanged()
                    }
                } catch (e: Exception) {
                    requireActivity().runOnUiThread {
                        item.insights = emptyList()
                        adapter.notifyDataSetChanged()
                    }
                }
            }
        }
    }

    private fun addInsight(item: MemoryItem) {
        val text = item.insightInput.trim()
        if (text.isEmpty()) {
            ToastUtil.show(requireContext(), "先写点什么")
            return
        }
        exec.execute {
            try {
                Api.addInsight(requireContext(), item.id, text)
                val list = Api.insights(requireContext(), item.id)
                requireActivity().runOnUiThread {
                    item.insights = list
                    item.insightInput = ""
                    adapter.notifyDataSetChanged()
                    ToastUtil.show(requireContext(), "闪光点已记下")
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread { ToastUtil.show(requireContext(), e.message ?: "添加失败") }
            }
        }
    }

    private fun deleteInsight(item: MemoryItem, insight: Api.Insight) {
        exec.execute {
            try {
                Api.deleteInsight(requireContext(), insight.id)
                val list = Api.insights(requireContext(), item.id)
                requireActivity().runOnUiThread {
                    item.insights = list
                    adapter.notifyDataSetChanged()
                    ToastUtil.show(requireContext(), "已删除")
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread { ToastUtil.show(requireContext(), e.message ?: "删除失败") }
            }
        }
    }

    // ==================== Adapter ====================

    inner class MemoryAdapter : RecyclerView.Adapter<MemoryAdapter.VH>() {

        inner class VH(val b: ItemMemoryBinding) : RecyclerView.ViewHolder(b.root)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
            VH(ItemMemoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: VH, position: Int) {
            val item = items[position]
            val b = holder.b

            val cover = item.coverThumb ?: item.coverPhoto
            if (cover.isNullOrBlank()) {
                b.coverImage.setImageResource(R.drawable.bg_sun_soft)
            } else {
                b.coverImage.load(cover) {
                    error(R.drawable.bg_sun_soft)
                    placeholder(R.drawable.bg_sun_soft)
                }
            }

            b.titleText.text = item.title ?: "未命名"
            b.statusText.text = statusLabel(item.status)
            b.timeText.text = TimeUtil.format(item.createTime)

            // 情绪 tag
            val emo = item.emotions?.split(",")?.firstOrNull { it.isNotBlank() }
            if (emo.isNullOrBlank()) {
                b.emotionTag.visibility = View.GONE
            } else {
                b.emotionTag.visibility = View.VISIBLE
                b.emotionTag.text = emo
            }

            // 已发布 tag
            b.publishedTag.visibility = if (item.publishedArticleId != null) View.VISIBLE else View.GONE

            // 预览
            if (item.preview.isNullOrBlank()) {
                b.previewText.visibility = View.GONE
            } else {
                b.previewText.visibility = View.VISIBLE
                b.previewText.text = item.preview
            }

            // 操作按钮可见性
            b.btnCondense.visibility = if (item.status == 2 || item.status == 3) View.VISIBLE else View.GONE
            b.btnPublish.visibility = if (item.status == 3 && item.publishedArticleId == null) View.VISIBLE else View.GONE

            // 点击
            b.root.setOnClickListener {
                (requireActivity() as MainActivity).showChat(
                    item.id, item.title ?: "", item.coverThumb ?: item.coverPhoto ?: "", item.emotions ?: ""
                )
            }
            b.btnEdit.setOnClickListener { onEdit(item) }
            b.btnCondense.setOnClickListener { onCondense(item) }
            b.btnPublish.setOnClickListener { onPublish(item) }
            b.btnPrivacy.setOnClickListener { onPrivacy(item) }
            b.btnAbandon.setOnClickListener { onAbandon(item) }
            b.btnInsights.setOnClickListener { toggleInsights(item) }

            // 闪光点展开区
            if (item.insightsExpanded) {
                b.insightsBox.visibility = View.VISIBLE
                b.btnInsights.text = "收起闪光点"
                bindInsights(b, item)
            } else {
                b.insightsBox.visibility = View.GONE
                b.btnInsights.text = "闪光点"
            }

            // 输入框
            b.insightInput.setText(item.insightInput)
            b.insightInput.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus) item.insightInput = b.insightInput.text?.toString() ?: ""
            }
            b.insightInput.setOnEditorActionListener { _, _, _ ->
                addInsight(item)
                true
            }
            b.btnAddInsight.setOnClickListener { addInsight(item) }
        }

        private fun bindInsights(b: ItemMemoryBinding, item: MemoryItem) {
            b.insightList.removeAllViews()
            val list = item.insights
            if (list.isNullOrEmpty()) {
                val tv = TextView(b.root.context).apply {
                    text = "（还没有闪光点，AI 凝聚时自动提炼，也可以自己添加）"
                    setTextSize(11f)
                    setTextColor(resources.getColor(R.color.text_hint, null))
                    setPadding(4, 4, 4, 4)
                }
                b.insightList.addView(tv)
            } else {
                for (ins in list) {
                    val row = ItemInsightBinding.inflate(LayoutInflater.from(b.root.context), b.insightList, false)
                    if (ins.emotion.isNullOrBlank()) {
                        row.insightEmotion.visibility = View.GONE
                    } else {
                        row.insightEmotion.visibility = View.VISIBLE
                        row.insightEmotion.text = ins.emotion
                    }
                    row.insightContent.text = ins.content ?: ""
                    row.btnDeleteInsight.setOnClickListener { deleteInsight(item, ins) }
                    b.insightList.addView(row.root)
                }
            }
        }
    }

    private fun statusLabel(s: Int?): String = when (s) {
        1 -> "未开始"
        2 -> "对话中"
        3 -> "已成念"
        4 -> "已废弃"
        else -> ""
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        exec.shutdownNow()
    }
}
