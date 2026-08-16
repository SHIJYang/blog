<template>
  <div class="resume-page" ref="containerRef">
    <!-- Hero -->
    <header class="resume-hero">
      <p class="resume-hero__kicker">RESUME / 简历</p>
      <h1 class="resume-hero__title">
        <span class="word" v-for="(w, i) in titleWords" :key="i">{{ w }}</span>
      </h1>
      <p class="resume-hero__sub">用大模型与工程化思维，构建真正可用的智能产品</p>
    </header>

    <div class="bento-grid">
      <!-- 1. 个人名片 + 社交 -->
      <div class="bento-item profile-slot animate-box">
        <info />
      </div>
      <div class="bento-item social-slot animate-box">
        <Social />
      </div>

      <!-- 2. AI 专注定位卡 -->
      <div class="bento-item ai-focus animate-box">
        <span class="ai-focus__tag"><span class="pulse-dot"></span>当前专注</span>
        <h3 class="ai-focus__title">LLM 应用开发</h3>
        <p class="ai-focus__desc">
          大模型 API 集成 · Prompt 工程 · RAG 检索增强 · AI Agent 编排，把模型能力落到真实产品里。
        </p>
        <div class="ai-focus__stack">
          <span>OpenAI / DeepSeek</span>
          <span>LangChain</span>
          <span>MCP</span>
          <span>Function Calling</span>
          <span>向量数据库</span>
        </div>
      </div>

      <!-- 3. AI 技术栈（分组） -->
      <div class="bento-item skills-card animate-box">
        <h3 class="bento-title">技术武器库</h3>

        <div class="skill-group">
          <p class="skill-group__label">LLM / Agent</p>
          <div class="skill-tags">
            <span class="skill-pill ai">LangChain / LangGraph</span>
            <span class="skill-pill ai">RAG 检索增强</span>
            <span class="skill-pill ai">Prompt Engineering</span>
            <span class="skill-pill ai">Embedding</span>
            <span class="skill-pill ai">MCP 协议</span>
            <span class="skill-pill ai">HuggingFace</span>
            <span class="skill-pill ai">Fine-tuning</span>
          </div>
        </div>

        <div class="skill-group">
          <p class="skill-group__label">前端工程</p>
          <div class="skill-tags">
            <span class="skill-pill">Vue 3 / Vue 2</span>
            <span class="skill-pill">React</span>
            <span class="skill-pill">TypeScript</span>
            <span class="skill-pill">Vite</span>
            <span class="skill-pill">UniApp</span>
            <span class="skill-pill">GSAP / TresJS</span>
          </div>
        </div>

        <div class="skill-group">
          <p class="skill-group__label">后端 / 数据</p>
          <div class="skill-tags">
            <span class="skill-pill">Python</span>
            <span class="skill-pill">SpringBoot</span>
            <span class="skill-pill">Node.js</span>
            <span class="skill-pill">MySQL</span>
            <span class="skill-pill">Redis</span>
            <span class="skill-pill">Milvus / FAISS</span>
          </div>
        </div>
      </div>

      <!-- 4. 成长轨迹（跨2列） -->
      <div class="bento-item experience-card animate-box">
        <h3 class="bento-title">成长轨迹</h3>

        <div class="custom-timeline">
          <!-- 邻米科技 -->
          <div class="timeline-item">
            <div class="time-meta">
              <span class="badge active">AI 产品实习</span>
              <span class="date">2025.09 - 2026.01</span>
            </div>
            <div class="timeline-content">
              <h4>邻米科技 · AI 应用前端开发实习</h4>
              <ul class="bullets">
                <li>主导“<strong>环视译迅</strong>”AI 翻译产品前端架构，Vue3 + Vite 搭建性能级组件底座。</li>
                <li>对接 LLM 翻译引擎，负责<strong>流式输出</strong>与多模型<strong>路由切换</strong>的交互链路实现。</li>
                <li>参与翻译质量 Prompt 调优与多机型兼容优化，首屏渲染时延实测<strong>缩减 20%</strong>。</li>
              </ul>
            </div>
          </div>

          <!-- 软通动力 -->
          <div class="timeline-item">
            <div class="time-meta">
              <span class="badge">研发实践</span>
              <span class="date">2024.03 - 2024.08</span>
            </div>
            <div class="timeline-content">
              <h4>软通动力-上海杉达梦工厂 · 全栈开发实践</h4>
              <ul class="bullets">
                <li>参与设备管理中台开发，负责权限安全过滤与可视化监控看板。</li>
                <li>使用 Pinia 与本地并发分片控制，大幅缩短接口加载响应耗时。</li>
              </ul>
            </div>
          </div>

          <!-- 学校 -->
          <div class="timeline-item">
            <div class="time-meta">
              <span class="badge">学术背景</span>
              <span class="date">2022.09 - 至今</span>
            </div>
            <div class="timeline-content">
              <h4>上海杉达学院 · 计算机科学与技术（本科）</h4>
              <p class="bullets-desc">
                系统学习数据结构、操作系统与软件工程；课外科研方向聚焦
                <strong>LLM 应用开发</strong>（RAG、Agent、Prompt 工程）。
              </p>
            </div>
          </div>
        </div>
      </div>

      <!-- 5. AI 项目实战（全宽） -->
      <div class="bento-item projects-card animate-box">
        <h3 class="bento-title">AI 实战成果</h3>

        <div class="projects-grid">
          <div class="project-box">
            <div class="proj-badge ai">LLM 集成</div>
            <h4>环视译迅 · AI 翻译助手</h4>
            <p>
              集成多模型 LLM 翻译引擎，实现流式译文渲染、语种自动识别与
              多模型路由降本；Prompt 分层调优提升专有名词翻译准确率。
            </p>
            <div class="proj-stack">Vue3 · Vite · LLM API · SSE</div>
          </div>

          <div class="project-box">
            <div class="proj-badge ai">RAG</div>
            <h4>个人知识库问答系统</h4>
            <p>
              文档解析 → 切片 → Embedding 入库 → 向量检索 → LLM 生成，
              搭建完整 RAG 链路；支持流式问答与引用溯源。
            </p>
            <div class="proj-stack">Python · LangChain · Milvus · DeepSeek</div>
          </div>

          <div class="project-box">
            <div class="proj-badge ai">AI Agent</div>
            <h4>智能行程规划 Agent</h4>
            <p>
              基于 Function Calling 实现工具调用：天气、地图、票务 API
              自主编排，多轮对话中动态规划行程并给出理由。
            </p>
            <div class="proj-stack">LangGraph · Function Calling · 工具链</div>
          </div>

          <div class="project-box">
            <div class="proj-badge">全栈</div>
            <h4>食堂订餐系统</h4>
            <p>
              基于 Vue2 + SpringBoot，负责数据库 ER 设计、订单生命周期
              控制在内的完整业务闭环。
            </p>
            <div class="proj-stack">Vue2 · SpringBoot · MySQL</div>
          </div>
        </div>
      </div>

      <!-- 6. 核心自驱特质（全宽） -->
      <div class="bento-item traits-card animate-box">
        <h3 class="bento-title">核心自驱特质</h3>
        <div class="traits-body">
          <p class="traits-quote">“不只会调 API —— 理解模型能力边界，用工程化手段把 AI 变成可靠的产品。”</p>
          <div class="traits-list">
            <span class="trait-pill">✦ LLM 全链路交付</span>
            <span class="trait-pill">✦ Prompt 工程调优</span>
            <span class="trait-pill">✦ 前端工程级生态</span>
            <span class="trait-pill">✦ 跨端多端架构</span>
            <span class="trait-pill">✦ 性能深度审计</span>
          </div>
        </div>
      </div>
    </div>

    <footer class="resume-footer">
      <span>📢 保持对技术的专注与热爱 · 景阳的技术笔记</span>
    </footer>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { gsap } from 'gsap'
