<template>
  <div class="blog-page">
    <!-- Hero -->
    <section class="blog-hero">
      <p class="blog-hero__kicker">BLOG / 文章</p>
      <h1 class="blog-hero__title">
        <span class="word" v-for="(w, i) in titleWords" :key="i">{{ w }}</span>
      </h1>
      <p class="blog-hero__sub">{{ total }} 篇文章 · {{ categoryOptions.length }} 个分类</p>
    </section>

    <!-- 筛选栏（吸顶 + 毛玻璃） -->
    <div class="blog-filter" ref="filterBar">
      <div class="blog-filter__inner">
        <button
          class="filter-chip"
          :class="{ 'filter-chip--active': !activeCategory }"
          @click="activeCategory = ''; handleSearch()"
        >
          全部
        </button>
        <button
          v-for="cat in categoryOptions"
          :key="cat.id"
          class="filter-chip"
          :class="{ 'filter-chip--active': activeCategory === cat.id }"
          @click="activeCategory = cat.id; handleSearch()"
        >
          {{ cat.name }}
        </button>

        <el-select
          v-model="activeTag"
          placeholder="标签"
          clearable
          size="small"
          @change="handleSearch"
          class="filter-tag"
        >
          <el-option v-for="t in tagOptions" :key="t.id" :label="t.name" :value="t.id" />
        </el-select>
      </div>
    </div>

    <!-- 文章网格 -->
    <div class="blog-grid" v-loading="loading" element-loading-background="rgba(248,246,241,0.7)">
      <BlogListCard
        v-for="(item, i) in tableData"
        :key="item.id"
        :item="item"
        :index="(queryParams.current - 1) * queryParams.size + i + 1"
        :gradient="gradients[i % gradients.length]"
        :delay="Math.min(i * 0.06, 0.4)"
        @click="goArticle"
      />

      <div v-if="!loading && !tableData.length" class="blog-empty">
        <p class="blog-empty__text">还没有文章</p>
        <button class="blog-empty__reset" @click="resetFilter">查看全部</button>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > queryParams.size" class="blog-pagination">
      <el-pagination
        v-model:current-page="queryParams.current"
        v-model:page-size="queryParams.size"
        :page-sizes="[6, 12, 24]"
        :total="total"
        layout="prev, pager, next, sizes"
        @size-change="handleSizeChange"
        @current-change="handleCurrentChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, onUnmounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useArticleStore } from '@/stores/article'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
import BlogListCard from '@/components/bloglistcard.vue'

gsap.registerPlugin(ScrollTrigger)

const router = useRouter()
const articleStore = useArticleStore()

const { publicArticles: tableData, publicPagination } = storeToRefs(articleStore)
const total = computed(() => publicPagination.value.total)
const categoryOptions = computed(() => articleStore.publicCategories)
const tagOptions = computed(() => articleStore.publicTags)

const filterBar = ref(null)
const loading = ref(false)
const activeCategory = ref('')
const activeTag = ref(null)
const queryParams = reactive({ current: 1, size: 6 })

const titleWords = ['文', '章']

const gradients = [
  'linear-gradient(135deg, #e9e2d2, #d9cba8)',
  'linear-gradient(135deg, #e2ddd2, #c9c0ae)',
  'linear-gradient(135deg, #f0e4d4, #dfc4a0)',
  'linear-gradient(135deg, #dde5d8, #b9cdb4)',
  'linear-gradient(135deg, #e4dde8, #c9b8d6)',
  'linear-gradient(135deg, #dbe3ea, #b3c6d8)'
]

const formatDate = (s) => {
  if (!s) return ''
  const d = new Date(s)
  if (Number.isNaN(d.getTime())) return ''
  const months = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec']
  return `${months[d.getMonth()]} ${String(d.getDate()).padStart(2, '0')}`
}

