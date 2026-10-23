<template>
  <div class="article-view">
    <!-- 顶部阅读进度条 -->
    <div class="read-progress" aria-hidden="true">
      <div class="read-progress-bar" :style="{ transform: `scaleX(${progress})` }"></div>
    </div>

    <!-- 悬浮返回按钮（毛玻璃胶囊） -->
    <button type="button" class="float-back" aria-label="返回博客" @click="goBack">
      <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" stroke-width="2"
        stroke-linecap="round" stroke-linejoin="round">
        <path d="M19 12H5M12 19l-7-7 7-7" />
      </svg>
      <span class="back-label">返回博客</span>
    </button>

    <!-- 加载态 -->
    <div class="article-loading" v-if="loading">
      <div class="loader">
        <span class="loader-ring"></span>
        <span class="loader-text">正在加载文章</span>
      </div>
    </div>

    <template v-else-if="currentArticle">
      <!-- Hero -->
      <header class="article-hero" ref="heroRef">
        <div class="container hero-inner">
          <!-- 分类 / 标签 -->
          <div class="hero-chips" v-if="categoryNames.length || tagNames.length">
            <span v-for="c in categoryNames" :key="`c-${c}`" class="category-chip">{{ c }}</span>
            <span v-for="t in tagNames" :key="`t-${t}`" class="tag-chip">#{{ t }}</span>
          </div>

          <ArticleHeader :article="currentArticle" />
        </div>

        <!-- 封面（可选） -->
        <div class="container" v-if="currentArticle.coverImage">
          <div class="hero-cover">
            <img :src="currentArticle.coverImage" :alt="currentArticle.title" loading="lazy" />
          </div>
        </div>
      </header>

      <!-- 正文 -->
      <div class="container">
        <div class="article-body" :class="{ 'no-toc': !hasToc }">
          <main class="article-main" ref="mainRef">
            <!-- 纸张卡片 -->
            <div class="paper">
              <ArticleContent :content="renderedContent" />

              <!-- 文末标记 -->
              <div class="article-end">
                <span class="end-line"></span>
                <span class="end-mark">END</span>
                <span class="end-line"></span>
              </div>
            </div>

            <CommentSection v-if="allowComment" :article-id="currentArticle.id" />
          </main>

          <aside class="article-toc" v-if="hasToc">
            <div class="toc-sticky">
              <TableOfContents :content="renderedContent" />
            </div>
          </aside>
        </div>
      </div>
    </template>

    <!-- 空态 -->
    <div class="article-empty" v-else>
      <FileQuestion :size="44" :stroke-width="1" />
      <p class="empty-title">文章不存在</p>
      <p class="empty-sub">它可能已被删除或移动到了别处</p>
      <router-link to="/" class="empty-link">返回首页</router-link>
    </div>

    <!-- 回到顶部 -->
    <Transition name="pop">
      <button v-show="showTop" type="button" class="to-top" aria-label="回到顶部" @click="scrollToTop">
        <ArrowUp :size="18" />
      </button>
    </Transition>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { gsap } from 'gsap'
import { ArrowUp, FileQuestion } from 'lucide-vue-next'
import { useArticleStore } from '@/stores/article'
import { renderArticleContent } from '@/utils/markdown'

import ArticleHeader from '@/components/article/ArticleHeader.vue'
import ArticleContent from '@/components/article/ArticleContent.vue'
import TableOfContents from '@/components/article/TableOfContents.vue'
import CommentSection from '@/components/comment/CommentSection.vue'

const route = useRoute()
const router = useRouter()
const articleStore = useArticleStore()
const { currentArticle } = storeToRefs(articleStore)

const loading = ref(true)
const heroRef = ref(null)
const mainRef = ref(null)
const progress = ref(0)
const showTop = ref(false)
let revealObserver = null

const categoryNames = computed(() =>
  (currentArticle.value?.categories || []).map(c => c?.name).filter(Boolean)
)
const tagNames = computed(() =>
  (currentArticle.value?.tags || []).map(t => t?.name).filter(Boolean)
)

// 后端返回的可能是压平/转义过的 markdown，统一还原为 HTML；
// 正文开头的一级标题与页面标题重复，直接去掉
const allowComment = computed(() => Number(currentArticle.value?.allowComment ?? 0) === 1)

const renderedContent = computed(() => {
  const html = renderArticleContent(currentArticle.value?.content || '')
  return html.replace(/^\s*<h1[\s\S]*?<\/h1>\s*/, '')
})