import info from '../self/info.vue'
import Social from '../self/Social.vue'

const containerRef = ref<HTMLElement | null>(null)

const titleWords = ['AI', '应用开发']

onMounted(() => {
  // Hero 入场
  gsap.timeline({ delay: 0.1 })
    .from('.resume-hero__kicker', { y: 14, opacity: 0, duration: 0.5, ease: 'power3.out' })
    .from('.resume-hero__title .word', {
      y: 26, opacity: 0, duration: 0.7, stagger: 0.1, ease: 'power3.out'
    }, '-=0.25')
    .from('.resume-hero__sub', { y: 12, opacity: 0, duration: 0.5 }, '-=0.35')

  // Bento 卡片交错入场
  gsap.fromTo('.animate-box',
    { opacity: 0, y: 32, rotateX: -4 },
    {
      opacity: 1,
      y: 0,
      rotateX: 0,
      stagger: 0.08,
      duration: 0.65,
      ease: 'power3.out',
      transformOrigin: 'top center',
      delay: 0.35
    }
  )
})
</script>

<style scoped lang="scss">
/* ============ 浅色主题变量（与博客页一致） ============ */
.resume-page {
  --rs-bg: #f8f6f1;
  --rs-surface: #ffffff;
  --rs-surface-soft: #fbfaf7;
  --rs-accent: #b8860b;
  --rs-accent-soft: #f3ecd9;
  --rs-text: #232019;
  --rs-text-secondary: #6b675f;
  --rs-text-muted: #a29d92;
  --rs-line: #e9e6e0;
  --rs-shadow: 0 1px 2px rgba(0, 0, 0, 0.04), 0 4px 12px rgba(0, 0, 0, 0.03);
  --rs-shadow-hover: 0 14px 34px -10px rgba(120, 90, 20, 0.14);

  max-width: 1080px;
  margin: 0 auto;
  padding: 32px 24px 60px;
  min-height: 100vh;
  background: var(--rs-bg);
  color: var(--rs-text);
  perspective: 1200px;
}

