<template>
  <div :class="['ci', { reply: depth > 0 }]">
    <div class="ci-main">
      <div class="ci-avatar">
        <!-- 后端返回了 avatar 字段，但可能为 null，这里用昵称首字母作为 fallback -->
        {{ comment.avatar ? '' : (comment.nickname?.charAt(0)?.toUpperCase() || '?') }}
        <img v-if="comment.avatar" :src="comment.avatar" alt="" class="avatar-img" />
      </div>
      <div class="ci-body">
        <div class="ci-head">
          <span class="ci-author">
            <a v-if="comment.website" :href="comment.website" target="_blank"
              rel="noopener nofollow noreferrer">{{ comment.nickname || '匿名网友' }}</a>
            <template v-else>{{ comment.nickname || '匿名网友' }}</template>
          </span>
          <span class="ci-time">{{ relativeTime(comment.createTime) }}</span>
        </div>
        <p class="ci-text">{{ comment.content }}</p>
        <div class="ci-actions">
          <!-- 点赞为临时前端状态，后续可接入后端接口 -->
          <button :class="['ci-btn', { liked }]" @click="liked = !liked">
            <Heart :size="12" :fill="liked ? 'currentColor' : 'none'" />
            {{ (comment.likes || 0) + (liked ? 1 : 0) }}
          </button>
          <button class="ci-btn" @click="showReply = !showReply">
            <ReplyIcon :size="12" />
            回复
          </button>
        </div>

        <!-- 内联回复表单 -->
        <Transition name="rf">
          <CommentForm v-if="showReply" :placeholder="`回复 ${comment.nickname || '匿名网友'}…`" compact
            :loading="replying" @submit="onReplySubmit" @cancel="showReply = false" />
        </Transition>
      </div>
    </div>

    <!-- 子回复：字段改为 replies -->
    <div class="ci-children" v-if="replies.length">
      <CommentItem v-for="child in replies" :key="child.id" :comment="child" :depth="depth + 1"
        @reply="p => emit('reply', p)" />
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Heart, Reply as ReplyIcon } from 'lucide-vue-next'
import CommentForm from './CommentForm.vue'

const props = defineProps({
  comment: { type: Object, required: true },
  depth: { type: Number, default: 0 }
})

const emit = defineEmits(['reply'])
const liked = ref(false)
const showReply = ref(false)
const replying = ref(false)

/** 后端可能用 replies / children 任一种字段承载子评论 */
const replies = computed(() => props.comment.replies || props.comment.children || [])

function onReplySubmit(data) {
  replying.value = true
  // 把父评论 id、表单数据和一个收尾回调交给父组件，提交成功才收起表单
  emit('reply', {
    parentId: props.comment.id,
    data,
    finish: (ok) => {
      replying.value = false
      if (ok) showReply.value = false
    }
  })
}

function relativeTime(d) {
  if (!d) return ''
  const time = new Date(d).getTime()
  if (Number.isNaN(time)) return ''
  const diff = Date.now() - time
  const m = Math.floor(diff / 60000)
  if (diff < 60000) return '刚刚'
  if (m < 60) return `${m} 分钟前`
  const h = Math.floor(m / 60)
  if (h < 24) return `${h} 小时前`
  const day = Math.floor(h / 24)
  if (day < 30) return `${day} 天前`
  return new Date(d).toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}
</script>

<style lang="scss" scoped>
.ci {
  &.reply {
    margin-left: 48px;
    padding-top: 20px;
    border-top: 1px solid var(--border-subtle);
  }

  &:not(.reply)+&:not(.reply) {
    padding-top: 28px;
    border-top: 1px solid var(--border);
    margin-top: 4px;
  }
}

.ci-main {
  display: flex;
  gap: 14px;
}

.ci-avatar {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display);
  font-size: 14px;
  color: var(--text-muted);
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 50%;
  overflow: hidden;

  .avatar-img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }
}

.ci-body {
  flex: 1;
  min-width: 0;
}

.ci-head {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.ci-author {
  font-family: var(--font-mono);
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);

  a {
    color: inherit;
    border-bottom: 1px solid transparent;
    transition: color 0.2s, border-color 0.2s;

    &:hover {
      color: var(--accent-strong);
      border-color: var(--accent-medium);
    }
  }
}

.ci-time {
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
}

.ci-text {
  font-size: 0.92rem;
  line-height: 1.75;
  color: var(--text-secondary);
  word-break: break-word;
  white-space: pre-wrap;
}

.ci-actions {
  display: flex;
  gap: 2px;
  margin-top: 8px;
}

.ci-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 3px 10px;
  background: transparent;
  font-family: var(--font-mono);
  font-size: 11px;
  color: var(--text-muted);
  border-radius: 100px;
  transition: all 0.2s;

  &:hover {
    color: var(--text-secondary);
    background: var(--surface);
  }

  &.liked {
    color: var(--danger);
  }
}

.ci-children {
  margin-top: 4px;
}

/* 表单动画 */
.rf-enter-active {
  transition: all 0.3s ease;
}

.rf-leave-active {
  transition: all 0.2s ease;
}

.rf-enter-from,
.rf-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

@media (max-width: 640px) {
  .ci.reply {
    margin-left: 24px;
  }

  .ci-avatar {
    width: 30px;
    height: 30px;
    font-size: 12px;
  }
}
</style>