<template>
  <section class="comment-section">
    <!-- 标题行：居中 -->
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
      <button class="retry-btn" @click="retry">重试</button>
    </div>

    <!-- 正常状态 -->
    <template v-else>
      <!-- 表单提前到列表上方 -->
      <CommentForm placeholder="写下你的想法..." @submit="onAdd" />

      <div class="cs-list" v-if="store.comments.length">
        <TransitionGroup name="fade">
          <CommentItem v-for="c in store.comments" :key="c.id" :comment="c" @reply="onReply" />
        </TransitionGroup>
      </div>

      <div class="cs-empty" v-else>
        <MessageCircle :size="28" :stroke-width="1" />
        <span>还没有评论，来坐沙发吧</span>
      </div>
    </template>
  </section>
</template>

<script setup>
import { watch, onUnmounted } from 'vue'
import { MessageCircle } from 'lucide-vue-next'
import { useCommentStore } from '@/stores/comment'
import CommentItem from './CommentItem.vue'
import CommentForm from './CommentForm.vue'

const props = defineProps({
  articleId: { type: [Number, String], required: true }
})

const store = useCommentStore()

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

async function onAdd(data) {
  await store.addComment({
    articleId: props.articleId,
    content: data.content,
    nickname: data.nickname,
    email: data.email,
    website: data.website
  })
}

async function onReply({ parentId, data }) {
  await store.addReply(parentId, {
    articleId: props.articleId,
    content: data.content,
    nickname: data.nickname,
    email: data.email,
    website: data.website
  })
}
</script>

<style lang="scss" scoped>
.comment-section {
  margin-top: 80px;
  max-width: 720px;
  /* 限制宽度，居中阅读 */
  margin-left: auto;
  margin-right: auto;
}

/* 标题行：居中 */
.cs-header {
  display: flex;
  align-items: baseline;
  justify-content: center;
  /* 居中 */
  gap: 10px;
  margin-bottom: 32px;
}

.cs-title {
  font-family: var(--font-display);
  font-size: 1.8rem;
  font-weight: 400;
  color: var(--text-primary);
  letter-spacing: -0.02em;
}

.cs-count {
  font-family: var(--font-mono);
  font-size: 12px;
  color: var(--text-muted);
  padding: 2px 10px;
  background: var(--surface);
  border-radius: 100px;
}

/* 加载状态：居中 */
.cs-loading {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  padding: 60px 0;
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

/* 错误状态：居中 */
.cs-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 60px 0;
  color: var(--text-muted);
  font-size: 0.95rem;

  p {
    margin: 0;
  }
}

.retry-btn {
  padding: 6px 16px;
  background: var(--accent-soft);
  border: 1px solid var(--accent);
  border-radius: 6px;
  color: var(--accent);
  font-family: var(--font-mono);
  font-size: 12px;
  cursor: pointer;
  transition: 0.2s;

  &:hover {
    background: var(--accent);
    color: #fff;
  }
}

/* 评论列表 */
.cs-list {
  margin-top: 48px;
}

/* 空状态：居中 */
.cs-empty {
  margin-top: 40px;
  padding: 48px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12px;
  color: var(--text-muted);
  font-size: 0.9rem;
  border: 1px dashed var(--border);
  border-radius: var(--radius-md);
  text-align: center;
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
    max-width: 100%;
    padding: 0 4px;
  }

  .cs-title {
    font-size: 1.5rem;
  }

  .cs-empty {
    padding: 40px 16px;
  }
}
</style>