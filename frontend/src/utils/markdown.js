import MarkdownIt from 'markdown-it'

/**
 * 文章正文渲染工具
 *
 * 后端返回的 content 有两种可能：
 * 1. 富文本 HTML（AiEditor 编写的文章）
 * 2. markdown 源码，且经常被压平成一行（换行丢失）、并做了 HTML 转义（> 变成 &gt;）
 *
 * 这里统一处理：HTML 直接返回；markdown 先还原换行结构，再交给 markdown-it 渲染。
 */

const md = new MarkdownIt({
  html: false,      // markdown 分支不信任内嵌 HTML，避免内容被吞
  linkify: true,    // 纯文本 URL 自动变链接
  breaks: false,
  typographer: false,
})

/**
 * 任务列表：把 `- [ ] xxx` / `- [x] xxx` 渲染成可勾选的方框
 * （markdown-it 默认不支持 GFM 任务列表，这里在 inline 解析前剥掉标记并打上 class）
 */
function taskListPlugin(mdInstance) {
  mdInstance.core.ruler.before('inline', 'task_list', (state) => {
    const tokens = state.tokens
    for (let i = 2; i < tokens.length; i++) {
      const inline = tokens[i]
      if (inline.type !== 'inline') continue
      if (tokens[i - 1].type !== 'paragraph_open') continue

      const itemOpen = tokens[i - 2]
      if (!itemOpen || itemOpen.type !== 'list_item_open') continue

      const m = inline.content.match(/^\[([ xX])\]\s+/)
      if (!m) continue

      inline.content = inline.content.slice(m[0].length)
      itemOpen.attrJoin('class', m[1].trim() ? 'task-item task-item--done' : 'task-item')
    }
  })
}

md.use(taskListPlugin)

const ENTITIES = {
  '&lt;': '<',
  '&gt;': '>',
  '&quot;': '"',
  '&#39;': "'",
  '&#x27;': "'",
  '&apos;': "'",
  '&nbsp;': ' ',
}

