<template>
  <article
    ref="rootRef"
    class="blog-card"
    :class="{ 'blog-card--ready': ready }"
    @click="handleClick"
  >
    <!-- 封面 -->
    <div class="blog-card__media">
      <img
        v-if="item.coverImage"
        :src="item.coverImage"
        :alt="item.title"
        class="blog-card__img"
        loading="lazy"
      />
      <div v-else class="blog-card__placeholder" :style="{ background: gradientBg }">
        <span class="blog-card__placeholder-text">{{ categoryName }}</span>
      </div>

      <!-- 分类角标 -->
      <span v-if="categoryName" class="blog-card__chip">{{ categoryName }}</span>

      <!-- hover 阅读箭头 -->
      <span class="blog-card__go">
        <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round">
          <path d="M7 17L17 7M17 7H7M17 7v10" />
        </svg>
      </span>
    </div>

    <!-- 正文 -->
    <div class="blog-card__body">
      <header class="blog-card__head">
        <span class="blog-card__index">{{ indexLabel }}</span>
        <time class="blog-card__date">{{ dateText }}</time>
      </header>

      <h3 class="blog-card__title">{{ item.title }}</h3>

      <p v-if="item.summary" class="blog-card__summary">{{ item.summary }}</p>

      <footer class="blog-card__meta">
        <span class="blog-card__author">{{ item.authorName || '佚名' }}</span>
        <span class="blog-card__dot" aria-hidden="true"></span>
        <span class="blog-card__views">
          <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" stroke-width="1.8"
            stroke-linecap="round" stroke-linejoin="round">
            <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z" />
            <circle cx="12" cy="12" r="3" />
          </svg>
          {{ item.views || 0 }}
        </span>
      </footer>
    </div>
  </article>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import gsap from 'gsap'

/**
 * BlogListCard —— 博客文章卡片（独立组件，浅色主题）
 * - 数据由父组件通过 props 注入
 * - 入场动画自包含：挂载后播放，delay 由父组件控制交错
 * - hover 动效纯 CSS（动画结束后清除 GSAP inline 样式，避免覆盖）
 * - 组件自带浅色设计变量，不依赖全局暗色主题
 */
const props = defineProps({
  /** 文章对象：{ id, title, summary, coverImage, createTime, views, authorName, categories[] } */
  item: { type: Object, required: true },
  /** 全局序号（从 1 开始），用于渲染 01 / 02 样式 */
  index: { type: Number, default: 0 },
  /** 序号补零宽度，默认 2 位 */
  pad: { type: Number, default: 2 },
  /** 封面缺失时的渐变背景 */
  gradient: { type: String, default: 'linear-gradient(135deg, #e7e2d6, #d8d0bd)' },
  /** 入场动画延迟（秒），由父组件用于 stagger */
  delay: { type: Number, default: 0 },
  /** 是否禁用点击跳转（如仅作展示） */
  disabled: { type: Boolean, default: false }
})

const emit = defineEmits(['click'])

const rootRef = ref(null)
const ready = ref(false)

/** 序号文本：01 / 02 … */
const indexLabel = computed(() =>
  props.index > 0 ? String(props.index).padStart(props.pad, '0') : ''
)

/** 首个分类名（角标 + 占位图文字共用） */
const categoryName = computed(() => props.item.categories?.[0]?.name || '')

/** 封面占位渐变背景（优先使用 props 传入的渐变） */
const gradientBg = computed(() => props.gradient)

/** 日期格式化：2026-08-03 → Aug 03 */
const dateText = computed(() => {
  const s = props.item.createTime
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return ''
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  return `${months[d.getMonth()]} ${String(d.getDate()).padStart(2, '0')}`
})

/** 点击：禁用时拦截，否则抛出事件（默认由父组件跳转） */
function handleClick() {
  if (props.disabled) return
  emit('click', props.item)
}

/** 入场动画：y 上移 + 淡入，完成后清除 inline 样式交还 CSS */
onMounted(() => {
  const el = rootRef.value
  if (!el) return
  gsap.fromTo(
    el,
    { y: 36, opacity: 0 },
    {
      y: 0,
      opacity: 1,
      duration: 0.7,
      ease: 'power3.out',
      delay: props.delay,
      onComplete: () => {
        ready.value = true
        gsap.set(el, { clearProps: 'transform,opacity' })
      }
    }
  )
})
</script>

