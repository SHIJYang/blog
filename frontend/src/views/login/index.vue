<template>
  <div class="page">
    <div class="center">
      <h1 class="big-text">{{ activeTab === 'login' ? '登录' : '注册' }}</h1>

      <div class="divider"></div>

      <!-- 移除 transition，直接切换 -->
      <div v-if="activeTab === 'login'" key="login" class="form-pane">
        <el-form ref="loginFormRef" :model="loginForm" :rules="loginRules" @keyup.enter="handleLogin" class="form">
          <div class="field">
            <label>用户名</label>
            <input v-model="loginForm.username" placeholder="" maxlength="20" />
          </div>
          <div class="field">
            <label>密码</label>
            <input v-model="loginForm.password" type="password" placeholder="" maxlength="20" />
          </div>
          <div v-if="loginError" class="error-msg">{{ loginError }}</div>
          <button type="button" class="submit-btn" :disabled="loginLoading" @click="handleLogin">
            {{ loginLoading ? '登录中...' : '登录' }}
          </button>
        </el-form>
      </div>

      <div v-else key="register" class="form-pane">
        <el-form ref="registerFormRef" :model="registerForm" :rules="registerRules" @keyup.enter="handleRegister"
          class="form">
          <div class="field">
            <label>用户名</label>
            <input v-model="registerForm.username" placeholder="" maxlength="20" />
          </div>
          <div class="field">
            <label>密码</label>
            <input v-model="registerForm.password" type="password" placeholder="" maxlength="20" />
          </div>
          <div class="field">
            <label>昵称</label>
            <input v-model="registerForm.nickname" placeholder="" />
          </div>
          <div class="field">
            <label>邮箱</label>
            <input v-model="registerForm.email" type="email" placeholder="" />
          </div>
          <div v-if="registerError" class="error-msg">{{ registerError }}</div>
          <button type="button" class="submit-btn" :disabled="registerLoading" @click="handleRegister">
            {{ registerLoading ? '注册中...' : '注册' }}
          </button>
        </el-form>
      </div>

      <div class="foot">
        <span>{{ activeTab === 'login' ? '还没有账号？' : '已有账号？' }}</span>
        <button class="switch-link" @click="switchForms">
          {{ activeTab === 'login' ? '注册' : '登录' }}
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import gsap from 'gsap'
import { useAuthStore } from '@/stores/auth'
import { register as registerApi } from '@/api/auth'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()
const activeTab = ref('login')

let tl = null

// 初始入场动画
onMounted(() => {
  tl = gsap.timeline({ defaults: { ease: 'power3.out' } })
  tl.to('.big-text', { y: 0, opacity: 1, duration: 0.8 })
    .to('.divider', { scaleX: 1, duration: 0.6, ease: 'power2.inOut' }, '-=0.3')
    .to('.form-pane', { y: 0, opacity: 1, duration: 0.5 }, '-=0.3')
    .to('.field', { y: 0, opacity: 1, stagger: 0.06, duration: 0.4 }, '-=0.3')
    .to('.submit-btn', { y: 0, opacity: 1, duration: 0.4 }, '-=0.2')
    .to('.foot', { opacity: 1, duration: 0.3 }, '-=0.1')
})

onUnmounted(() => {
  tl?.kill()
})

// 包装 GSAP 动画为 Promise
const animate = (targets, vars) => {
  return new Promise((resolve) => {
    gsap.to(targets, { ...vars, onComplete: resolve })
  })
}

/**
 * 切换登录/注册表单（完全由 GSAP 控制）
 */
const switchForms = async () => {
  const pane = document.querySelector('.form-pane')
  if (!pane) return

  const direction = activeTab.value === 'login' ? -30 : 30

  // 离场动画：当前表单左移/右移并淡出
  await animate(pane, { x: direction, opacity: 0, duration: 0.2, ease: 'power2.in' })

  // 切换表单类型
  activeTab.value = activeTab.value === 'login' ? 'register' : 'login'

  // 等待 Vue 渲染新表单
  await nextTick()

  // 获取新表单元素并执行入场动画
  const newPane = document.querySelector('.form-pane')
  if (newPane) {
    // 重置新表单初始状态（确保从正确位置开始）
    gsap.set(newPane, { x: activeTab.value === 'login' ? -30 : 30, opacity: 0 })
    gsap.set(newPane.querySelectorAll('.field'), { y: 10, opacity: 0 })
    gsap.set(newPane.querySelector('.submit-btn'), { y: 10, opacity: 0 })

    // 表单容器入场
    await animate(newPane, { x: 0, opacity: 1, duration: 0.4, ease: 'power3.out' })
    // 字段逐项入场
    await animate(newPane.querySelectorAll('.field'), { y: 0, opacity: 1, stagger: 0.05, duration: 0.3, ease: 'power3.out' })
    // 提交按钮入场
    await animate(newPane.querySelector('.submit-btn'), { y: 0, opacity: 1, duration: 0.3, ease: 'power3.out' })
  }
}