/* ================= Hero ================= */
.resume-hero {
  padding: 40px 0 36px;
  text-align: center;
}

.resume-hero__kicker {
  font-family: var(--font-mono);
  font-size: 0.72rem;
  letter-spacing: 0.4em;
  text-transform: uppercase;
  color: var(--rs-accent);
  margin: 0 0 16px;
}

.resume-hero__title {
  font-family: var(--font-display);
  font-size: clamp(2.4rem, 5.5vw, 3.4rem);
  font-weight: 600;
  letter-spacing: 0.02em;
  line-height: 1.12;
  color: var(--rs-text);
  margin: 0 0 14px;
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 0.14em;
}

.word {
  display: inline-block;
  opacity: 0;
  transform: translateY(26px);

  &:first-child {
    color: var(--rs-accent);
  }
}

.resume-hero__sub {
  font-family: var(--font-mono);
  font-size: 0.8rem;
  letter-spacing: 0.12em;
  color: var(--rs-text-muted);
  margin: 0;
}

/* ================= Bento 网格 ================= */
.bento-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
}

.bento-item {
  background: var(--rs-surface);
  border: 1px solid var(--rs-line);
  border-radius: 20px;
  padding: 24px;
  box-shadow: var(--rs-shadow);
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);

  &:hover {
    border-color: rgba(184, 134, 11, 0.35);
    box-shadow: var(--rs-shadow-hover);
    transform: translateY(-2px);
  }
}

.bento-title {
  font-family: var(--font-mono);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.16em;
  color: var(--rs-text-muted);
  margin: 0 0 18px;
  display: flex;
  align-items: center;
  gap: 8px;

  &::before {
    content: '';
    width: 14px;
    height: 2px;
    background: var(--rs-accent);
    border-radius: 2px;
  }
}

