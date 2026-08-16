<!--
  记忆功能组件 3：ChatConversation
  - 与当前选中的记忆对话
  - 功能：AI 开场白 + 消息列表 + 文字发送 + 图片发送 + 验证码（10 分钟免验证）
  父层传入 memoryId / cover / title / emotions；对话状态经内存维护，父层只需传 memoryId。
-->
<template>
  <div class="chat-conversation" v-if="memoryId">
    <!-- 当前记忆头 -->
    <div class="chat-header">
      <el-image v-if="cover" :src="cover" fit="cover" class="header-cover" />
      <div class="header-meta">
        <span class="header-title">{{ title || '我的记忆' }}</span>
        <span v-if="emotions" class="header-emotions">情绪：{{ emotions }}</span>
      </div>
    </div>

    <!-- 消息列表 -->
    <div ref="msgBox" class="chat-messages">
      <div v-if="opening" class="msg msg-ai">
        <div class="msg-bubble ai">{{ opening }}</div>
      </div>
      <div v-for="(msg, i) in messages" :key="i" class="msg" :class="msg.role === 'ai' ? 'msg-ai' : 'msg-me'">
        <div class="msg-bubble" :class="msg.role">
          <el-image v-if="msg.type === 3 && msg.imgUrl" :src="msg.imgUrl" fit="cover" style="max-width:180px; border-radius:6px;" :preview-src-list="[msg.imgUrl]" />
          <span v-if="msg.content">{{ msg.content }}</span>
        </div>
      </div>
      <div v-if="chatLoading" class="msg msg-ai">
        <div class="msg-bubble ai typing">拾光正在温柔回应…</div>
      </div>
    </div>

    <!-- 输入区（含验证码与发图） -->
    <div class="chat-input-area">
      <div v-if="showCaptcha" class="captcha-row">
        <img v-if="captchaImage" :src="captchaImage" class="captcha-img" alt="验证码" @click="loadCaptcha" />
        <el-tooltip content="点击验证码刷新" placement="top"><span class="captcha-hint">🔄</span></el-tooltip>
        <el-input ref="captchaInput" v-model="captchaCode" placeholder="验证码" size="small" style="width: 110px" />
      </div>
      <div v-else class="captcha-free-tip">✓ 10 分钟内无需验证码</div>
      <div class="input-row">
        <input ref="chatImgInput" type="file" accept="image/*" style="display:none" @change="handleChatImgChange" />
        <el-button :loading="imgSending" @click="chatImgInput?.click()">发图</el-button>
        <el-input v-model="inputText" placeholder="说点什么，或只是安静待着…" @keyup.enter="sendMessage" />
        <el-button type="primary" :loading="sending" @click="sendMessage">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getMemoryOpening,
  getMemoryDetail,
  getMemoryCaptcha,
  chatSendMemory,
  chatImgMemory
} from '@/api/memory'

const props = defineProps<{
  memoryId: number | null
  cover?: string
  title?: string
  emotions?: string
}>()

const emit = defineEmits<{ (e: 'message-sent'): void }>()

// 当前记忆信息（切换时重置）
const opening = ref('')
const messages = ref<{ role: string; content: string; type?: number; imgUrl?: string }[]>([])
const msgBox = ref<HTMLElement | null>(null)

// 输入态
const inputText = ref('')
const sending = ref(false)
const chatLoading = ref(false)
const chatImgInput = ref<HTMLInputElement | null>(null)
const imgSending = ref(false)

// 验证码（10 分钟免验证）
const captchaImage = ref('')
const captchaKey = ref('')
const captchaCode = ref('')
const CAPTCHA_FREE_KEY = 'memory…ntil'
function isCaptchaFree() { return Date.now() < Number(localStorage.getItem(CAPTCHA_FREE_KEY) || 0) }
function markCaptchaFree() { localStorage.setItem(CAPTCHA_FREE_KEY, String(Date.now() + 10 * 60 * 1000)) }
// showCaptcha 独立控制验证码框是否显示（失败时可强制弹出，即使本地标记免验证）
const showCaptcha = ref(!isCaptchaFree())
const captchaInput = ref<any>(null)
function focusCaptcha() {
  nextTick(() => { try { captchaInput.value?.focus?.() } catch {} })
}

// 切换记忆时：重置对话 + 加载历史消息 + 拉开场白 + 验证码 + 滚动
watch(() => props.memoryId, async (id) => {
  if (id == null) return
  opening.value = ''
  messages.value = []
  // 1) 加载历史对话（memory_message 已入库的记录）
  try {
    const detail = await getMemoryDetail(id)
    const history: any[] = (detail as any)?.messages || []
    history.forEach((m) => {
      // msgType: 1-用户文字 2-AI文字 3-用户图片
      messages.value.push({
        role: m?.msgType === 2 ? 'ai' : 'me',
        type: m?.msgType,
        content: m?.content || (m?.msgType === 3 ? '(图片)' : ''),
        imgUrl: m?.imgUrl || ''
      })
    })
  } catch (e) { /* 历史加载失败不阻断 */ }
  // 2) AI 开场白
  try { const o = await getMemoryOpening(id); opening.value = (o as any)?.opening || '' } catch { opening.value = '' }
  loadCaptcha()
  scrollToBottom()
}, { immediate: true })