// 目录只解析 h1~h3；没有标题时折叠侧栏，正文单列居中
const hasToc = computed(() => /<h[1-3][\s>]/.test(renderedContent.value))

onMounted(async () => {
  window.addEventListener('scroll', onScroll, { passive: true })

  try {
    await articleStore.fetchPublicArticleDetail(route.params.id)
  } catch (e) {
    console.error(e)
  }

  loading.value = false
  await nextTick()
  onScroll()
  requestAnimationFrame(() => {
    if (prefersReducedMotion()) return
    runAnimations()
    setupReveal()
  })
})

const prefersReducedMotion = () =>
  window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false

onUnmounted(() => {
  articleStore.clearCurrentArticle()
  window.removeEventListener('scroll', onScroll)
  revealObserver?.disconnect()
})

// 同步浏览器标题
watch(currentArticle, (a) => {
  if (a?.title) document.title = `${a.title} · 博客`
})

function goBack() {
  router.push('/')
}

function scrollToTop() {
  window.scrollTo({ top: 0, behavior: 'smooth' })
}

function onScroll() {
  const s = window.scrollY
  const max = document.documentElement.scrollHeight - window.innerHeight
  progress.value = max > 0 ? Math.min(1, Math.max(0, s / max)) : 0
  showTop.value = s > 600
}

function runAnimations() {
  const tl = gsap.timeline({
    defaults: { ease: 'power4.out', duration: 0.9 },
  })

  // header 子元素
  const header = heroRef.value
  if (header) {
    const chips = header.querySelectorAll('.category-chip, .tag-chip')
    const title = header.querySelector('.header-title')
    const divider = header.querySelector('.header-divider')
    const meta = header.querySelector('.header-meta')
    const cover = header.querySelector('.hero-cover')

    if (chips.length) tl.from(chips, { y: 16, opacity: 0, stagger: 0.06, duration: 0.5 }, 0)
    if (title) tl.from(title, { y: 50, opacity: 0 }, 0.1)
    if (divider) tl.from(divider, { scaleX: 0, opacity: 0, duration: 0.6 }, 0.25)
    if (meta) tl.from(meta, { y: 15, opacity: 0, duration: 0.5 }, '-=0.5')
    if (cover) tl.from(cover, { y: 40, opacity: 0, duration: 0.9 }, '-=0.6')
  }

  // 主内容
  if (mainRef.value) {
    tl.from(mainRef.value, { y: 80, opacity: 0, duration: 1 }, '-=0.5')
  }

  // 目录
  const toc = document.querySelector('.article-toc')
  if (toc) {
    tl.from(toc, { x: 30, opacity: 0, duration: 0.7 }, '-=0.7')
  }
}

// 正文滚动显现：逐段淡入上移
function setupReveal() {
  const root = mainRef.value
  if (!root) return

  const prose = root.querySelector('.prose')
  const targets = root.querySelectorAll('.prose > *, .article-end')
  if (!prose || !targets.length || !('IntersectionObserver' in window)) return

  // 先加类再观察，避免 JS 异常时正文被隐藏
  root.classList.add('reveal-on')
  prose.classList.add('reveal-ready')

  revealObserver = new IntersectionObserver(
    entries => {
      entries.forEach(e => {
        if (e.isIntersecting) {
          e.target.classList.add('is-revealed')
          revealObserver.unobserve(e.target)
        }
      })
    },
    { threshold: 0, rootMargin: '0px 0px -6% 0px' }
  )
  targets.forEach(el => revealObserver.observe(el))
}
</script>

<style lang="scss" scoped>
.article-view {
  overflow-x: clip;
  /* ============ 浅色主题变量（与 blog.vue / BlogListCard 同一套色板） ============ */
  --bg: #f8f6f1;
  --bg-elevated: #ffffff;
  --surface: #f4f0e7;
  --surface-hover: #efeade;
  --accent: #b8860b;
  --accent-strong: #8a6109;
  --accent-soft: rgba(184, 134, 11, 0.08);
  --accent-medium: rgba(184, 134, 11, 0.24);
  --text-primary: #232019;
  --text-secondary: #6b675f;
  --text-muted: #a29d92;
  --border: #e9e6e0;
  --border-subtle: #f1eee8;
  --radius-xs: 4px;
  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 14px;
  --content-width: 720px;
  --shadow-glow: 0 12px 40px rgba(120, 90, 20, 0.12);

  min-height: 100vh;
  position: relative;
  background: var(--bg);
  color: var(--text-primary);
}

/* 页面容器（原项目未加载全局样式，这里自带一份） */
.container {
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 32px;
}