/* ---------- 1. 名片 / 社交 ---------- */
.profile-slot {
  grid-column: span 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18px;
}

.social-slot {
  grid-column: span 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 14px;
  background: var(--rs-surface-soft);
}

/* ---------- 2. AI 专注定位 ---------- */
.ai-focus {
  grid-column: span 1;
  display: flex;
  flex-direction: column;
  gap: 14px;
  background: linear-gradient(150deg, #fffdf6 0%, var(--rs-accent-soft) 100%);
  border-color: rgba(184, 134, 11, 0.25);
}

.ai-focus__tag {
  display: inline-flex;
  align-items: center;
  gap: 7px;
  align-self: flex-start;
  font-family: var(--font-mono);
  font-size: 0.68rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--rs-accent);
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid rgba(184, 134, 11, 0.3);
  padding: 4px 12px;
  border-radius: 999px;
}

.pulse-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #22c55e;
  animation: pulse 1.8s ease-in-out infinite;
}

@keyframes pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(34, 197, 94, 0.45); }
  50% { box-shadow: 0 0 0 5px rgba(34, 197, 94, 0); }
}

.ai-focus__title {
  font-family: var(--font-display);
  font-size: 1.6rem;
  font-weight: 600;
  color: var(--rs-text);
  margin: 0;
  line-height: 1.2;
}

.ai-focus__desc {
  font-size: 0.84rem;
  line-height: 1.7;
  color: var(--rs-text-secondary);
  margin: 0;
}

.ai-focus__stack {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: auto;

  span {
    font-size: 0.7rem;
    color: var(--rs-accent);
    background: rgba(255, 255, 255, 0.75);
    border: 1px solid rgba(184, 134, 11, 0.25);
    padding: 3px 10px;
    border-radius: 999px;
  }
}

/* ---------- 3. 技术栈 ---------- */
.skills-card {
  grid-column: span 1;
}

.skill-group {
  margin-bottom: 16px;

  &:last-child {
    margin-bottom: 0;
  }
}

.skill-group__label {
  font-family: var(--font-mono);
  font-size: 0.66rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--rs-text-muted);
  margin: 0 0 8px;
}

.skill-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.skill-pill {
  font-size: 0.74rem;
  font-weight: 500;
  color: var(--rs-text-secondary);
  background: rgba(0, 0, 0, 0.035);
  padding: 5px 11px;
  border-radius: 8px;
  border: 1px solid transparent;
  transition: all 0.2s;

  &:hover {
    background: var(--rs-accent-soft);
    color: var(--rs-accent);
    border-color: rgba(184, 134, 11, 0.3);
    transform: translateY(-1px);
  }

  &.ai {
    font-weight: 600;
    color: var(--rs-accent);
    background: var(--rs-accent-soft);
    border-color: rgba(184, 134, 11, 0.22);
  }
}

/* ---------- 4. 成长轨迹 ---------- */
.experience-card {
  grid-column: span 2;
}

.custom-timeline {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 148px 1fr;
  gap: 16px;
  position: relative;

  &::before {
    content: '';
    position: absolute;
    left: -10px;
    top: 6px;
    bottom: -20px;
    width: 2px;
    background: var(--rs-line);
  }

  &:last-child::before {
    display: none;
  }

  .time-meta {
    display: flex;
    flex-direction: column;
    gap: 5px;
    align-items: flex-start;

    .badge {
      font-family: var(--font-mono);
      font-size: 0.66rem;
      font-weight: 600;
      padding: 3px 10px;
      border-radius: 999px;
      background: #f1f5f9;
      color: var(--rs-text-secondary);

      &.active {
        background: var(--rs-accent-soft);
        color: var(--rs-accent);
      }
    }

    .date {
      font-family: var(--font-mono);
      font-size: 0.72rem;
      color: var(--rs-text-muted);
    }
  }

  .timeline-content {
    h4 {
      font-size: 0.92rem;
      font-weight: 700;
      color: var(--rs-text);
      margin: 0 0 6px;
    }

    .bullets {
      padding-left: 14px;
      margin: 0;

      li {
        font-size: 0.8rem;
        color: var(--rs-text-secondary);
        line-height: 1.6;
        margin-bottom: 3px;
      }
    }

    .bullets-desc {
      font-size: 0.8rem;
      color: var(--rs-text-secondary);
      margin: 0;
      line-height: 1.6;
    }

    strong {
      color: var(--rs-accent);
      font-weight: 600;
    }
  }
}

