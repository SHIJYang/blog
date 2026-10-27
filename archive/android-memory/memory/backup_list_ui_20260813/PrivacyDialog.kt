package com.memory.ui.list

import android.content.Context
import com.memory.data.Api
import com.memory.util.ToastUtil
import java.util.concurrent.Executor

object PrivacyDialog {

    fun show(context: Context, memoryId: Long, current: Int, exec: Executor, onSaved: () -> Unit) {
        val options = arrayOf("公开", "私密", "封存")
        val checked = when (current) {
            0 -> 0
            2 -> 2
            else -> 1
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("记忆隐私")
            .setSingleChoiceItems(options, checked) { _, which ->
                val newPrivacy = when (which) {
                    0 -> 0
                    2 -> 2
                    else -> 1
                }
                exec.execute {
                    try {
                        Api.setPrivacy(context, memoryId, newPrivacy)
                        (context as? android.app.Activity)?.runOnUiThread {
                            ToastUtil.show(context, when (newPrivacy) {
                                0 -> "已设为公开"
                                1 -> "已设为私密"
                                else -> "已封存（AI 不再召回）"
                            })
                            onSaved()
                        }
                    } catch (e: Exception) {
                        (context as? android.app.Activity)?.runOnUiThread {
                            ToastUtil.show(context, e.message ?: "操作失败")
                        }
                    }
                }
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
