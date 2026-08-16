package com.memory.ui.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.memory.data.Api
import com.memory.databinding.FragmentGalleryBinding
import com.memory.ui.main.MainActivity
import java.util.concurrent.Executors

/**
 * 回忆长廊：3D 无限球体菜单（移植自 Vue Bits <InfiniteMenu />）
 * - 记忆图片贴在 3D 球面上，拖拽 + 惯性 + 自动定格吸附
 * - 定格后正面图片上方显示记忆名字，滑动时淡出
 * - 点击最前面的图片 → 进入该记忆的对话页
 * - 无任何功能按钮
 */
class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val exec = Executors.newSingleThreadExecutor()
    private val items = mutableListOf<Api.MemoryItem>()
    private var isMoving = false
    private var lastActiveIdx = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.backBtn.setOnClickListener {
            (requireActivity() as? MainActivity)?.backToHome()
        }

        binding.sphereView.listener = object : SphereMenuView.Listener {
            override fun onItemClick(index: Int) {
                if (index < items.size) {
                    val item = items[index]
                    (requireActivity() as MainActivity).showChat(
                        item.id,
                        item.autoTitle ?: "我的记忆",
                        Api.resolveUrl(item.coverThumb ?: item.coverPhoto),
                        item.emotions ?: ""
                    )
                }
            }

            override fun onActiveItemChange(index: Int) {
                // 定格后：正面图片上方显示记忆名字
                if (index in items.indices && index != lastActiveIdx) {
                    lastActiveIdx = index
                    val tv = binding.activeTitle ?: return
                    tv.text = items[index].autoTitle ?: "我的记忆"
                    if (!isMoving) {
                        tv.alpha = 1f
                        tv.visibility = View.VISIBLE
                        tv.animate().cancel()
                    }
                }
            }

            override fun onMovingChange(moving: Boolean) {
                isMoving = moving
                val tv = binding.activeTitle ?: return
                if (moving) {
                    tv.animate().cancel()
                    tv.alpha = 0f
                } else {
                    // 定格静止：恢复标题可见，并更新为新正面的标题
                    tv.animate().cancel()
                    tv.alpha = 1f
                    if (lastActiveIdx in items.indices) {
                        tv.text = items[lastActiveIdx].autoTitle ?: "我的记忆"
                        tv.visibility = View.VISIBLE
                    }
                }
            }
        }

        loadGallery()
    }

    /** 加载回忆图片（只取有封面的） */
    private fun loadGallery() {
        binding.emptyView.visibility = View.GONE
        exec.execute {
            try {
                val page = Api.listMemories(requireContext(), 1, 200)
                val visible = page.records.filter { it.status != 4 && !it.coverThumb.isNullOrBlank() }
                requireActivity().runOnUiThread {
                    items.clear()
                    items.addAll(visible)
                    if (items.isEmpty()) {
                        binding.emptyView.visibility = View.VISIBLE
                    } else {
                        val urls = items.map { Api.resolveUrl(it.coverThumb ?: it.coverPhoto) }
                        binding.sphereView.setImages(urls)
                    }
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    binding.emptyView.visibility = View.VISIBLE
                    binding.emptyView.text = e.message ?: "加载失败"
                }
            }
        }
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