/* ---------- 5. AI 项目 ---------- */
.projects-card {
  grid-column: span 3;
}

.projects-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
}

.project-box {
  background: var(--rs-surface-soft);
  border: 1px solid var(--rs-line);
  padding: 18px;
  border-radius: 14px;
  display: flex;
  flex-direction: column;
  transition: all 0.25s;

  &:hover {
    background: #fff;
    border-color: rgba(184, 134, 11, 0.35);
    box-shadow: var(--rs-shadow-hover);
    transform: translateY(-3px);
  }

  .proj-badge {
    display: inline-block;
    align-self: flex-start;
    font-family: var(--font-mono);
    font-size: 0.62rem;
    font-weight: 600;
    letter-spacing: 0.1em;
    text-transform: uppercase;
    color: var(--rs-text-secondary);
    background: rgba(0, 0, 0, 0.04);
    padding: 3px 9px;
    border-radius: 6px;
    margin-bottom: 10px;

    &.ai {
      color: var(--rs-accent);
      background: var(--rs-accent-soft);
    }
  }

  h4 {
    font-size: 0.9rem;
    font-weight: 700;
    color: var(--rs-text);
    margin: 0 0 7px;
  }

  p {
    font-size: 0.76rem;
    color: var(--rs-text-secondary);
    line-height: 1.55;
    margin: 0 0 12px;
    flex: 1;
  }

  .proj-stack {
    font-family: var(--font-mono);
    font-size: 0.66rem;
    color: var(--rs-text-muted);
    padding-top: 10px;
    border-top: 1px dashed var(--rs-line);
  }
}

/* ---------- 6. 核心特质 ---------- */
.traits-card {
  grid-column: span 3;
  background: linear-gradient(120deg, #fffdf6 0%, #fbf7ec 100%);
  border-color: rgba(184, 134, 11, 0.2);
}

.traits-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.traits-quote {
  font-family: var(--font-display);
  font-size: 1.15rem;
  font-style: italic;
  font-weight: 500;
  color: var(--rs-text);
  margin: 0;
  line-height: 1.6;
}

.traits-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.trait-pill {
  font-size: 0.76rem;
  font-weight: 600;
  color: var(--rs-accent);
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid rgba(184, 134, 11, 0.25);
  padding: 6px 14px;
  border-radius: 999px;
}

/* ---------- 页脚 ---------- */
.resume-footer {
  text-align: center;
  padding: 36px 0 10px;
  color: var(--rs-text-muted);
  font-size: 0.76rem;
  font-family: var(--font-mono);
  letter-spacing: 0.05em;
}

/* ================= 响应式 ================= */
@media (max-width: 900px) {
  .bento-grid {
    grid-template-columns: 1fr 1fr;
  }

  .profile-slot,
  .social-slot,
  .ai-focus,
  .skills-card {
    grid-column: span 1;
  }

  .experience-card,
  .projects-card,
  .traits-card {
    grid-column: span 2;
  }

  .projects-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 640px) {
  .resume-page {
    padding: 16px 14px 48px;
  }

  .bento-grid {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .profile-slot,
  .social-slot,
  .ai-focus,
  .skills-card,
  .experience-card,
  .projects-card,
  .traits-card {
    grid-column: span 1;
  }

  .projects-grid {
    grid-template-columns: 1fr;
  }

  .timeline-item {
    grid-template-columns: 1fr;
    gap: 6px;

    &::before {
      display: none;
    }
  }
}
</style>
