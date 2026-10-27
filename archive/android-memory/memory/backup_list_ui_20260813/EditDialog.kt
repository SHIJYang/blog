package com.memory.ui.list

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import com.memory.R
import java.util.concurrent.Executor

/**
 * 编辑记忆：标题 / 情绪标签（逗号分隔）/ 正文
 */
object EditDialog {

    fun show(
        context: Context,
        item: MemoryListFragment.MemoryItem,
        exec: Executor,
        onSaved: () -> Unit
    ) {
        val inflater = LayoutInflater.from(context)
        val view = inflater.inflate(R.layout.dialog_edit_memory, null)
        val titleInput = view.findViewById<EditText>(R.id.editTitle)
        val emotionInput = view.findViewById<EditText>(R.id.editEmotions)
        val diaryInput = view.findViewById<EditText>(R.id.editDiary)

        titleInput.setText(item.title ?: "")
        emotionInput.setText(item.emotions ?: "")
        diaryInput.setText(item.preview ?: "")

        AlertDialog.Builder(context)
            .setTitle("编辑记忆")
            .setView(view)
            .setPositiveButton("保存") { _, _ ->
                val title = titleInput.text?.toString()?.trim().orEmpty()
                val emotions = emotionInput.text?.toString()?.trim().orEmpty()
                val diary = diaryInput.text?.toString()?.trim().orEmpty()
                val emoList = if (emotions.isEmpty()) null else emotions.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                exec.execute {
                    try {
                        com.memory.data.Api.updateMemory(
                            context,
                            item.id,
                            if (title.isEmpty()) null else title,
                            emoList,
                            if (diary.isEmpty()) null else diary
                        )
                        (context as? android.app.Activity)?.runOnUiThread {
                            android.widget.Toast.makeText(context, "已保存", android.widget.Toast.LENGTH_SHORT).show()
                            onSaved()
                        }
                    } catch (e: Exception) {
                        (context as? android.app.Activity)?.runOnUiThread {
                            android.widget.Toast.makeText(context, e.message ?: "保存失败", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