<style scoped lang="scss">
/* ============ 浅色主题变量（组件自包含） ============ */
.blog-card {
  --card-bg: #ffffff;
  --card-bg-hover: #ffffff;
  --card-line: #e9e6e0;
  --card-accent: #b8860b;          /* 深金：浅色底上对比度更佳 */
  --card-accent-soft: #f5efe0;     /* 浅金底 */
  --card-text: #1f1e1b;
  --card-text-secondary: #6b675f;
  --card-text-muted: #a09b91;
  --card-shadow: 0 1px 2px rgba(0, 0, 0, 0.04), 0 4px 12px rgba(0, 0, 0, 0.03);
  --card-shadow-hover: 0 0 0 1px var(--card-accent), 0 16px 40px rgba(120, 90, 20, 0.12);

  position: relative;
  display: flex;
  flex-direction: column;
  background: var(--card-bg);
  border: 1px solid var(--card-line);
  border-radius: 14px;
  overflow: hidden;
  cursor: pointer;
  box-shadow: var(--card-shadow);
  opacity: 0;
  transform: translateY(36px);
  transition:
    border-color 0.35s ease,
    transform 0.4s cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 0.4s ease;

  &:hover {
    border-color: var(--card-accent);
    box-shadow: var(--card-shadow-hover);
    transform: translateY(-4px);
  }
}

/* 入场动画完成后落位（同时接管样式，保证 hover transform 生效） */
.blog-card--ready {
  opacity: 1;
  transform: none;
}

/* ---------- 封面 ---------- */
.blog-card__media {
  position: relative;
  aspect-ratio: 16 / 9;
  overflow: hidden;
  background: #f0ede6;
}

.blog-card__img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition: transform 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}

.blog-card:hover .blog-card__img {
  transform: scale(1.05);
}

.blog-card__placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.6s cubic-bezier(0.22, 1, 0.36, 1);
}

.blog-card:hover .blog-card__placeholder {
  transform: scale(1.05);
}

.blog-card__placeholder-text {
  font-family: var(--font-mono, monospace);
  font-size: 0.72rem;
  letter-spacing: 0.3em;
  text-transform: uppercase;
  color: rgba(40, 38, 33, 0.35);
  user-select: none;
}

/* 分类角标 */
.blog-card__chip {
  position: absolute;
  top: 14px;
  left: 14px;
  z-index: 2;
  padding: 4px 12px;
  font-family: var(--font-mono, monospace);
  font-size: 0.68rem;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: #3a3730;
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 999px;
  transition: background 0.3s ease, color 0.3s ease, border-color 0.3s ease;
}

.blog-card:hover .blog-card__chip {
  background: var(--card-accent);
  border-color: var(--card-accent);
  color: #fff;
}

/* 阅读箭头（hover 出现） */
.blog-card__go {
  position: absolute;
  right: 14px;
  bottom: 14px;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34px;
  height: 34px;
  border-radius: 50%;
  background: var(--card-accent);
  color: #fff;
  opacity: 0;
  transform: translateY(8px);
  transition: opacity 0.3s ease, transform 0.3s ease;
}

.blog-card:hover .blog-card__go {
  opacity: 1;
  transform: translateY(0);
}

/* ---------- 正文 ---------- */
.blog-card__body {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px 20px 16px;
}

.blog-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.blog-card__index {
  font-family: var(--font-mono, monospace);
  font-size: 0.72rem;
  letter-spacing: 0.15em;
  color: var(--card-text-muted);
}

.blog-card__date {
  font-family: var(--font-mono, monospace);
  font-size: 0.72rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--card-text-muted);
  white-space: nowrap;
}

.blog-card__title {
  font-family: var(--font-display, Georgia, serif);
  font-size: 1.18rem;
  font-weight: 600;
  line-height: 1.4;
  color: var(--card-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  transition: color 0.3s ease;
}

.blog-card:hover .blog-card__title {
  color: var(--card-accent);
}

.blog-card__summary {
  margin: 0;
  font-size: 0.86rem;
  line-height: 1.65;
  color: var(--card-text-secondary);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.blog-card__meta {
  margin-top: auto;
  padding-top: 12px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-family: var(--font-mono, monospace);
  font-size: 0.72rem;
  color: var(--card-text-muted);
  border-top: 1px solid #f0eee9;
}

.blog-card__author {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.blog-card__dot {
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #d8d3c8;
  flex-shrink: 0;
}

.blog-card__views {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
}
</style>