async function scrollToBottom() {
  await nextTick()
  msgBox.value?.scrollTo({ top: msgBox.value.scrollHeight, behavior: 'smooth' })
}

// 拉取验证码
async function loadCaptcha() {
  if (!showCaptcha.value && !props.memoryId) return
  try {
    const res = await getMemoryCaptcha()
    captchaKey.value = (res as any)?.key || ''
    captchaImage.value = (res as any)?.image || ''
    captchaCode.value = ''
  } catch { captchaImage.value = ''; captchaKey.value = '' }
}

// 发送文字
async function sendMessage() {
  const content = inputText.value.trim()
  if (!content) return
  if (props.memoryId == null) return

  // 需要验证码但没填：提示 + 聚焦验证码框（不管之前是否标记免验证）
  if (showCaptcha.value && !captchaCode.value.trim()) {
    ElMessage.warning('请先输入验证码')
    if (!captchaImage.value) await loadCaptcha()
    focusCaptcha()
    return
  }

  messages.value.push({ role: 'me', content })
  inputText.value = ''
  sending.value = true
  chatLoading.value = true
  scrollToBottom()
  try {
    const res = await chatSendMemory({
      memoryId: props.memoryId,
      content,
      captchaCode: showCaptcha.value ? captchaCode.value : '',
      captchaKey: showCaptcha.value ? captchaKey.value : ''
    })
    messages.value.push({ role: 'ai', content: (res as any)?.reply || '' })
    // 发送成功：后续 10 分钟免验证，隐藏验证码框
    markCaptchaFree()
    showCaptcha.value = false
    captchaImage.value = ''
    captchaCode.value = ''
    emit('message-sent')
  } catch (e: any) {
    const msg = e?.message || ''
    ElMessage.error(msg || '发送失败')
    // 后端要求验证码（免验证窗口没生效/服务重启等）→ 强制显示验证码框并聚焦
    if (/验证码|captcha|401|Unauthorized/i.test(msg)) {
      localStorage.removeItem(CAPTCHA_FREE_KEY)
      showCaptcha.value = true
      await loadCaptcha()
      focusCaptcha()
    } else if (showCaptcha.value) {
      await loadCaptcha()
    }
  } finally {
    sending.value = false
    chatLoading.value = false
    scrollToBottom()
  }
}

// 发送图片
function handleChatImgChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file && props.memoryId != null) sendImage(file)
  input.value = ''
}

async function sendImage(file: File) {
  imgSending.value = true
  try {
    await chatImgMemory(props.memoryId as number, file)
    messages.value.push({ role: 'me', content: '(发送了一张图片)' })
    ElMessage.success('图片已发送')
    emit('message-sent')
  } catch (e: any) {
    ElMessage.error(e?.message || '图片发送失败')
  } finally {
    imgSending.value = false
  }
}
</script>

<style scoped lang="scss">
.chat-conversation {
  display: flex;
  flex-direction: column;
  height: 100%;

  .chat-header {
    display: flex; gap: 12px; align-items: center; padding: 14px 18px; border-bottom: 1px solid #f1f5f9;
    .header-cover { width: 48px; height: 48px; border-radius: 8px; }
    .header-meta {
      display: flex; flex-direction: column;
      .header-title { font-size: 1rem; font-weight: 600; color: #1e293b; }
      .header-emotions { font-size: 0.75rem; color: #2563eb; margin-top: 2px; }
    }
  }

  .chat-messages { flex: 1; overflow-y: auto; padding: 18px; min-height: 360px; max-height: 52vh; }

  .chat-input-area { border-top: 1px solid #f1f5f9; padding: 12px 18px;
    .captcha-row { display: flex; align-items: center; gap: 8px; margin-bottom: 8px;
      .captcha-img { width: 110px; height: 36px; border-radius: 4px; border: 1px solid #e2e8f0; cursor: pointer; object-fit: cover; }
      .captcha-hint { font-size: 1rem; cursor: pointer; }
    }
    .captcha-free-tip { font-size: 0.8rem; color: #16a34a; margin-bottom: 8px; }
    .input-row { display: flex; gap: 8px; }
  }
}

.msg { display: flex; margin-bottom: 12px;
  &.msg-me { justify-content: flex-end; }
  .msg-bubble {
    max-width: 78%; padding: 10px 14px; border-radius: 14px; font-size: 0.9rem;
    line-height: 1.55; white-space: pre-wrap; word-break: break-word;
    &.me { background: #2563eb; color: #fff; border-bottom-right-radius: 4px; }
    &.ai { background: #f1f5f9; color: #334155; border-bottom-left-radius: 4px; }
    &.typing { color: #94a3b8; }
  }
}
</style>
