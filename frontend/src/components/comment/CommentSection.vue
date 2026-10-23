<template>
  <section class="comment-section">
    <!-- 标题行 -->
    <div class="cs-header">
      <h2 class="cs-title">评论</h2>
      <span class="cs-count">{{ store.total }}</span>
    </div>

    <!-- 加载中 -->
    <div class="cs-loading" v-if="store.loading">
      <div class="loading-spinner" />
      <span>加载评论中…</span>
    </div>

    <!-- 加载出错 -->
    <div class="cs-error" v-else-if="store.error">
      <p>{{ store.error }}</p>
      <button class="retry-btn" type="button" @click="retry">重试</button>
    </div>

    <!-- 正常状态 -->
    <template v-else>
      <CommentForm
        ref="formRef"
        :loading="submitting"
        placeholder="写下你的想法…"
        @submit="onAdd"
      />

      <div class="cs-list" v-if="store.comments.length">
        <TransitionGroup name="fade">
          <CommentItem v-for="c in store.comments" :key="c.id" :comment="c" @reply="onReply" />
        </TransitionGroup>

        <div class="cs-more-wrap" v-if="store.hasMore">
          <button class="cs-more" type="button" :disabled="store.loadingMore" @click="store.fetchMore()">
            {{ store.loadingMore ? '加载中…' : '加载更多评论' }}
          </button>
        </div>
      </div>

      <div class="cs-empty" v-else>
        <MessageCircle :size="28" :stroke-width="1" />
        <span>还没有评论，来坐沙发吧</span>
      </div>
    </template>
  </section>
</template>

<script setup>
import { ref, watch, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { MessageCircle } from 'lucide-vue-next'
import { useCommentStore } from '@/stores/comment'
import CommentItem from './CommentItem.vue'
import CommentForm from './CommentForm.vue'

const props = defineProps({
  articleId: { type: [Number, String], required: true }
})

const store = useCommentStore()
const formRef = ref(null)
const submitting = ref(false)

watch(
  () => props.articleId,
  (id) => {
    if (id) store.fetchComments(id)
  },
  { immediate: true }
)

onUnmounted(() => {
  store.clearComments()
})

function retry() {
  if (props.articleId) store.fetchComments(props.articleId)
}

/** 发布成功后的提示：能立刻看到就是已发布，看不到说明需要审核 */
function notifyPublished(content) {
  const visible = store.hasCommentWithContent(content)
  ElMessage({
    type: visible ? 'success' : 'info',
    message: visible ? '评论已发布' : '评论已提交，审核通过后展示',
    duration: 2600
  })
}

async function onAdd(data) {
  if (submitting.value) return
  submitting.value = true

  try {
    await store.addComment({
      articleId: props.articleId,
      nickname: data.nickname || '匿名网友',
      email: data.email || '',
      content: data.content
    })
    formRef.value?.clear()
    notifyPublished(data.content)
  } catch (e) {
    // 请求拦截器已提示后端错误信息，这里保留用户输入内容
  } finally {
    submitting.value = false
  }
}

/** 子评论回复：finish(ok) 由 CommentItem 决定是否收起表单 */
async function onReply({ parentId, data, finish }) {
  let ok = false
  try {
    await store.addReply(parentId, {
      articleId: props.articleId,
      nickname: data.nickname || '匿名网友',
      email: data.email || '',
      content: data.content
    })
    notifyPublished(data.content)
    ok = true
  } catch (e) {
    // 保留回复输入内容
  } finally {
    finish?.(ok)
  }
}
</script>

<style lang="scss" scoped>
.comment-section {
  /* 与正文卡片同宽、同一条左边缘，避免错位 */
  width: 100%;
  margin-top: 56px;
}

/* 标题行 */
.cs-header {
  display: flex;
  align-items: baseline;
  justify-content: center;
  gap: 10px;
  margin-bottom: 28px;
}

.cs-title {
  font-size: 1.7rem;
  font-weight: 400;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}

.cs-count {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-muted);
  padding: 2px 10px;
  background: #ffffff;
  border: 1px solid var(--border);
  border-radius: 100px;
}

/* 加载状态 */
.cs-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 56px 0;
  color: var(--text-muted);
  font-size: 0.9rem;
}

.loading-spinner {
  width: 24px;
  height: 24px;
  border: 2px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* 错误状态 */
.cs-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 56px 0;
  color: var(--text-muted);
  font-size: 0.95rem;

  p {
    margin: 0;
  }
}

.retry-btn {
  padding: 6px 16px;
  background: #f3ecd9;
  border: 1px solid var(--accent-medium);
  border-radius: 6px;
  color: var(--accent-strong);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
  transition: 0.2s;

  &:hover {
    background: var(--accent);
    border-color: var(--accent);
    color: #ffffff;
  }
}

/* 评论列表 */
.cs-list {
  margin-top: 44px;
}

.cs-more-wrap {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}

.cs-more {
  padding: 8px 22px;
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-secondary);
  background: #ffffff;
  border: 1px solid var(--border);
  border-radius: 100px;
  transition: color 0.2s, border-color 0.2s, transform 0.2s;

  &:hover:not(:disabled) {
    color: var(--accent-strong);
    border-color: var(--accent-medium);
    transform: translateY(-1px);
  }

  &:disabled {
    opacity: 0.5;
    cursor: default;
  }
}

/* 空状态 */
.cs-empty {
  margin-top: 36px;
  padding: 44px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--text-muted);
  font-size: 0.9rem;
  border: 1px dashed var(--border);
  border-radius: var(--radius-md);
  text-align: center;
  background: rgba(255, 255, 255, 0.5);
}

/* 过渡动画 */
.fade-enter-active {
  transition: all 0.4s ease;
}

.fade-leave-active {
  transition: all 0.3s ease;
}

.fade-enter-from {
  opacity: 0;
  transform: translateY(-12px);
}

.fade-leave-to {
  opacity: 0;
}

/* 响应式 */
@media (max-width: 768px) {
  .comment-section {
    margin-top: 40px;
  }

  .cs-title {
    font-size: 1.45rem;
  }

  .cs-empty {
    padding: 36px 16px;
  }
}
</style>