/* 页面容器（原项目未加载全局样式，这里自带一份） */
.container {
  width: 100%;
  max-width: 1120px;
  margin: 0 auto;
  padding: 0 32px;
}

/* ============ 阅读进度条 ============ */
.read-progress {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  z-index: 90;
  pointer-events: none;
}

.read-progress-bar {
  height: 100%;
  transform-origin: left center;
  background: linear-gradient(90deg, var(--accent-medium), var(--accent));
}

/* ============ 悬浮返回 ============ */
.float-back {
  position: fixed;
  top: 20px;
  left: 24px;
  z-index: 80;
  display: inline-flex;
  align-items: center;
  gap: 7px;
  padding: 8px 15px;
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.04em;
  color: var(--text-secondary);
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--border);
  border-radius: 999px;
  box-shadow: 0 2px 10px rgba(60, 45, 15, 0.05);
  transition: color 0.25s, border-color 0.25s, transform 0.25s;

  svg {
    transition: transform 0.25s;
  }

  &:hover {
    color: var(--accent);
    border-color: var(--accent-medium);
    transform: translateX(-3px);

    svg {
      transform: translateX(-2px);
    }
  }
}

/* ============ 加载态 ============ */
.article-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
}

.loader {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 18px;
}

.loader-ring {
  width: 32px;
  height: 32px;
  border: 2px solid var(--border);
  border-top-color: var(--accent);
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
}

.loader-text {
  font-family: var(--font-mono);
  font-size: 12px;
  letter-spacing: 0.16em;
  color: var(--text-muted);
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

/* ============ Hero ============ */
.article-hero {
  position: relative;
  padding: 112px 0 56px;
}

.hero-inner {
  position: relative;
  z-index: 1;
  max-width: var(--content-width);
  margin: 0 auto;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}

/* 中文按字断行、英文单词整体不拆；超长单词兜底换行 */
.article-hero :deep(.header-title) {
  word-break: normal;
  overflow-wrap: break-word;
}

/* 分类 / 标签 */
.hero-chips {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 8px;
  margin-bottom: 26px;
}

.category-chip {
  font-family: var(--font-mono);
  font-size: 11px;
  font-weight: 500;
  letter-spacing: 0.1em;
  color: var(--accent-strong);
  background: #f3ecd9;
  border: 1px solid rgba(184, 134, 11, 0.22);
  padding: 4px 13px;
  border-radius: 999px;
}

.tag-chip {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.05em;
  color: var(--text-secondary);
  background: #ffffff;
  border: 1px solid var(--border);
  padding: 4px 11px;
  border-radius: 999px;
  transition: color 0.2s, border-color 0.2s;

  &:hover {
    color: var(--accent);
    border-color: var(--accent-medium);
  }
}

/* 封面 */
.hero-cover {
  position: relative;
  z-index: 1;
  max-width: 940px;
  margin: 44px auto 0;
  aspect-ratio: 16 / 9;
  border-radius: var(--radius-lg);
  overflow: hidden;
  border: 1px solid var(--border);
  box-shadow: 0 18px 50px rgba(60, 45, 15, 0.14);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
}

/* ============ 正文布局 ============ */
.article-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 224px;
  gap: 56px;
  align-items: start;
  padding: 16px 0 116px;
}

.article-main {
  min-width: 0;
}

/* 无目录：折叠侧栏，正文列居中 */
.article-body.no-toc {
  grid-template-columns: minmax(0, 1fr);
  gap: 0;
  max-width: 776px;
  margin: 0 auto;

  .article-main {
    max-width: none;
    margin: 0;
  }
}

