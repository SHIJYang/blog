<template>
  <article ref="contentRef" class="prose" v-html="content"></article>

  <!-- 图片灯箱 -->
  <Teleport to="body">
    <Transition name="lightbox-fade">
      <div v-if="lightboxSrc" class="img-lightbox" @click="lightboxSrc = null">
        <img :src="lightboxSrc" alt="" />
        <span class="lightbox-hint">点击任意处关闭</span>
      </div>
    </Transition>
  </Teleport>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'

const props = defineProps({ content: { type: String, default: '' } })
const contentRef = ref(null)
const lightboxSrc = ref(null)

function enhance() {
  if (!contentRef.value) return

  contentRef.value.querySelectorAll('pre code').forEach((codeEl) => {
    const pre = codeEl.parentElement
    if (pre.dataset.enhanced) return
    pre.dataset.enhanced = 'true'
    pre.style.position = 'relative'

    const langClass = Array.from(codeEl.classList).find(c => c.startsWith('language-'))
    const lang = langClass ? langClass.replace('language-', '') : 'code'

    const badge = document.createElement('span')
    badge.className = '__lang'
    badge.textContent = lang
    pre.appendChild(badge)

    const btn = document.createElement('button')
    btn.className = '__copy'
    btn.textContent = '复制'
    btn.addEventListener('click', async () => {
      try {
        await navigator.clipboard.writeText(codeEl.textContent || '')
        btn.textContent = '✓'
        setTimeout(() => (btn.textContent = '复制'), 2000)
      } catch { }
    })
    pre.appendChild(btn)
  })

  // 图片：懒加载 + 点击灯箱
  contentRef.value.querySelectorAll('img').forEach((img) => {
    if (img.dataset.enhanced) return
    img.dataset.enhanced = 'true'
    img.loading = 'lazy'
    img.addEventListener('click', () => {
      lightboxSrc.value = img.currentSrc || img.src
    })
  })

  // 表格：包裹一层，小屏可横向滚动
  contentRef.value.querySelectorAll('table').forEach((table) => {
    if (table.dataset.enhanced) return
    table.dataset.enhanced = 'true'
    const wrap = document.createElement('div')
    wrap.className = 'table-scroll'
    table.parentNode.insertBefore(wrap, table)
    wrap.appendChild(table)
  })

  contentRef.value.querySelectorAll('h1,h2,h3,h4').forEach((el, i) => {
    if (!el.id) el.id = `heading-${i}`
  })
}

onMounted(() => nextTick(enhance))
watch(() => props.content, () => {
  lightboxSrc.value = null
  nextTick(enhance)
})
</script>