const getList = async () => {
  loading.value = true
  try {
    const params = {
      ...queryParams,
      categoryId: activeCategory.value || undefined,
      tagId: activeTag.value || undefined,
      status: 1
    }
    await articleStore.fetchPublicArticles(params)
    await nextTick()
    animateHero()
  } catch (err) {
    console.error(err)
  } finally {
    loading.value = false
  }
}

/** Hero 入场动画（仅首次加载时播放） */
let heroPlayed = false
const animateHero = () => {
  if (heroPlayed) return
  heroPlayed = true

  const kicker = document.querySelector('.blog-hero__kicker')
  const title = document.querySelectorAll('.blog-hero__title .word')
  const sub = document.querySelector('.blog-hero__sub')
  const bar = filterBar.value

  gsap.timeline({ delay: 0.1 })
    .from(kicker, { y: 16, opacity: 0, duration: 0.5, ease: 'power3.out' })
    .from(title, { y: 30, opacity: 0, duration: 0.7, stagger: 0.09, ease: 'power3.out' }, '-=0.25')
    .from(sub, { y: 12, opacity: 0, duration: 0.5 }, '-=0.4')
    .from(bar, { y: 14, opacity: 0, duration: 0.5 }, '-=0.3')
}

const resetFilter = () => {
  activeCategory.value = ''
  activeTag.value = null
  handleSearch()
}

const handleSearch = () => { queryParams.current = 1; getList() }
const handleSizeChange = () => { queryParams.current = 1; getList() }
const handleCurrentChange = () => { getList() }

const goArticle = (item) => {
  router.push({ name: 'ArticleBrowse', params: { id: item.id } })
}

onMounted(async () => {
  // 分类/标签缓存为空时初始化
  if (!categoryOptions.value.length) {
    articleStore.initPublicMeta().catch(() => {})
  }
  await getList()
})

onUnmounted(() => {
  ScrollTrigger.getAll()?.forEach(t => t.kill())
})
</script>

<style scoped lang="scss">
/* ============ 浅色主题变量（页面级） ============ */
.blog-page {
  --blog-bg: #f8f6f1;                    /* 页面底色：暖米白 */
  --blog-surface: #ffffff;               /* 卡片表面 */
  --blog-text: #232019;                  /* 主文字：暖深棕 */
  --blog-text-secondary: #6b675f;
  --blog-text-muted: #a29d92;
  --blog-accent: #b8860b;                /* 深金 */
  --blog-accent-soft: #f3ecd9;           /* 浅金底 */
  --blog-line: #e9e6e0;
  --blog-radius: 14px;

  max-width: 1200px;
  margin: 0 auto;
  padding: 32px 24px 80px;
  min-height: 100vh;
  background: var(--blog-bg);
  color: var(--blog-text);
}

/* ================= Hero ================= */
.blog-hero {
  padding: 48px 0 40px;
  text-align: center;
}

.blog-hero__kicker {
  font-family: var(--font-mono);
  font-size: 0.72rem;
  letter-spacing: 0.4em;
  text-transform: uppercase;
  color: var(--blog-accent);
  margin: 0 0 18px;
}

.blog-hero__title {
  font-family: var(--font-display);
  font-size: clamp(2.6rem, 6vw, 3.8rem);
  font-weight: 600;
  letter-spacing: 0.02em;
  line-height: 1.1;
  color: var(--blog-text);
  margin: 0 0 16px;
  display: flex;
  justify-content: center;
  gap: 0.08em;
}

.word {
  display: inline-block;
  opacity: 0;
  transform: translateY(30px);
}

.blog-hero__sub {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  letter-spacing: 0.15em;
  color: var(--blog-text-muted);
  margin: 0;
}

/* ================= 筛选栏 ================= */
.blog-filter {
  position: sticky;
  top: 16px;
  z-index: 100;
  margin: 0 auto 36px;
  padding: 10px 14px;
  background: rgba(255, 255, 255, 0.78);
  backdrop-filter: blur(18px);
  -webkit-backdrop-filter: blur(18px);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 999px;
  max-width: 720px;
  display: flex;
  justify-content: center;
  box-shadow: 0 8px 30px rgba(80, 60, 20, 0.08);
}

