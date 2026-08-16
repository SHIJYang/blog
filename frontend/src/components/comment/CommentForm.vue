<template>
  <div :class="['cf', { compact }]">
    <div class="cf-fields" v-if="!compact">
      <div class="cf-field">
        <label class="cf-label">昵称</label>
        <Input v-model="form.nickname" placeholder="你的昵称" class="cf-neo-input" />
      </div>
      <div class="cf-field">
        <label class="cf-label">邮箱</label>
        <Input v-model="form.email" placeholder="选填" class="cf-neo-input" />
      </div>
    </div>

    <div class="cf-field cf-textarea-field">
      <Textarea v-model="form.content" :placeholder="placeholder" :rows="compact ? 2 : 4"
        class="cf-neo-textarea" @focus="focused = true" />
      <span class="cf-count" v-if="focused || compact">
        {{ form.content.length }} / 2000
      </span>
    </div>

    <Transition name="bar">
      <div class="cf-bar" v-if="focused || compact">
        <button v-if="compact" class="cf-btn cancel" @click="cancel">取消</button>
        <button class="cf-btn submit" :disabled="!form.content.trim()" @click="submit">
          <Send :size="12" />
          发表
        </button>
      </div>
    </Transition>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { Send } from 'lucide-vue-next'


const props = defineProps({
  placeholder: { type: String, default: '写下你的评论...' },
  compact: { type: Boolean, default: false },
})

const emit = defineEmits(['submit', 'cancel'])
const focused = ref(false)
const form = reactive({ nickname: '', email: '', content: '' })

function submit() {
  if (!form.content.trim()) return
  emit('submit', {
    nickname: form.nickname.trim(),
    email: form.email.trim(),
    content: form.content.trim(),
  })
  form.content = ''
  if (!props.compact) focused.value = false
}

function cancel() {
  form.content = ''
  focused.value = false
  emit('cancel')
}
</script>

<style lang="scss" scoped>
/* 保留原有的布局、按钮、动画、响应式等样式，只微调输入控件的边距 */
.cf:not(.compact) {
  padding: 28px;
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
}

.cf.compact {
  margin-top: 14px;
}

.cf-fields {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.cf-field {
  display: flex;
  flex-direction: column;
  gap: 6px;
  position: relative;
}

/* 文本域容器需要相对定位，因为计数要绝对定位 */
.cf-textarea-field {
  position: relative;
}

.cf-label {
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  color: var(--text-muted);
}



.cf-count {
  position: absolute;
  bottom: 14px;
  right: 16px;
  font-family: var(--font-mono);
  font-size: 10px;
  color: var(--text-muted);
  opacity: 0.4;
  pointer-events: none;
}

/* 按钮和动画保持原样 */
.cf-bar {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
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
  color: var(--bg);
  background: var(--accent);
  border: 1px solid var(--accent);

  &:hover:not(:disabled) {
    box-shadow: 0 0 20px var(--accent-soft);
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.25;
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
    padding: 20px;
  }
}
</style>