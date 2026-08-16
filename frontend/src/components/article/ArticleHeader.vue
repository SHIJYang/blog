<template>
  <div class="article-header">
    <h1 class="header-title">{{ article.title }}</h1>

    <div class="header-divider"></div>

    <div class="header-meta">
      <div class="meta-left">
        <span class="meta-avatar">
          {{ article.authorName?.charAt(0)?.toUpperCase() }}
        </span>
        <div class="meta-author-info">
          <span class="meta-name">{{ article.authorName }}</span>
          <span class="meta-date">{{ formatDate(article.createTime) }}</span>
        </div>
      </div>
      <div class="meta-right">
        <span class="meta-stat">
          <Clock :size="13" />
          {{ estimateReadTime(article.content) }} 分钟
        </span>
        <span class="meta-stat">
          <Eye :size="13" />
          {{ article.views }}
        </span>
      </div>
    </div>
  </div>
</template>

<script setup>
import { Clock, Eye } from 'lucide-vue-next'

defineProps({
  article: {
    type: Object,
    required: true
  }
})

function formatDate(d) {
  if (!d) return ''
  return new Date(d).toLocaleDateString('zh-CN', {
    year: 'numeric',
    month: 'long',
    day: 'numeric'
  })
}

function estimateReadTime(html) {
  if (!html) return 1
  const text = html.replace(/<[^>]*>/g, '')
  return Math.max(1, Math.ceil(text.length / 400))
}
</script>

<style lang="scss" scoped>
.article-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  /* 整体水平居中 */
  text-align: center;
  /* 文本居中 */
  width: 100%;
}

/* 标题 */
.header-title {
  font-family: var(--font-display, serif);
  font-size: clamp(2.4rem, 5vw, 3.6rem);
  font-weight: 500;
  line-height: 1.15;
  color: var(--text-primary);
  letter-spacing: -0.02em;
  margin-bottom: 16px;
  max-width: 800px;
  /* 避免标题过长时铺满整个屏幕 */
}

/* 分割线 */
.header-divider {
  width: 40px;
  height: 2px;
  background: var(--accent);
  border-radius: 1px;
  margin: 0 auto 24px;
  /* 居中并留出底部间距 */
  opacity: 0.5;
}

/* 元信息行：左右分布，整体居中 */
.header-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  flex-wrap: wrap;
  gap: 16px;
  width: 100%;
  max-width: 620px;
  /* 与摘要宽度匹配，保持视觉统一 */
  margin-bottom: 0;
}

.meta-left {
  display: flex;
  align-items: center;
  gap: 10px;
  text-align: left;
  /* 作者名和日期左对齐，更易读 */
}

.meta-avatar {
  width: 36px;
  height: 36px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--font-display, serif);
  font-size: 15px;
  font-weight: 500;
  color: var(--accent);
  background: var(--accent-soft);
  border-radius: 50%;
  flex-shrink: 0;
}

.meta-author-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.meta-name {
  font-family: var(--font-mono, monospace);
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.meta-date {
  font-family: var(--font-mono, monospace);
  font-size: 11px;
  color: var(--text-muted);
}

.meta-right {
  display: flex;
  gap: 16px;
}

.meta-stat {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  font-family: var(--font-mono, monospace);
  font-size: 12px;
  color: var(--text-muted);
}

/* 响应式：小屏幕下元信息改为上下堆叠，整体仍居中 */
@media (max-width: 640px) {
  .header-meta {
    flex-direction: column;
    align-items: center;
    /* 堆叠时居中 */
    gap: 12px;
  }

  .meta-left {
    text-align: center;
    flex-direction: column;
  }

  .meta-author-info {
    align-items: center;
  }

  .meta-avatar {
    width: 30px;
    height: 30px;
    font-size: 13px;
  }

  .header-title {
    font-size: clamp(2rem, 7vw, 2.8rem);
  }
}
</style>