/* 纸张卡片 */
.paper {
  position: relative;
  background: var(--bg-elevated);
  border: 1px solid var(--border);
  border-radius: var(--radius-lg);
  padding: 48px 52px 40px;
  overflow: hidden;
  box-shadow: 0 1px 2px rgba(60, 45, 15, 0.03), 0 14px 36px rgba(60, 45, 15, 0.05);

  /* 顶部金色发丝线 */
  &::before {
    content: '';
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    height: 2px;
    background: linear-gradient(90deg, transparent, #d9b45e 30%, #d9b45e 70%, transparent);
    opacity: 0.55;
  }
}

/* 文末标记 */
.article-end {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-top: 56px;
}

.end-line {
  flex: 1;
  height: 1px;
  background: linear-gradient(90deg, transparent, var(--border));

  &:last-child {
    background: linear-gradient(90deg, var(--border), transparent);
  }
}

.end-mark {
  font-family: var(--font-mono);
  font-size: 10px;
  letter-spacing: 0.32em;
  text-indent: 0.32em;
  color: var(--text-muted);
  white-space: nowrap;
  user-select: none;
}

/* 目录 */
.article-toc {
  position: relative;
  /* 撑满整行高度，sticky 目录才能跟随滚动 */
  align-self: stretch;
}

.toc-sticky {
  position: sticky;
  top: 96px;
}

/* 目录列表去圆点（全局 reset 缺失） */
.article-toc :deep(.toc-list) {
  list-style: none;
  margin: 0;
  padding: 0;
}

/* ============ 正文滚动显现 ============ */
.article-main.reveal-on {
  :deep(.prose.reveal-ready > *),
  .article-end {
    opacity: 0;
    transform: translateY(18px);
    transition:
      opacity 0.7s cubic-bezier(0.22, 1, 0.36, 1),
      transform 0.7s cubic-bezier(0.22, 1, 0.36, 1),
      filter 0.4s ease,
      box-shadow 0.4s ease;
  }

  :deep(.prose.reveal-ready > .is-revealed),
  .article-end.is-revealed {
    opacity: 1;
    transform: none;
  }
}

@media (prefers-reduced-motion: reduce) {
  .article-main.reveal-on {

    :deep(.prose.reveal-ready > *),
    .article-end {
      opacity: 1;
      transform: none;
      transition: none;
    }
  }
}

/* ============ 回到顶部 ============ */
.to-top {
  position: fixed;
  right: 28px;
  bottom: 32px;
  z-index: 80;
  width: 42px;
  height: 42px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--text-secondary);
  background: rgba(255, 255, 255, 0.82);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border: 1px solid var(--border);
  box-shadow: 0 4px 16px rgba(60, 45, 15, 0.08);
  transition: color 0.3s, border-color 0.3s, transform 0.3s;

  &:hover {
    color: var(--accent);
    border-color: var(--accent-medium);
    transform: translateY(-3px);
  }
}

/* ============ 过渡 ============ */
.pop-enter-active,
.pop-leave-active {
  transition: opacity 0.35s ease, transform 0.35s ease;
}

.pop-enter-from,
.pop-leave-to {
  opacity: 0;
  transform: translateY(10px);
}

/* ============ 空态 ============ */
.article-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  min-height: 100vh;
  color: var(--text-muted);

  svg {
    opacity: 0.5;
    margin-bottom: 10px;
  }

  .empty-title {
    font-size: 1.6rem;
    color: var(--text-primary);
  }

  .empty-sub {
    font-size: 0.9rem;
  }

  .empty-link {
    margin-top: 22px;
    font-family: var(--font-mono);
    font-size: 12px;
    letter-spacing: 0.06em;
    color: var(--accent-strong);
    padding: 9px 20px;
    border: 1px solid var(--accent-medium);
    border-radius: 999px;
    transition: background 0.25s, border-color 0.25s;

    &:hover {
      background: #f3ecd9;
      border-color: var(--accent);
    }
  }
}

/* ============ 响应式 ============ */
@media (max-width: 1080px) {
  .container {
    padding: 0 24px;
  }

  .article-body {
    grid-template-columns: minmax(0, 1fr);
    gap: 0;
    padding: 12px 0 88px;
  }

  /* 单列时限制阅读宽度并居中 */
  .article-main {
    width: 100%;
    max-width: 744px;
    margin: 0 auto;
  }

  .article-body.no-toc {
    max-width: 744px;
  }

  .article-toc {
    display: none;
  }

  .paper {
    padding: 40px 40px 34px;
  }
}

@media (max-width: 768px) {
  .container {
    padding: 0 16px;
  }

  .article-hero {
    padding: 84px 0 32px;
  }

  .hero-chips {
    margin-bottom: 20px;
  }

  .hero-cover {
    margin-top: 28px;
    border-radius: var(--radius-md);
  }

  .article-body {
    padding: 4px 0 72px;
  }

  .paper {
    padding: 26px 20px 22px;
    border-radius: 12px;
  }

  .article-end {
    margin-top: 40px;
  }

  .float-back {
    top: 14px;
    left: 14px;
    padding: 8px;
  }

  .back-label {
    display: none;
  }

  .to-top {
    right: 16px;
    bottom: 20px;
    width: 38px;
    height: 38px;
  }
}

@media (max-width: 420px) {
  .paper {
    padding: 22px 16px 18px;
  }

  .article-body {
    padding-bottom: 56px;
  }
}
</style>
