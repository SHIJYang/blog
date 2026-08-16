<template>
  <article ref="contentRef" class="prose" v-html="content"></article>
</template>

<script setup>
import { ref, onMounted, nextTick, watch } from 'vue'

const props = defineProps({ content: { type: String, default: '' } })
const contentRef = ref(null)

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

  contentRef.value.querySelectorAll('h1,h2,h3,h4').forEach((el, i) => {
    if (!el.id) el.id = `heading-${i}`
  })
}

onMounted(() => nextTick(enhance))
watch(() => props.content, () => nextTick(enhance))
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
    margin-top: 3em;
    margin-bottom: 0.5em;
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
    color: var(--accent);
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
    background: var(--surface);
    color: var(--accent);
    padding: 2px 8px;
    border-radius: var(--radius-xs);
    border: 1px solid var(--border);
  }

  /* 代码块 */
  pre {
    margin: 2.5em 0;
    border-radius: var(--radius-md);
    overflow: hidden;
    background: #0a0a0c !important;
    border: 1px solid var(--border);

    code {
      display: block;
      padding: 28px 32px;
      overflow-x: auto;
      font-size: 0.82rem;
      line-height: 1.8;
      background: none !important;
      border: none !important;
      color: var(--text-secondary);
      tab-size: 4;
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
    color: var(--text-muted);
    opacity: 0.6;
    user-select: none;
  }

  .__copy {
    position: absolute;
    top: 10px;
    right: 60px;
    font-family: var(--font-mono);
    font-size: 10px;
    color: var(--text-muted);
    background: none;
    border: 1px solid var(--border);
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
    color: var(--text-muted);
    border-bottom: 1px solid var(--border);
    background: var(--surface);
  }

  td {
    padding: 14px 16px;
    border-bottom: 1px solid var(--border-subtle);
    vertical-align: top;
  }

  tr:last-child td {
    border-bottom: none;
  }

  tbody tr {
    transition: background 0.2s;

    &:hover {
      background: var(--accent-soft);
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

  /* 引用 */
  blockquote {
    margin: 2.5em 0;
    padding: 20px 24px;
    border-left: 2px solid var(--accent);
    background: var(--accent-soft);
    border-radius: 0 var(--radius-sm) var(--radius-sm) 0;
    color: var(--text-secondary);
    font-style: italic;

    p:last-child {
      margin-bottom: 0;
    }
  }

  hr {
    border: none;
    height: 1px;
    background: var(--border);
    margin: 3.5em 0;
  }

  img {
    max-width: 100%;
    border-radius: var(--radius-md);
    margin: 2em 0;
  }
}
</style>