package com.memory.util

import android.content.Context
import android.widget.Toast

object ToastUtil {
    fun show(ctx: Context, msg: String) {
        Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
    }
}
