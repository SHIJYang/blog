<!-- src/components/AiEditor.vue -->
<template>
  <div class="aieditor-container">
    <div ref="editorRef" class="aieditor-mount"></div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { AiEditor } from 'aieditor'
import 'aieditor/dist/style.css'

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: '开始你的创作...'
  },
  height: {
    type: String,
    default: '100%'
  }
})

const emit = defineEmits(['update:modelValue'])

const editorRef = ref(null)
let aiEditor = null

const AGNES_API_KEY = 'sk-gnwF7Jzc7ETWgLzTp73Xf0KrnCGPlPnPvEsMl56gweMWdoc7'
const AGNES_ENDPOINT = 'https://apihub.agnes-ai.com/v1/chat/completions'
const AGNES_MODEL = 'agnes-3.0-flash'

onMounted(() => {
  aiEditor = new AiEditor({
    element: editorRef.value,
    placeholder: props.placeholder,
    content: props.modelValue,
    height: props.height,
    onChange: (editor) => {
      emit('update:modelValue', editor.getHtml())
    },
    ai: {
      models: {
        custom: {
          url: AGNES_ENDPOINT,

          headers: () => ({
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${AGNES_API_KEY}`
          }),

          // 将 AiEditor 传入的 prompt 转为 Agnes 请求体
          // Agnes 2.0 Flash 格式完全兼容 OpenAI
          wrapPayload: (message) => {
            return JSON.stringify({
              model: AGNES_MODEL,
              stream: true,
              temperature: 0.7,
              max_tokens: 4096,
              messages: [
                {
                  role: 'system',
                  content: [
                    '你是一位专业的中文写作助手。',
                    '请根据用户的指令处理以下内容。',
                    '要求：',
                    '1. 直接输出结果，不要添加解释说明',
                    '2. 保持原文的核心意思不变',
                    '3. 使用流畅自然的中文',
                    '4. 如果是润色/改写，保持与原文相近的篇幅'
                  ].join('\n')
                },
                {
                  role: 'user',
                  content: message
                }
              ]
            })
          },

          // 解析 Agnes 返回的 OpenAI SSE 流式数据
          // SSE 格式: data: {"id":"...","choices":[{"index":0,"delta":{"content":"字"},"finish_reason":null}]}
          parseMessage: (message) => {
            // 处理 [DONE] 信号
            if (typeof message === 'string' && message.trim() === '[DONE]') {
              return { role: 'assistant', content: '', index: 0, status: 2 }
            }

            try {
              const data = typeof message === 'string' ? JSON.parse(message) : message
              const choice = data.choices?.[0]
              if (!choice) {
                return { role: 'assistant', content: '', index: 0, status: 1 }
              }

              const content = choice.delta?.content || ''
              const isFinish = choice.finish_reason === 'stop'

              return {
                role: 'assistant',
                content: content,
                index: choice.index ?? 0,
                status: isFinish ? 2 : 1
              }
            } catch {
              return {
                role: 'assistant',
                content: typeof message === 'string' ? message : '',
                index: 0,
                status: 1
              }
            }
          }
        }
      }
    }
  })
})

onUnmounted(() => {
  aiEditor?.destroy()
  aiEditor = null
})

watch(
  () => props.modelValue,
  (newVal) => {
    if (aiEditor && newVal !== aiEditor.getHtml()) {
      aiEditor.setContent(newVal || '')
    }
  }
)

defineExpose({
  getHtml: () => aiEditor?.getHtml() || '',
  getText: () => aiEditor?.getText() || '',
  getJson: () => aiEditor?.getJSON() || null,
  setContent: (content) => aiEditor?.setContent(content || ''),
  clear: () => aiEditor?.clear(),
  focus: () => aiEditor?.focus(),
  insertHtml: (html) => aiEditor?.insertHtml(html),
  getAiEditor: () => aiEditor
})
</script>

<style scoped lang="scss">
.aieditor-container {
  width: 100%;
  height: 100%;

  .aieditor-mount {
    width: 100%;
    height: 100%;
  }

  :deep(.aie-container) {
    border: none !important;
    border-radius: 0 !important;
    background-color: #ffffff;

    .aie-toolbar {
      border-bottom: 1px solid #f3f4f6;
      border-radius: 0;
      background-color: #fafafa;
    }

    .aie-content {
      padding: 16px 24px;
      font-size: 1rem;
      line-height: 1.8;
      color: #1f2937;
      min-height: 400px;

      p {
        margin-bottom: 0.75em;
      }

      h1,
      h2,
      h3,
      h4 {
        color: #111827;
        font-weight: 700;
        margin-top: 1.5em;
        margin-bottom: 0.5em;
      }

      blockquote {
        border-left: 3px solid #d1d5db;
        padding-left: 16px;
        color: #6b7280;
        font-style: italic;
      }

      code {
        background-color: #f3f4f6;
        padding: 2px 6px;
        border-radius: 4px;
        font-size: 0.9em;
      }

      pre {
        background-color: #1f2937;
        color: #e5e7eb;
        border-radius: 8px;
        padding: 16px;
        overflow-x: auto;
      }

      img {
        max-width: 100%;
        border-radius: 8px;
      }
    }
  }
}
</style>