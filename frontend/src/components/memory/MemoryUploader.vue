<!--
  记忆功能组件 1：MemoryUploader
  - 上传照片创建记忆
  - 支持点击选择 / 拖拽上传
  - 创建成功后 emit('created', note)，由父层决定进入对话并刷新列表
-->
<template>
  <div class="memory-uploader" :class="{ dragging: isDragging }" @dragover.prevent="isDragging = true"
    @dragleave.prevent="isDragging = false" @drop.prevent="handleDrop" @click="triggerFileInput">
    <input ref="fileInput" type="file" accept="image/*" style="display: none" @change="handleFileChange" />
    <div v-if="loading" class="upload-status">正在收纳这一刻…</div>
    <template v-else>
      <p class="upload-icon">🖼</p>
      <p class="upload-text">拖入或点击上传一张照片</p>
      <p class="upload-sub">拾光会为你记住这一刻</p>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createMemory } from '@/api/memory'

const emit = defineEmits<{
  (e: 'created', note: any): void
}>()

const fileInput = ref<HTMLInputElement | null>(null)
const isDragging = ref(false)
const loading = ref(false)

// 触发文件选择
function triggerFileInput() {
  fileInput.value?.click()
}

// 文件选择回调
function handleFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (file) performUpload(file)
  input.value = ''
}

// 拖拽上传
function handleDrop(e: DragEvent) {
  isDragging.value = false
  const file = e.dataTransfer?.files?.[0]
  if (file && file.type.startsWith('image/')) {
    performUpload(file)
  }
}

// 上传图片创建记忆
async function performUpload(file: File) {
  loading.value = true
  try {
    const note = await createMemory(file)
    ElMessage.success('时刻已收纳')
    emit('created', note)
  } catch (e: any) {
    ElMessage.error(e?.message || '上传失败')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
.memory-uploader {
  border: 2px dashed #cbd5e1;
  border-radius: 12px;
  padding: 28px 16px;
  text-align: center;
  cursor: pointer;
  background: #f8fafc;
  transition: all 0.2s;

  &.dragging {
    border-color: #2563eb;
    background: #eff6ff;
  }

  .upload-icon {
    font-size: 2rem;
    margin: 0 0 6px;
  }

  .upload-text {
    margin: 0;
    font-size: 0.95rem;
    color: #334155;
    font-weight: 500;
  }

  .upload-sub {
    margin: 4px 0 0;
    font-size: 0.78rem;
    color: #94a3b8;
  }

  .upload-status {
    color: #2563eb;
    font-size: 0.9rem;
    padding: 12px 0;
  }
}
</style>