<style lang="scss">
.prose {
  font-family: var(--font-body);
  font-size: 1.08rem;
  line-height: 1.9;
  color: var(--text-primary);
  word-break: break-word;

  /* 标题 */
  h1,
  h2,
  h3,
  h4,
  h5,
  h6 {
    font-family: var(--font-display);
    margin-top: 2.6em;
    margin-bottom: 0.55em;
    line-height: 1.2;
    scroll-margin-top: 100px;
    color: var(--text-primary);

    &:first-child {
      margin-top: 0;
    }
  }

  h1 {
    font-size: 2rem;
    font-weight: 400;
    padding-bottom: 16px;
    border-bottom: 1px solid var(--border);
  }

  h2 {
    font-size: 1.6rem;
    font-weight: 400;
    position: relative;
    padding-left: 20px;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 0.2em;
      width: 3px;
      height: 0.8em;
      background: var(--accent);
      border-radius: 2px;
      opacity: 0.7;
    }
  }

  h3 {
    font-size: 1.15rem;
    font-weight: 600;
    font-family: var(--font-mono);
    letter-spacing: -0.005em;
  }

  h4 {
    font-size: 0.8rem;
    font-weight: 600;
    font-family: var(--font-mono);
    text-transform: uppercase;
    letter-spacing: 0.1em;
    color: var(--text-muted);
  }

  p {
    margin-bottom: 1.5em;
  }

  strong {
    color: var(--accent-strong);
    font-weight: 600;
  }

  em {
    color: var(--text-secondary);
    font-style: italic;
  }

  a {
    color: var(--accent);
    text-decoration: underline;
    text-decoration-color: var(--accent-medium);
    text-underline-offset: 4px;
    text-decoration-thickness: 1px;
    transition: text-decoration-color 0.2s;

    &:hover {
      text-decoration-color: var(--accent);
    }
  }

  /* 行内 code */
  code {
    font-family: var(--font-mono);
    font-size: 0.84em;
    background: #f6f2e8;
    color: var(--accent-strong);
    padding: 2px 8px;
    border-radius: var(--radius-xs);
    border: 1px solid #efe8d8;
  }

  /* 代码块 */
  pre {
    margin: 2.5em 0;
    border-radius: var(--radius-md);
    overflow: hidden;
    background: #1d1b18 !important;
    border: 1px solid #1d1b18;

    code {
      display: block;
      padding: 24px 28px;
      overflow-x: auto;
      font-size: 0.82rem;
      line-height: 1.8;
      background: none !important;
      border: none !important;
      color: #d7d2c8;
      tab-size: 4;
      /* 后端会把代码块的换行压平，这里改为自动换行，避免出现超宽单行 */
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }
  }

  .__lang {
    position: absolute;
    top: 12px;
    right: 16px;
    font-family: var(--font-mono);
    font-size: 10px;
    font-weight: 500;
    letter-spacing: 0.08em;
    text-transform: uppercase;
    color: #8d867a;
    opacity: 0.7;
    user-select: none;
  }

  .__copy {
    position: absolute;
    top: 10px;
    right: 60px;
    font-family: var(--font-mono);
    font-size: 10px;
    color: #b7b0a3;
    background: none;
    border: 1px solid rgba(255, 255, 255, 0.16);
    padding: 3px 10px;
    border-radius: var(--radius-xs);
    cursor: pointer;
    opacity: 0;
    transition: all 0.2s;
    user-select: none;
  }

  pre:hover .__copy {
    opacity: 1;

    &:hover {
      color: var(--accent);
      border-color: var(--accent-medium);
    }
  }

  /* 表格 */
  table {
    width: 100%;
    border-collapse: collapse;
    margin: 2.5em 0;
    font-size: 0.9rem;
  }

  thead th {
    padding: 10px 16px;
    text-align: left;
    font-family: var(--font-mono);
    font-size: 0.7rem;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.1em;
    color: #8f887c;
    border-bottom: 1px solid var(--border);
    background: #f8f4ec;
  }

  td {
    padding: 14px 16px;
    border-bottom: 1px solid #f3f0e9;
    vertical-align: top;
  }

  tr:last-child td {
    border-bottom: none;
  }

  tbody tr {
    transition: background 0.2s;

    &:hover {
      background: rgba(184, 134, 11, 0.06);
    }
  }

  /* 列表 */
  ul,
  ol {
    padding-left: 1.6em;
    margin-bottom: 1.5em;
  }

  li {
    margin-bottom: 0.4em;

    &::marker {
      color: var(--accent);
      opacity: 0.7;
    }
  }

  ul li {
    list-style-type: '—  ';
  }

  ol li {
    list-style-type: decimal;
  }

  /* 任务列表：自绘勾选框，避免与列表破折号重复 */
  li.task-item {
    list-style: none;
    position: relative;
    padding-left: 24px;

    &::before {
      content: '';
      position: absolute;
      left: 0;
      top: 0.58em;
      width: 14px;
      height: 14px;
      border: 1px solid #d6cfc0;
      border-radius: 3px;
      background: #ffffff;
    }

    &.task-item--done {
      color: var(--text-muted);

      &::before {
        background: var(--accent);
        border-color: var(--accent);
      }

      &::after {
        content: '';
        position: absolute;
        left: 4.5px;
        top: calc(0.58em + 2px);
        width: 4px;
        height: 8px;
        border: solid #ffffff;
        border-width: 0 1.5px 1.5px 0;
        transform: rotate(45deg);
      }
    }
  }

  /* 引用 */
  blockquote {
    position: relative;
    margin: 2em 0;
    padding: 22px 24px 20px 52px;
    border-left: 2px solid var(--accent);
    background: #f9f4e6;
    border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
    color: var(--text-secondary);
    line-height: 1.85;

    p {
      margin-bottom: 0.6em;
    }

    /* 装饰引号 */
    &::before {
      content: '\201C';
      position: absolute;
      top: 16px;
      left: 16px;
      font-family: var(--font-display);
      font-size: 2.6rem;
      line-height: 1;
      color: var(--accent);
      opacity: 0.32;
      pointer-events: none;
    }

    p:last-child {
      margin-bottom: 0;
    }
  }

  hr {
    border: none;
    height: 1px;
    background: linear-gradient(90deg, transparent, var(--border), transparent);
    margin: 3.5em 0;
  }

  img {
    max-width: 100%;
    border-radius: var(--radius-md);
    margin: 2em 0;
    cursor: zoom-in;

    &:hover {
      filter: brightness(1.07);
      box-shadow: var(--shadow-glow);
    }
  }

  /* 表格滚动包裹层 */
  .table-scroll {
    overflow-x: auto;
    margin: 2.5em 0;
    border: 1px solid var(--border);
    border-radius: var(--radius-md);
    -webkit-overflow-scrolling: touch;

    table {
      margin: 0;
      min-width: 480px;
    }
  }
}

/* ============ 移动端排版 ============ */
@media (max-width: 768px) {
  .prose {
    font-size: 1rem;
    line-height: 1.85;

    h1 {
      font-size: 1.65rem;
    }

    h2 {
      font-size: 1.38rem;
      padding-left: 16px;
    }

    h3 {
      font-size: 1.06rem;
    }

    pre {
      margin: 2em 0;

      code {
        padding: 18px 16px;
        font-size: 0.78rem;
      }
    }

    blockquote {
      padding: 18px 16px 16px 44px;

      &::before {
        font-size: 2.2rem;
        left: 12px;
      }
    }

    table {
      font-size: 0.82rem;
    }

    thead th {
      padding: 8px 12px;
    }

    td {
      padding: 10px 12px;
    }
  }
}

/* ============ 图片灯箱 ============ */
.img-lightbox {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 18px;
  padding: 32px;
  background: rgba(4, 4, 5, 0.88);
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
  cursor: zoom-out;

  img {
    max-width: 92vw;
    max-height: 86vh;
    border-radius: var(--radius-md);
    box-shadow: 0 24px 80px rgba(0, 0, 0, 0.6);
    cursor: inherit;
  }
}

.lightbox-hint {
  font-family: var(--font-mono);
  font-size: 11px;
  letter-spacing: 0.14em;
  color: var(--text-muted);
  user-select: none;
}

.lightbox-fade-enter-active,
.lightbox-fade-leave-active {
  transition: opacity 0.3s ease;
}

.lightbox-fade-enter-from,
.lightbox-fade-leave-to {
  opacity: 0;
}
</style>