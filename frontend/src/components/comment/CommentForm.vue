<template>
  <div :class="['cf', { compact }]">
    <div class="cf-fields" v-if="!compact">
      <div class="cf-field">
        <label class="cf-label" for="cf-nickname">昵称</label>
        <input
          id="cf-nickname"
          v-model="form.nickname"
          class="cf-input"
          type="text"
          maxlength="24"
          autocomplete="nickname"
          placeholder="你的昵称（选填）"
        />
      </div>
      <div class="cf-field">
        <label class="cf-label" for="cf-email">邮箱</label>
        <input
          id="cf-email"
          v-model="form.email"
          class="cf-input"
          :class="{ 'has-error': emailError }"
          type="email"
          maxlength="64"
          autocomplete="email"
          placeholder="选填，不会公开"
        />
        <span v-if="emailError" class="cf-error">{{ emailError }}</span>
      </div>
    </div>

    <div class="cf-field cf-textarea-field">
      <textarea
        v-model="form.content"
        class="cf-textarea"
        :rows="compact ? 2 : 4"
        maxlength="2000"
        :placeholder="placeholder"
        @focus="focused = true"
        @blur="focused = false"
        @keydown.ctrl.enter.prevent="submit"
        @keydown.meta.enter.prevent="submit"
      ></textarea>
      <span class="cf-count" v-if="focused || form.content.length">{{ form.content.length }} / 2000</span>
    </div>

    <Transition name="bar">
      <div class="cf-bar" v-if="focused || compact || form.content.length">
        <span class="cf-hint">Ctrl / ⌘ + Enter 快速发表</span>
        <button v-if="compact" type="button" class="cf-btn cancel" @click="cancel">取消</button>
        <button type="button" class="cf-btn submit" :disabled="!canSubmit" @click="submit">
          <Send :size="12" />
          {{ loading ? '发送中…' : '发表' }}
        </button>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, reactive, computed } from 'vue'
import { Send } from 'lucide-vue-next'

const props = defineProps({
  placeholder: { type: String, default: '写下你的评论...' },
  compact: { type: Boolean, default: false },
  loading: { type: Boolean, default: false },
})

const emit = defineEmits(['submit', 'cancel'])
const focused = ref(false)
const form = reactive({ nickname: '', email: '', content: '' })

const EMAIL_RE = /^[^\s@]+@[^\s@]+\.[^\s@]+$/

const emailError = computed(() => {
  const v = form.email.trim()
  if (!v) return ''
  return EMAIL_RE.test(v) ? '' : '邮箱格式不正确'
})

const canSubmit = computed(
  () => !!form.content.trim() && !emailError.value && !props.loading
)

function submit() {
  if (!canSubmit.value) return
  emit('submit', {
    nickname: form.nickname.trim(),
    email: form.email.trim(),
    content: form.content.trim(),
  })
}

/** 由父组件在提交成功后调用，避免请求失败时把用户写的内容清空 */
function clear() {
  form.content = ''
  focused.value = false
  // 输入框仍聚焦时 Vue 会跳过一次 value 同步（保护光标），这里主动失焦
  const el = document.activeElement
  if (el && el.classList && el.classList.contains('cf-textarea')) el.blur()
}

function cancel() {
  form.content = ''
  focused.value = false
  emit('cancel')
}

defineExpose({ clear })
</script>

<style lang="scss" scoped>
.cf:not(.compact) {
  padding: 24px;
  background: #ffffff;
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  box-shadow: 0 1px 2px rgba(60, 45, 15, 0.03);
}

.cf.compact {
  margin-top: 14px;
}

.cf-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 14px;
}

.cf-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  position: relative;
}

.cf-textarea-field {
  position: relative;
}

.cf-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--text-muted);
}

.cf-input,
.cf-textarea {
  width: 100%;
  font-family: inherit;
  font-size: 0.9rem;
  line-height: 1.7;
  color: var(--text-primary);
  background: #fbf9f5;
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  transition: background 0.2s, border-color 0.2s, box-shadow 0.2s;
  outline: none;

  &::placeholder {
    color: var(--text-muted);
  }

  &:hover {
    border-color: #ded8cb;
  }

  &:focus {
    background: #ffffff;
    border-color: var(--accent-medium);
    box-shadow: 0 0 0 3px var(--accent-soft);
  }
}

.cf-textarea {
  display: block;
  min-height: 84px;
  resize: vertical;

  .compact & {
    min-height: 64px;
  }
}

.cf-input.has-error {
  border-color: #d98b7a;
}

.cf-error {
  font-family: var(--font-mono);
  font-size: 10px;
  color: #c0563f;
}

.cf-count {
  position: absolute;
  bottom: 10px;
  right: 14px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-muted);
  opacity: 0.65;
  pointer-events: none;
}

.cf-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}

.cf-hint {
  margin-right: auto;
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.04em;
  color: var(--text-muted);
  opacity: 0;
  transition: opacity 0.2s;
}

.cf-bar:hover .cf-hint {
  opacity: 0.8;
}

.cf-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 7px 18px;
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  border-radius: 100px;
  transition: all 0.25s;
  border: none;
  cursor: pointer;
}

.cf-btn.cancel {
  color: var(--text-muted);
  background: transparent;
  border: 1px solid var(--border);

  &:hover {
    color: var(--text-secondary);
    border-color: var(--text-muted);
  }
}

.cf-btn.submit {
  color: #ffffff;
  background: var(--accent);
  border: 1px solid var(--accent);

  &:hover:not(:disabled) {
    background: #a97d1f;
    border-color: #a97d1f;
    box-shadow: 0 6px 16px rgba(184, 134, 11, 0.22);
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.35;
    cursor: not-allowed;
  }
}

.bar-enter-active {
  transition: all 0.3s ease;
}

.bar-leave-active {
  transition: all 0.2s ease;
}

.bar-enter-from,
.bar-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 640px) {
  .cf-fields {
    grid-template-columns: 1fr;
  }

  .cf:not(.compact) {
    padding: 18px;
  }

  .cf-hint {
    display: none;
  }
}
</style>
