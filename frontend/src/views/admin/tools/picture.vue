<template>
  <div class="image-uploader">
    <div class="drop-area" :class="{ dragging: isDragging }" @dragover.prevent="isDragging = true"
      @dragleave.prevent="isDragging = false" @drop.prevent="handleDrop" @click="triggerFileInput">
      <input type="file" ref="fileInput" accept="image/*" @change="handleFileChange" style="display: none;" />
      <div v-if="loading" class="status">上传中...</div>
      <div v-else class="status">
        <p>将图片拖拽到此处，或<span>点击上传</span></p>
        <p class="tip">上传到后端存储（{{ isPrivate ? '私有' : '公开' }}）</p>
      </div>
    </div>

    <!-- 是否私有 -->
    <div class="privacy-row">
      <el-radio-group v-model="isPrivate">
        <el-radio :value="0">公开</el-radio>
        <el-radio :value="1">私有</el-radio>
      </el-radio-group>
    </div>

    <!-- 上传成功显示 URL -->
    <div v-if="uploadedUrl" class="upload-result">
      <p>上传成功：</p>
      <div v-if="uploadedThumb" class="thumb-preview">
        <img :src="uploadedThumb" alt="缩略图" />
        <span>缩略图</span>
      </div>
      <div class="url-box">
        <input type="text" readonly :value="uploadedUrl" ref="urlRef" />
        <button @click="copyToClipboard">复制</button>
      </div>
    </div>

    <!-- 错误信息 -->
    <div v-if="errorMessage" class="error-msg">
      {{ errorMessage }}
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue';
import { uploadImage } from '@/api/upload';

const fileInput = ref(null);
const urlRef = ref(null);
const loading = ref(false);
const isDragging = ref(false);
const uploadedUrl = ref('');
const uploadedThumb = ref('');
const errorMessage = ref('');
const isPrivate = ref(0);

function triggerFileInput() {
  fileInput.value.click();
}

function handleFileChange(e) {
  const file = e.target.files[0];
  if (file) performUpload(file);
}

function handleDrop(e) {
  isDragging.value = false;
  const file = e.dataTransfer.files[0];
  if (file && file.type.startsWith('image/')) {
    performUpload(file);
  }
}

// 从后端返回的 ResultObject.data 中提取图片 URL（兼容多种返回形态）
function extractUrl(data) {
  if (!data) return '';
  if (typeof data === 'string') return data;                 // 直接返回 URL
  if (typeof data.url === 'string') return data.url;         // { url }
  if (data.data && typeof data.data.url === 'string') return data.data.url; // { data: { url } }
  if (typeof data.path === 'string') return data.path;       // { path }
  return '';
}

// 提取缩略图 URL
function extractThumb(data) {
  if (!data) return '';
  if (typeof data.thumbnail_url === 'string') return data.thumbnail_url;
  if (data.data && typeof data.data.thumbnail_url === 'string') return data.data.thumbnail_url;
  if (data.data?.links && typeof data.data.links.thumbnail_url === 'string') return data.data.links.thumbnail_url;
  return '';
}

// 执行上传（走后端 /api/upload/image）
async function performUpload(file) {
  if (!file) return;

  loading.value = true;
  errorMessage.value = '';
  uploadedUrl.value = '';
  uploadedThumb.value = '';

  try {
    const res = await uploadImage(file, isPrivate.value)
    const url = extractUrl(res)
    if (url) {
      uploadedUrl.value = url
      uploadedThumb.value = extractThumb(res)
    } else {
      errorMessage.value = '上传成功但未返回图片地址，请检查后端接口返回格式'
    }
  } catch (error) {
    errorMessage.value = error?.message || '上传失败，请稍后重试';
    console.error(error);
  } finally {
    loading.value = false;
  }
}

// 复制链接方法（包含现代 API 兼容处理）
async function copyToClipboard() {
  if (urlRef.value) {
    try {
      if (navigator.clipboard && navigator.clipboard.writeText) {
        await navigator.clipboard.writeText(urlRef.value.value);
        alert('链接已复制到剪贴板');
      } else {
        urlRef.value.select();
        document.execCommand('copy');
        alert('链接已复制到剪贴板');
      }
    } catch (err) {
      alert('复制失败，请手动选择复制');
    }
  }
}
</script>

<style scoped>
.image-uploader {
  max-width: 500px;
  margin: 20px auto;
  font-family: sans-serif;
}

.drop-area {
  border: 2px dashed #ccc;
  border-radius: 8px;
  padding: 40px 20px;
  text-align: center;
  cursor: pointer;
  background-color: #fafafa;
  transition: background-color 0.2s, border-color 0.2s;
}

.drop-area.dragging {
  border-color: #409eff;
  background-color: #ecf5ff;
}

.drop-area span {
  color: #409eff;
}

.tip {
  font-size: 12px;
  color: #909399;
  margin-top: 8px;
}

.privacy-row {
  margin-top: 16px;
  text-align: center;
}

.upload-result {
  margin-top: 20px;
}

.thumb-preview {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 10px;

  img {
    max-width: 120px;
    border-radius: 6px;
    border: 1px solid #e2e8f0;
  }

  span {
    font-size: 12px;
    color: #909399;
  }
}

.url-box {
  display: flex;
  gap: 10px;
}

.url-box input {
  flex: 1;
  padding: 8px;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
}

.url-box button {
  padding: 0 15px;
  background-color: #409eff;
  color: white;
  border: none;
  border-radius: 4px;
  cursor: pointer;
}

.error-msg {
  margin-top: 15px;
  color: #f56c6c;
  font-size: 14px;
}
</style>