export function unescapeEntities(str) {
  return String(str).replace(/&(lt|gt|quot|#39|#x27|apos|nbsp);/g, m => ENTITIES[m] ?? m)
}

const HTML_HINT = /<(p|div|h[1-6]|img|br|ul|ol|table|blockquote|pre|section|figure|a|span|strong|em)\b[^>]*>/i

export function looksLikeHtml(str) {
  return HTML_HINT.test(str)
}

/** 常见代码块语言标记，用于从压平的代码块里拆出语言名 */
const LANGS = new Set([
  'js', 'javascript', 'jsx', 'ts', 'typescript', 'tsx', 'vue', 'html', 'xml', 'css', 'scss', 'sass', 'less',
  'json', 'yaml', 'yml', 'toml', 'ini', 'bash', 'sh', 'shell', 'zsh', 'bat', 'cmd', 'powershell', 'ps1',
  'python', 'py', 'java', 'kotlin', 'go', 'golang', 'rust', 'sql', 'md', 'markdown', 'diff', 'text', 'plain',
  'c', 'cpp', 'c++', 'csharp', 'cs', 'php', 'ruby', 'rb', 'swift', 'dockerfile', 'nginx', 'http', 'graphql',
])

/**
 * 保护代码块：```...``` 内部内容不做任何块级还原
 */
function protectFences(text, store) {
  return text.replace(/```([\s\S]*?)```/g, (_, body) => {
    const trimmed = body.trim()
    const sep = trimmed.search(/\s/)
    const first = sep === -1 ? trimmed : trimmed.slice(0, sep)
    let lang = ''
    let code = trimmed
    if (first && LANGS.has(first.toLowerCase())) {
      lang = first
      code = trimmed.slice(sep + 1).trim()
    }
    store.push({ lang, code })
    return `\n\n\u0000F${store.length - 1}\u0000\n\n`
  })
}

function restoreFences(text, store) {
  return text.replace(/\u0000F(\d+)\u0000/g, (_, i) => {
    const { lang, code } = store[+i]
    return ['```' + lang, code, '```'].join('\n')
  })
}

const SEP_ROW = /^\s*\|(\s*:?-{3,}:?\s*\|)+/
const isTableLine = line => /^\s*\|/.test(line)

/**
 * 表格还原：
 * 压平后行与行之间会残留 "| |"（前一行的收尾竖线 + 后一行的起始竖线），
 * 用它断行；再借助分隔行 |---|---| 的列数，在表头前补一个空行。
 */
function reflowTables(text) {
  const lines = text.replace(/\|\s+\|/g, '|\n|').split('\n')
  const out = []

  for (const line of lines) {
    if (SEP_ROW.test(line) && out.length) {
      const prev = out[out.length - 1]
      // 表头可能还挂在上一行（正文）的末尾
      if (prev && !isTableLine(prev)) {
        const cols = (line.match(/\|/g) || []).length - 1
        const pipes = []
        for (let p = 0; p < prev.length; p++) if (prev[p] === '|') pipes.push(p)

        if (cols >= 1 && pipes.length >= cols + 1) {
          const cut = pipes[pipes.length - (cols + 1)]
          const head = prev.slice(0, cut).trim()
          const row = prev.slice(cut).trim()
          if (row) {
            out[out.length - 1] = head
            if (head) out.push('')
            out.push(row)
          }
        }
      }
    }
    out.push(line.trim())
  }

  return out.join('\n')
}

const HEADING = /^#{1,6}$/
const HR = /^(?:-{3,}|\*{3,}|_{3,})$/
const BULLET = /^[-*+]$/
const ORDERED = /^\d{1,2}[.)]$/

/** 正文段落的常见起始词（标题后紧跟这些词时，说明标题已经结束） */
const BODY_START = /^(推荐|使用|用|不要|别|需要|注意|如果|否则|当|把|让|对|从|通过|可以|能|会|是|有|没有|每|所有|直接|先|再|然后|例如|比如|如下|下面|以上|同时|顺便|记得|务必|尽量|一个|两个|两个|两种|三种|本文|这里|此时|其实|但是|但|而|所以|因此|首先|其次|最后|总之|结论|原因|做法|短期|长期|上线|部署|开发|测试|运行|打开|点击|输入|保存|提交|查看|进入|回到|选择|安装|配置|执行|编写|生成|定义|判断|检查|确认|避免|防止|减少|提高|解决|实现|支持|依赖|基于|由于|为了|相比|相比之|目前|现在|之前|之后|以前|以后)/

function looksLikeBodyStart(tok) {
  if (!tok) return false
  if (HEADING.test(tok)) return true
  if (tok.startsWith('**') || tok.startsWith('`') || tok.startsWith('“') || tok.startsWith('"')) return true
  if (tok.endsWith('：') || tok.endsWith(':')) return true
  if (BULLET.test(tok) || ORDERED.test(tok) || tok === '>' || tok === '---') return true
  return BODY_START.test(tok)
}

/** 句读符号：出现即说明是正文句子 */
const PROSE_PUNCT = /[，。；！？]/

/**
 * 标题与正文被压平粘连，需要判断标题在哪里结束。
 * 只有出现“块级标记 / 引号强调 / 句读号 / 长句 / 标题后两个词起出现的正文动词”时才切断，
 * 宁可标题略长，也不切出 ### 2.2 这种残缺标题。
 */
function splitHeading(tokens, level) {
  if (!tokens.length) return [[], []]

  const title = []
  const isH1 = level === 1

  for (let i = 0; i < tokens.length; i++) {
    const t = tokens[i]

    if (title.length) {
      const isMarker = HEADING.test(t) || t === '>' || HR.test(t) || BULLET.test(t) || ORDERED.test(t)
      const startsInline = t.startsWith('**') || t.startsWith('`') || t.startsWith('“') || t.startsWith('"')

      // 一级标题就是文章标题，只在遇到块级标记时收尾，避免把标题句读切断
      if (isH1) {
        if (isMarker || startsInline) return [title, tokens.slice(i)]
        title.push(t)
        continue
      }

      const looksProse = title.length >= 2 && (PROSE_PUNCT.test(t) || t.length >= 20)
      const verbStart = title.length >= 3 && BODY_START.test(t)
      const colonLead = t.endsWith('：') || t.endsWith(':')

      if (isMarker || startsInline || looksProse || verbStart || colonLead) {
        return [title, tokens.slice(i)]
      }
      if (title.join(' ').length > 40) {
        return [title, tokens.slice(i)]
      }
    }

    title.push(t)
  }

  return [title, []]
}

/**
 * 块级还原：把以空格连接的 token 流重新切分成行
 */
function splitBlockLine(line) {
  const queue = line.split(/\s+/).filter(Boolean)
  const lines = []
  let cur = []

  const flush = () => {
    if (cur.length) lines.push(cur.join(' '))
    cur = []
  }

  while (queue.length) {
    const t = queue.shift()
    const head = cur[0] || ''
    const inQuote = head.startsWith('>')

    if (HEADING.test(t)) {
      flush()
      const [title] = splitHeading(queue, t.length)
      lines.push([t, ...title].join(' '))
      // 标题词已消费，剩余 token 留在队列里继续按块解析
      queue.splice(0, title.length)
      continue
    }
    if (t === '>') { flush(); cur.push('>'); continue }
    if (HR.test(t)) { flush(); lines.push(t); continue }
    if (BULLET.test(t) && !inQuote) { flush(); cur.push('-'); continue }
    if (ORDERED.test(t) && !inQuote) { flush(); cur.push(t); continue }

    cur.push(t)
  }
  flush()
  return lines
}

function blockType(line) {
  if (HEADING.test(line.split(' ')[0])) return 'heading'
  if (line.startsWith('>')) return 'quote'
  if (HR.test(line)) return 'hr'
  if (isTableLine(line)) return 'table'
  if (BULLET.test(line.split(' ')[0])) return 'list'
  if (ORDERED.test(line.split(' ')[0])) return 'list'
  return 'text'
}

/** 相邻不同类型块之间补空行，避免引用/列表把后文吞进去 */
function joinBlocks(lines) {
  const out = []
  let lastType = ''

  for (const raw of lines) {
    const line = raw.trimEnd()

    if (!line.trim()) {
      if (out.length && out[out.length - 1] !== '') out.push('')
      lastType = ''
      continue
    }

    const type = blockType(line)
    if (out.length && out[out.length - 1] !== '') {
      const needBlank =
        lastType !== type ||
        type === 'hr' || type === 'heading' ||
        lastType === 'hr' || lastType === 'heading'
      if (needBlank) out.push('')
    }

    out.push(line)
    lastType = type
  }

  return out.join('\n')
}

/**
 * 将（可能被压平的）markdown 还原为规范 markdown 文本
 */
export function normalizeMarkdown(raw) {
  if (!raw) return ''

  let text = String(raw).replace(/\r\n?/g, '\n')
  text = unescapeEntities(text)

  // 含真实换行且形态正常，直接交给 markdown-it
  const lines = text.split('\n')
  const looksFlattened = lines.length <= 1 || text.length / Math.max(1, lines.length) > 400

  if (!looksFlattened) return text

  const fenceStore = []
  let body = protectFences(text, fenceStore)
  // 补齐标题标记后缺失的空格：###标题 -> ### 标题
  body = body.replace(/(^|\s)(#{1,6})(?=[^\s#])/g, '$1$2 ')
  body = reflowTables(body)

  const blocks = []
  for (const line of body.split('\n')) {
    if (!line.trim()) { blocks.push(''); continue }

    if (isTableLine(line)) {
      const trimmed = line.trim()
      const last = trimmed.lastIndexOf('|')
      // 表格行后面可能还粘着下一个块（标题/正文），拆出来
      if (last >= 0 && last < trimmed.length - 1) {
        blocks.push(trimmed.slice(0, last + 1))
        const rest = trimmed.slice(last + 1).trim()
        if (rest) blocks.push(...splitBlockLine(rest))
      } else {
        blocks.push(trimmed)
      }
      continue
    }

    blocks.push(...splitBlockLine(line))
  }

  return restoreFences(joinBlocks(blocks), fenceStore)
}

/**
 * 渲染文章正文，输出可直接 v-html 的 HTML
 */
export function renderArticleContent(raw) {
  if (!raw) return ''

  const text = String(raw)
  if (looksLikeHtml(text) && !looksLikeMarkdown(text)) return text

  return md.render(normalizeMarkdown(text))
}

/** 粗略判断是否更像 markdown：出现块级/行内标记 */
export function looksLikeMarkdown(text) {
  const s = unescapeEntities(text)
  return /(^|\s)#{1,6}\s/.test(s) ||
    /\*\*[^*\n]+\*\*/.test(s) ||
    /```/.test(s) ||
    /(^|\s)>\s/.test(s) ||
    /\]\(/.test(s) ||
    /(^|\n)\s*[-*]\s/.test(s)
}
