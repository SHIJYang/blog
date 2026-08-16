<template>
  <nav class="toc" v-if="headings.length">
    <div class="toc-label">
      <List :size="12" />
      目录
    </div>
    <ul class="toc-list">
      <li v-for="(h, i) in headings" :key="i" :class="['toc-item', `level-${h.level}`, { active: activeId === h.id }]">
        <a :href="`#${h.id}`" @click.prevent="scrollTo(h.id)">{{ h.text }}</a>
      </li>
    </ul>
    <div class="toc-rail">
      <div class="toc-rail-fill" :style="{ height: progress + '%' }"></div>
    </div>
  </nav>
</template>

<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { List } from 'lucide-vue-next'

const props = defineProps({ content: { type: String, default: '' } })

const headings = ref([])
const activeId = ref('')
const progress = ref(0)
let observer = null

function parse() {
  const doc = new DOMParser().parseFromString(props.content, 'text/html')
  headings.value = [...doc.querySelectorAll('h1,h2,h3')].map((el, i) => ({
    id: el.id || `heading-${i}`,
    text: el.textContent.trim(),
    level: +el.tagName[1],
  }))
}

function scrollTo(id) {
  document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function onScroll() {
  const s = window.scrollY
  const d = document.documentElement.scrollHeight - window.innerHeight
  progress.value = d > 0 ? Math.min(100, (s / d) * 100) : 0
}

onMounted(() => {
  parse()
  observer = new IntersectionObserver(
    entries => entries.forEach(e => { if (e.isIntersecting) activeId.value = e.target.id }),
    { rootMargin: '-80px 0px -65% 0px', threshold: 0 }
  )
  setTimeout(() => {
    headings.value.forEach(h => {
      const el = document.getElementById(h.id)
      if (el) observer.observe(el)
    })
  }, 600)
  window.addEventListener('scroll', onScroll, { passive: true })
})

onUnmounted(() => {
  observer?.disconnect()
  window.removeEventListener('scroll', onScroll)
})
</script>

<style lang="scss" scoped>
.toc {
  position: relative;
  padding-left: 16px;
}

.toc-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 10px;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.12em;
  color: var(--text-muted);
  margin-bottom: 16px;
}

.toc-list {
  border-left: 1px solid var(--border);
}

.toc-item a {
  display: block;
  padding: 4px 14px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--text-muted);
  border-left: 2px solid transparent;
  margin-left: -1px;
  transition: all 0.2s;

  &:hover {
    color: var(--text-secondary);
  }
}

.level-2 a {
  padding-left: 14px;
}

.level-3 a {
  padding-left: 28px;
  font-size: 11px;
  opacity: 0.7;
}

.active a {
  color: var(--accent);
  border-left-color: var(--accent);
}

.toc-rail {
  position: absolute;
  left: 0;
  top: 30px;
  bottom: 0;
  width: 2px;
  background: var(--border);
  border-radius: 1px;
}

.toc-rail-fill {
  width: 100%;
  background: var(--accent);
  border-radius: 1px;
  transition: height 0.1s;
  opacity: 0.5;
}
</style>