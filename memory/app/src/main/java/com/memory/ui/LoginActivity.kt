package com.memory.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.memory.R
import com.memory.data.Api
import com.memory.databinding.ActivityLoginBinding
import com.memory.ui.main.MainActivity
import com.memory.util.ToastUtil
import java.util.concurrent.Executors

/**
 * 登录页
 * - 已登录（token 持久化在 SharedPreferences）→ 自动进入主页
 * - 「记住我」勾选：会话持久保存；不勾选：仅本次会话（进程内存）
 *   默认勾选，配合后端 30 天 token，实现持久登录
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private val exec = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 登录持久化：已有有效会话则直接进主页
        if (Api.isLoggedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        // 记住我（默认勾选）
        binding.rememberCheck.isChecked = true

        binding.loginBtn.setOnClickListener { doLogin() }
        binding.passwordInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                doLogin()
                true
            } else false
        }
    }

    private fun doLogin() {
        val username = binding.usernameInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        if (username.isEmpty() || password.isEmpty()) {
            binding.errorText.text = "请输入账号和密码"
            binding.errorText.visibility = android.view.View.VISIBLE
            return
        }
        binding.errorText.visibility = android.view.View.GONE
        binding.loginBtn.isEnabled = false
        binding.loginBtn.text = "正在唤醒拾光…"

        val remember = binding.rememberCheck.isChecked

        exec.execute {
            try {
                val result = Api.login(username, password)
                // 记住我：持久保存 token；不记住：仅本次会话
                Api.saveSession(this@LoginActivity, result.token, result.role, username, remember)
                runOnUiThread {
                    ToastUtil.show(this@LoginActivity, "欢迎回来，$username")
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    binding.loginBtn.isEnabled = true
                    binding.loginBtn.text = getString(com.memory.R.string.login_btn)
                    binding.errorText.text = e.message ?: "登录失败"
                    binding.errorText.visibility = android.view.View.VISIBLE
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        exec.shutdownNow()
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, LoginActivity::class.java))
        }
    }
}