.blog-filter__inner {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  justify-content: center;
}

.filter-chip {
  padding: 7px 18px;
  font-family: var(--font-mono);
  font-size: 0.78rem;
  letter-spacing: 0.06em;
  color: var(--blog-text-secondary);
  background: transparent;
  border: 1px solid transparent;
  border-radius: 999px;
  cursor: pointer;
  white-space: nowrap;
  transition: all 0.25s ease;

  &:hover {
    color: var(--blog-text);
    background: rgba(0, 0, 0, 0.04);
  }
}

.filter-chip--active {
  color: #fff;
  background: var(--blog-accent);
  border-color: var(--blog-accent);
  font-weight: 600;
  box-shadow: 0 4px 16px rgba(184, 134, 11, 0.28);
}

/* Element Plus Select 浅色融合 */
:deep(.filter-tag) {
  width: 120px;
}

:deep(.filter-tag .el-input__wrapper) {
  border-radius: 999px;
  background: rgba(0, 0, 0, 0.035);
  box-shadow: none;
  padding: 4px 14px;
  transition: background 0.2s;
}

:deep(.filter-tag .el-input__wrapper:hover),
:deep(.filter-tag .el-input__wrapper.is-focus) {
  background: rgba(0, 0, 0, 0.06);
}

:deep(.filter-tag .el-input__inner) {
  font-family: var(--font-mono);
  font-size: 0.78rem;
  color: var(--blog-text);
}

:deep(.filter-tag .el-input__inner::placeholder) {
  color: var(--blog-text-muted);
}

/* ================= 文章网格 ================= */
.blog-grid {
  position: relative;
  min-height: 200px;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 28px;
}

/* ================= 空状态 ================= */
.blog-empty {
  grid-column: 1 / -1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 90px 20px;
}

.blog-empty__text {
  font-family: var(--font-mono);
  font-size: 0.9rem;
  letter-spacing: 0.1em;
  color: var(--blog-text-muted);
  margin: 0;
}

.blog-empty__reset {
  font-family: var(--font-mono);
  font-size: 0.78rem;
  letter-spacing: 0.08em;
  color: var(--blog-text);
  background: transparent;
  border: 1px solid var(--blog-line);
  padding: 8px 22px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.25s ease;

  &:hover {
    color: #fff;
    background: var(--blog-accent);
    border-color: var(--blog-accent);
  }
}

/* ================= 分页 ================= */
.blog-pagination {
  display: flex;
  justify-content: center;
  margin-top: 56px;
}

:deep(.el-pagination) {
  --el-pagination-bg-color: transparent;
  --el-pagination-hover-color: var(--blog-accent);
  --el-pagination-button-color: var(--blog-text-secondary);
  gap: 6px;
}

:deep(.el-pagination .btn-prev),
:deep(.el-pagination .btn-next),
:deep(.el-pagination .el-pager li) {
  border-radius: 10px !important;
  border: 1px solid var(--blog-line) !important;
  background: #fff !important;
  color: var(--blog-text-secondary) !important;
  font-family: var(--font-mono) !important;
  font-size: 0.8rem !important;
  min-width: 38px;
  height: 38px;
  transition: all 0.2s;
}

:deep(.el-pagination .el-pager li:hover) {
  color: var(--blog-accent) !important;
}

:deep(.el-pagination .el-pager li.is-active) {
  background: var(--blog-accent) !important;
  border-color: var(--blog-accent) !important;
  color: #fff !important;
  font-weight: 600;
}

/* ================= 响应式 ================= */
@media (max-width: 900px) {
  .blog-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 767px) {
  .blog-page {
    padding: 16px 16px 60px;
  }

  .blog-hero {
    padding: 32px 0 28px;
  }

  .blog-filter {
    border-radius: 24px;
    max-width: none;
  }

  .blog-grid {
    gap: 20px;
  }
}
</style>
