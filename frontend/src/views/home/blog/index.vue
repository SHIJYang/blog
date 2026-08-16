<template>
  <div class="article-view">
    <div class="back-nav">
      <router-link to="/" class="back-btn">
        <svg viewBox="0 0 24 24" width="20" height="20" fill="none" stroke="currentColor" stroke-width="2"
          stroke-linecap="round" stroke-linejoin="round">
          <path d="M19 12H5M12 19l-7-7 7-7" />
        </svg>
        返回博客
      </router-link>
    </div>
    <!-- 加载态 -->
    <div class="article-loading" v-if="loading">
      <div class="loading-spinner"></div>
    </div>

    <template v-else-if="currentArticle">
      <!-- Hero -->
      <header class="article-hero" ref="heroRef">
        <div class="hero-noise" aria-hidden="true"></div>
        <div class="hero-glow" aria-hidden="true"></div>
        <div class="container hero-inner">
          <ArticleHeader :article="currentArticle" />
        </div>
      </header>

      <!-- 正文 -->
      <div class="container">
        <div class="article-body">
          <main class="article-main" ref="mainRef">
            <ArticleContent :content="currentArticle.content" />

            <CommentSection v-if="currentArticle.allowComment === 1" :article-id="currentArticle.id" />
          </main>
          <aside class="article-toc">
            <div class="toc-sticky">
              <TableOfContents :content="currentArticle.content" />
            </div>
          </aside>
        </div>
      </div>
    </template>

    <div class="article-empty" v-else>
      <p>文章不存在</p>
      <router-link to="/">返回首页</router-link>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { storeToRefs } from 'pinia'
import { gsap } from 'gsap'
import { useArticleStore } from '@/stores/article'

import ArticleHeader from '@/components/article/ArticleHeader.vue'
import ArticleContent from '@/components/article/ArticleContent.vue'
import TableOfContents from '@/components/article/TableOfContents.vue'
import CommentSection from '@/components/comment/CommentSection.vue'

const route = useRoute()
const articleStore = useArticleStore()
const { currentArticle } = storeToRefs(articleStore)

const loading = ref(true)
const heroRef = ref(null)
const mainRef = ref(null)

onMounted(async () => {
  try {
    await articleStore.fetchPublicArticleDetail(route.params.id)
  } catch (e) {
    console.error(e)
  }
  loading.value = false
  await nextTick()
  requestAnimationFrame(() => {
    runAnimations()
  })
})

onUnmounted(() => {
  articleStore.clearCurrentArticle()
})

function runAnimations() {
  const tl = gsap.timeline({
    defaults: { ease: 'power4.out', duration: 0.9 },
  })

  // header 子元素
  const header = heroRef.value
  if (header) {
    const chips = header.querySelectorAll('.category-chip')
    const title = header.querySelector('.header-title')
    const meta = header.querySelector('.header-meta')
    const tags = header.querySelector('.header-tags')

    if (chips.length) tl.from(chips, { y: 20, opacity: 0, stagger: 0.06, duration: 0.6 }, 0)
    if (title) tl.from(title, { y: 50, opacity: 0 }, 0.1)
    if (meta) tl.from(meta, { y: 15, opacity: 0, duration: 0.5 }, '-=0.5')
    if (tags) tl.from(tags, { y: 10, opacity: 0, duration: 0.4 }, '-=0.4')
  }

  // 主内容
  if (mainRef.value) {
    tl.from(mainRef.value, { y: 80, opacity: 0, duration: 1 }, '-=0.5')
  }

  const toc = document.querySelector('.article-toc')
  if (toc) {
    tl.from(toc, { x: 30, opacity: 0, duration: 0.7 }, '-=0.7')
  }
}
</script>

<style lang="scss" scoped>
.article-view {
  min-height: 100vh;
  position: relative;
}

/* 加载 */
.article-loading {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
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

/* Hero */
.article-hero {
  position: relative;
  padding: 160px 0 120px;
  overflow: hidden;
}

.hero-noise {
  position: absolute;
  inset: 0;
  opacity: 0.03;
  background-image: url("data:image/svg+xml,%3Csvg viewBox='0 0 256 256' xmlns='http://www.w3.org/2000/svg'%3E%3Cfilter id='n'%3E%3CfeTurbulence type='fractalNoise' baseFrequency='0.9' numOctaves='4' stitchTiles='stitch'/%3E%3C/filter%3E%3Crect width='100%25' height='100%25' filter='url(%23n)'/%3E%3C/svg%3E");
  pointer-events: none;
}

.hero-glow {
  position: absolute;
  top: -40%;
  left: -10%;
  width: 60%;
  height: 140%;
  background: radial-gradient(ellipse, var(--accent-soft) 0%, transparent 70%);
  filter: blur(80px);
  pointer-events: none;
}

.hero-inner {
  position: relative;
  z-index: 1;
  max-width: var(--content-width);
}

/* 正文布局 */
.article-body {
  display: grid;
  grid-template-columns: 1fr 220px;
  gap: 80px;
  align-items: start;
  padding-bottom: 160px;
  padding-top: 40px;
}

.article-main {
  min-width: 0;
}

.article-toc {
  position: relative;
}

.toc-sticky {
  position: sticky;
  top: 100px;
}

/* 空态 */
.article-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 16px;
  min-height: 60vh;
  color: var(--text-muted);
  font-size: 0.95rem;

  a {
    color: var(--accent);
    font-family: var(--font-mono);
    font-size: 12px;
    padding-bottom: 2px;
    border-bottom: 1px solid var(--accent-soft);
    transition: border-color 0.2s;

    &:hover {
      border-color: var(--accent);
    }
  }
}

.back-nav {
  margin-bottom: 24px;
}

.back-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  font-size: 0.9rem;
  font-family: var(--font-mono);
  color: var(--text-muted);
  text-decoration: none;
  transition: color 0.2s, transform 0.2s;

  svg {
    transition: transform 0.2s;
  }

  &:hover {
    color: var(--accent);
    transform: translateX(-4px);

    svg {
      transform: translateX(-2px);
    }
  }
}

@media (max-width: 1100px) {
  .article-body {
    grid-template-columns: 1fr;
    gap: 0;
  }

  .article-toc {
    display: none;
  }
}

@media (max-width: 768px) {
  .article-hero {
    padding: 110px 0 80px;
  }

  .article-body {
    padding-bottom: 100px;
  }
}
</style>