// ── 登录 ──
const loginFormRef = ref(null)
const loginLoading = ref(false)
const loginError = ref('')
const loginForm = reactive({ username: '', password: '' })
const loginRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 2, max: 20, message: '2-20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '6-20个字符', trigger: 'blur' }
  ]
}

const handleLogin = async () => {
  const valid = await loginFormRef.value?.validate().catch(() => false)
  if (!valid) return
  loginLoading.value = true
  loginError.value = ''
  try {
    const response = await authStore.login(loginForm)
    if (!response?.token) throw new Error('登录失败')
    const { token, role } = response
    localStorage.setItem('token', token)
    localStorage.setItem('role', role)
    localStorage.setItem('username', loginForm.username)
    authStore.setAuth(token, role, loginForm.username)
    const redirectPath = route.query.redirect
    if (role === 'ADMIN') {
      router.replace(redirectPath || '/admin')
      ElMessage.success('管理员登录成功')
    } else {
      router.replace(redirectPath || '/')
      ElMessage.success('登录成功')
    }
  } catch (err) {
    loginError.value = err.message || '登录失败，请重试'
  } finally {
    loginLoading.value = false
  }
}

// ── 注册 ──
const registerFormRef = ref(null)
const registerLoading = ref(false)
const registerError = ref('')
const registerForm = reactive({ username: '', password: '', nickname: '', email: '' })
const registerRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { min: 4, max: 20, message: '4-20个字符', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 20, message: '6-20个字符', trigger: 'blur' }
  ],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [
    { required: true, message: '请输入邮箱', trigger: 'blur' },
    { type: 'email', message: '请输入有效的邮箱地址', trigger: ['blur', 'change'] }
  ]
}

const handleRegister = async () => {
  const valid = await registerFormRef.value?.validate().catch(() => false)
  if (!valid) return
  registerLoading.value = true
  registerError.value = ''
  try {
    await registerApi(registerForm)
    ElMessage.success('注册成功，请登录')
    switchForms()
  } catch (err) {
    registerError.value = err.message || '注册失败，请重试'
  } finally {
    registerLoading.value = false
  }
}
</script>

<style scoped lang="scss">
/* ─── 页面 ─── */
.page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  padding: 24px;
}

.center {
  width: 100%;
  max-width: 400px;
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* ─── 标题 ─── */
.big-text {
  font-family: 'Georgia', 'Noto Serif SC', serif;
  font-size: 3.5rem;
  font-weight: 400;
  letter-spacing: -0.03em;
  line-height: 1;
  color: #111;
  margin: 0 0 12px;
  text-align: center;
}

/* ─── 分隔线 ─── */
.divider {
  width: 40px;
  height: 1px;
  background: #111;
  margin: 32px 0;
  transform: scaleX(0);
  transform-origin: center;
}

/* ─── 表单 ─── */
.form-pane {
  width: 100%;
}

.form {
  .field {
    margin-bottom: 28px;

    label {
      display: block;
      font-size: 0.7rem;
      font-weight: 500;
      color: #222222;
      letter-spacing: 0.08em;
      text-transform: uppercase;
      margin-bottom: 8px;
      font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
    }

    input {
      width: 100%;
      border: none;
      border-bottom: 1px solid #111111;
      padding: 8px 0;
      font-size: 1.1rem;
      color: #111;
      background: transparent;
      outline: none;
      transition: border-color 0.3s;
      font-family: inherit;

      &::placeholder {
        color: #ddd;
      }

      &:focus {
        border-bottom-color: #111;
      }
    }
  }
}

.error-msg {
  font-size: 0.8rem;
  color: #e53e3e;
  margin-bottom: 16px;
  text-align: center;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

.submit-btn {
  width: 100%;
  padding: 14px 0;
  margin-top: 12px;
  background: #111;
  color: #fff;
  border: none;
  font-size: 0.9rem;
  font-weight: 500;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  cursor: pointer;
  transition: all 0.3s;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;

  &:hover:not(:disabled) {
    background: #333;
    letter-spacing: 0.15em;
  }

  &:disabled {
    opacity: 0.5;
    cursor: not-allowed;
  }
}

/* ─── 底部 ─── */
.foot {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 36px;
  font-size: 0.82rem;
  color: #999;
  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
}

.switch-link {
  background: none;
  border: none;
  font-size: 0.82rem;
  color: #111;
  font-weight: 500;
  cursor: pointer;
  padding: 0;
  transition: opacity 0.3s;

  &:hover {
    opacity: 0.6;
  }
}

/* ─── 初始隐藏（用于首屏动画） ─── */
.big-text,
.divider,
.form-pane,
.field,
.submit-btn,
.foot {
  opacity: 0;
}

.big-text {
  transform: translateY(-20px);
}

.form-pane {
  transform: translateY(20px);
}

.field {
  transform: translateY(10px);
}

.submit-btn {
  transform: translateY(10px);
}

/* ─── 移动端 ─── */
@media (max-width: 480px) {
  .center {
    max-width: 100%;
  }

  .big-text {
    font-size: 2.5rem;
  }
}
</style>