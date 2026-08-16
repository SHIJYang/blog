<template>
  <div class="agnes-image">
    <h2 class="page-title">Agnes Image V2.0 图片生成</h2>

    <!-- API Key 配置 -->
    <el-card class="section-card" shadow="never">
      <template #header><span>API Key 配置</span></template>
      <div class="config-row">
        <el-input v-model="apiKeyInput" type="password" show-password placeholder="请输入 Agnes AI API Key"
          class="key-input" @keyup.enter="saveApiKey" />
        <el-button type="primary" :loading="savingKey" @click="saveApiKey">保存 Key</el-button>
        <el-tag :type="hasApiKey ? 'success' : 'warning'" size="large">
          {{ hasApiKey ? 'Key 已配置' : '未配置' }}
        </el-tag>
        <el-button v-if="hasApiKey" type="danger" plain :loading="savingKey" @click="clearApiKey">清除 Key</el-button>
      </div>
      <p class="tip">纯前端直连 Agnes API，Key 仅保存在浏览器 localStorage。</p>
    </el-card>

    <!-- 生成参数 -->
    <el-card class="section-card" shadow="never">
      <template #header><span>生成参数</span></template>
      <el-form label-width="110px" label-position="left">
        <el-form-item label="生成模式">
          <el-radio-group v-model="mode" @change="onModeChange">
            <el-radio value="t2i">文生图</el-radio>
            <el-radio value="i2i">图生图</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="mode === 't2i'">
          <el-form-item label="提示词">
            <el-input v-model="prompt" type="textarea" :rows="5"
              placeholder="描述你要生成的图片，例如：A cinematic shot of a cat walking on the beach at sunset" />
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="提示词">
            <el-input v-model="prompt" type="textarea" :rows="3"
              placeholder="描述图像编辑指令，例如：add sunglasses, change background to forest" />
          </el-form-item>
          <el-form-item label="输入图片">
            <div class="image-list">
              <div v-for="(img, i) in imageUrlsInput" :key="i" class="image-row">
                <el-input v-model="imageUrlsInput[i]" placeholder="图片 URL 或 Base64" />
                <el-button v-if="imageUrlsInput.length > 1" type="danger" circle @click="imageUrlsInput.splice(i, 1)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
              <el-button type="primary" plain @click="imageUrlsInput.push('')">添加图片</el-button>
            </div>
          </el-form-item>
        </template>

        <el-form-item label="尺寸档位">
          <el-select v-model="size" style="width: 120px" @change="onSizeChange">
            <el-option label="1K (推荐)" value="1K" />
            <el-option label="2K" value="2K" />
            <el-option label="3K" value="3K" />
            <el-option label="4K" value="4K" />
            <el-option label="1024x1024" value="1024x1024" />
            <el-option label="1024x1792" value="1024x1792" />
            <el-option label="1792x1024" value="1792x1024" />
          </el-select>
        </el-form-item>

        <el-form-item label="宽高比" v-if="['1K', '2K', '3K', '4K'].includes(size)">
          <el-select v-model="ratio" style="width: 120px">
            <el-option label="1:1 方形" value="1:1" />
            <el-option label="3:4 竖版" value="3:4" />
            <el-option label="4:3 横版" value="4:3" />
            <el-option label="9:16 竖版长" value="9:16" />
            <el-option label="16:9 横版长" value="16:9" />
            <el-option label="2:3" value="2:3" />
            <el-option label="3:2" value="3:2" />
            <el-option label="21:9 超宽" value="21:9" />
          </el-select>
        </el-form-item>

        <el-form-item label="返回格式">
          <el-radio-group v-model="responseFormat">
            <el-radio value="url">URL</el-radio>
            <el-radio value="b64_json">Base64</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="负向提示词">
          <el-input v-model="negativePrompt" type="textarea" :rows="2"
            placeholder="需要避免的内容，如：blurry, low quality, distorted" />
        </el-form-item>
      </el-form>

      <div class="action-row">
        <el-button type="primary" size="large" :loading="creating" :disabled="!canCreate" @click="createTask">
          {{ creating ? '生成中…' : '🎨 创建生成任务' }}
        </el-button>
        <el-button size="large" @click="resetForm">重置</el-button>
      </div>
    </el-card>

    <!-- 任务与结果 -->
    <el-card class="section-card" shadow="never">
      <template #header><span>任务与结果</span></template>
      <div v-if="task" class="task-info">
        <div class="task-status">
          <el-tag :type="statusTagType" size="large">{{ task.status }}</el-tag>
          <span class="task-id">request_id: {{ task.id }}</span>
        </div>

        <div v-if="task.status === 'pending' || task.status === 'in_progress'" class="progress-bar">
          <span class="tip">图片生成中，完成后自动展示…</span>
          <el-button type="primary" plain :loading="polling" @click="refreshCurrent">刷新状态</el-button>
        </div>

        <div v-if="task.status === 'completed' && imageUrls.length" class="image-result">
          <div class="image-grid">
            <div v-for="(item, i) in imageUrls" :key="i" class="image-item">
              <img v-if="getImageSrc(item)" :src="getImageSrc(item)" :alt="`生成图片 ${i + 1}`" 
                class="result-image" @click="previewImage(item)" />
              <div v-else class="image-placeholder">加载中...</div>
              <div class="image-actions">
                <el-button size="small" @click="downloadImage(item, i)">下载</el-button>
                <el-button size="small" @click="copyImageUrl(item)">复制链接</el-button>
              </div>
            </div>
          </div>
        </div>

        <el-alert v-if="task.status === 'failed'" type="error"
          :title="task.error?.message || '生成失败'" show-icon :closable="false" />
      </div>
      <el-empty v-else description="还没有任务，填写上方参数开始生成" />
    </el-card>

    <!-- 历史任务 -->
    <el-card v-if="history.length" class="section-card" shadow="never">
      <template #header><span>历史任务</span></template>
      <el-table :data="history" size="small">
        <el-table-column prop="created_at" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column prop="mode" label="模式" width="80" />
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 'completed' ? 'success' : row.status === 'failed' ? 'danger' : 'info'">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="prompt" label="提示词" show-overflow-tooltip />
        <el-table-column label="操作" width="140">
          <template #default="{ row }">
            <el-button v-if="row.status === 'completed' && row.imageUrls?.length" link type="primary" 
              @click="playHistory(row)">查看</el-button>
            <el-button v-else-if="['pending', 'in_progress'].includes(row.status)" link type="primary" 
              @click="refreshHistory(row)">刷新</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Delete } from '@element-plus/icons-vue';

const API_BASE = 'https://api.agnes-ai.cn';
const API_KEY_STORAGE = 'agnes_api_key';
const MODEL = 'agnes-image-2.1-flash';

function getApiKey() { return localStorage.getItem(API_KEY_STORAGE) || 'sk-2gaqtdDxCFtc5Azoc9AWXgqpXuKSM6qAfKmJHh8OHItygW16'; }

const mode = ref('t2i');
const prompt = ref('');
const imageUrlsInput = ref(['']);
const size = ref('1K');
const ratio = ref('1:1');
const responseFormat = ref('b64_json');
const negativePrompt = ref('');

const creating = ref(false);
const polling = ref(false);
const savingKey = ref(false);
const apiKeyInput = ref('');
const task = ref(null);
const imageUrls = ref([]);
const history = ref([]);
const hasApiKey = ref(false);

const canCreate = computed(() => {
  if (!prompt.value.trim()) return false;
  if (mode.value === 'i2i' && imageUrlsInput.value.every(u => !u.trim())) return false;
  return true;
});

const statusTagType = computed(() => {
  const map = { completed: 'success', failed: 'danger', in_progress: 'warning', pending: 'info' };
  return map[task.value?.status] || 'info';
});

async function saveApiKey() {
  const key = apiKeyInput.value.trim();
  if (!key) { ElMessage.warning('请输入 API Key'); return; }
  localStorage.setItem(API_KEY_STORAGE, key);
  hasApiKey.value = true;
  apiKeyInput.value = '';
  ElMessage.success('API Key 已保存');
}

function clearApiKey() {
  localStorage.removeItem(API_KEY_STORAGE);
  hasApiKey.value = false;
  apiKeyInput.value = '';
  ElMessage.success('API Key 已清除');
}

async function agnesFetch(path, options = {}) {
  const key = getApiKey();
  if (!key) throw new Error('请先配置 Agnes API Key');
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 30000);
  try {
    const res = await fetch(`${API_BASE}${path}`, {
      ...options,
      signal: controller.signal,
      headers: { 'Authorization': `Bearer ${key}`, 'Content-Type': 'application/json', ...(options.headers || {}) }
    });
    const text = await res.text();
    let body;
    try { body = text ? JSON.parse(text) : null; } catch { body = text; }
    if (!res.ok) throw new Error(body?.message || `HTTP ${res.status}`);
    return body;
  } catch (error) {
    if (error.name === 'AbortError') throw new Error('请求超时（30s）');
    throw error;
  } finally { clearTimeout(timer); }
}

function onModeChange() {
  if (mode.value === 'i2i' && imageUrlsInput.value.length < 1) {
    imageUrlsInput.value = [''];
  }
}

function onSizeChange() {
  if (['1K', '2K', '3K', '4K'].includes(size.value)) ratio.value = '1:1';
}

function resetForm() {
  stopPolling();
  mode.value = 't2i';
  prompt.value = '';
  imageUrlsInput.value = [''];
  size.value = '1K';
  ratio.value = '1:1';
  responseFormat.value = 'b64_json';
  negativePrompt.value = '';
  task.value = null;
  imageUrls.value = [];
}

function buildPayload() {
  const payload = { model: MODEL, prompt: prompt.value.trim(), size: size.value };
  if (mode.value === 'i2i') {
    const images = imageUrlsInput.value.map(u => u.trim()).filter(Boolean);
    if (images.length > 0) payload.image = images;
  }
  if (['1K', '2K', '3K', '4K'].includes(size.value)) payload.ratio = ratio.value;
  if (negativePrompt.value.trim()) payload.negative_prompt = negativePrompt.value.trim();
  if (responseFormat.value === 'b64_json') payload.extra_body = { response_format: 'b64_json' };
  return payload;
}

let pollTimer = null;
const POLL_MAX_MS = 5 * 60 * 1000;
let pollStartTime = 0;

function stopPolling() {
  if (pollTimer) { clearTimeout(pollTimer); pollTimer = null; }
  polling.value = false;
}

function startPolling(target) {
  if (!target?.id) return;
  stopPolling();
  pollStartTime = Date.now();
  pollOnce(target);
}

async function pollOnce(target) {
  polling.value = true;
  try {
    const result = await agnesFetch(`/v1/images/generations/${encodeURIComponent(target.id)}`);
    if (task.value?.id === target.id) {
      task.value = { ...result };
      imageUrls.value = extractImageUrls(result);
    }
    const existingIdx = history.value.findIndex(h => h.id === target.id);
    const record = { ...result, created_at: result.created_at || Date.now() / 1000, mode: target.mode || mode.value, 
      prompt: result.prompt || target.prompt || prompt.value, imageUrls: extractImageUrls(result) };
    if (existingIdx >= 0) history.value[existingIdx] = record;
    else history.value.unshift(record);
    if (result.status === 'completed') { ElMessage.success('图片生成完成！'); stopPolling(); }
    else if (result.status === 'failed') { ElMessage.error(result.error?.message || '生成失败'); stopPolling(); }
    else if (Date.now() - pollStartTime > POLL_MAX_MS) { ElMessage.warning('任务超时（5分钟）'); stopPolling(); }
    else pollTimer = setTimeout(() => pollOnce(target), 3000);
  } catch (error) {
    console.error(error);
    ElMessage.error('查询失败：' + (error?.message || '请检查 API Key'));
    stopPolling();
  } finally { polling.value = false; }
}

function extractImageUrls(data) {
  if (!data?.data) return [];
  return data.data.map(img => ({ url: img.url || '', base64: img.b64_json || '', blobUrl: null }))
    .filter(item => item.url || item.base64);
}

// 获取图片源（自动转换 blob URL）
function getImageSrc(item) {
  if (item.blobUrl) return item.blobUrl;
  if (item.base64) return `data:image/png;base64,${item.base64}`;
  return '';
}

async function loadImageBlob(item) {
  if (item.blobUrl) return item.blobUrl;
  if (item.base64) {
    item.blobUrl = `data:image/png;base64,${item.base64}`;
    return item.blobUrl;
  }
  if (item.url) {
    try {
      const key = getApiKey();
      if (!key) {
        ElMessage.warning('请先配置 Agnes API Key');
        return item.url;
      }
      const res = await fetch(item.url, { headers: { 'Authorization': `Bearer ${key}` } });
      if (res.ok) {
        const blob = await res.blob();
        item.blobUrl = URL.createObjectURL(blob);
        return item.blobUrl;
      }
      // 认证失败，返回原始 URL（可能需要在浏览器中手动登录）
      console.error('Failed to load image, HTTP', res.status);
      ElMessage.error('图片加载失败，请检查 API Key 是否有效');
      return item.url;
    } catch (e) {
      console.error('Failed to load image:', e);
      return item.url;
    }
  }
  return '';
}

function refreshCurrent() { if (task.value?.id) startPolling(task.value); }
function refreshHistory(row) { if (row?.id) startPolling(row); }

async function createTask() {
  if (creating.value) return;
  creating.value = true;
  try {
    const data = await agnesFetch('/v1/images/generations', { method: 'POST', body: JSON.stringify(buildPayload()) });
    task.value = { ...data, mode: mode.value, prompt: prompt.value.trim() };
    const urls = extractImageUrls(data);
    if (urls.length > 0) {
      imageUrls.value = urls;
      task.value.status = 'completed';
      history.value.unshift({ ...data, created_at: Date.now() / 1000, mode: mode.value, prompt: prompt.value.trim(), imageUrls: urls });
      ElMessage.success('图片生成完成！');
    } else {
      imageUrls.value = [];
      ElMessage.success('任务已创建，正在生成…');
      startPolling(task.value);
    }
  } catch (error) {
    console.error(error);
    ElMessage.error('创建失败：' + (error?.message || '请检查 API Key'));
  } finally { creating.value = false; }
}

function playHistory(row) { task.value = row; imageUrls.value = row.imageUrls || []; }

async function downloadImage(item, index) {
  const src = await loadImageBlob(item);
  if (!src) { ElMessage.warning('图片加载失败'); return; }
  const a = document.createElement('a');
  a.href = src;
  a.download = `image_${index + 1}.png`;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
}

async function copyImageUrl(item) {
  try {
    await navigator.clipboard.writeText(item.url);
    ElMessage.success('链接已复制');
  } catch {
    ElMessage.warning('复制失败，请手动复制');
  }
}

async function previewImage(item) {
  const src = await loadImageBlob(item);
  if (src) window.open(src, '_blank');
}

function formatTime(ts) {
  if (!ts) return '-';
  const d = new Date(ts * 1000);
  const pad = n => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

onMounted(() => { hasApiKey.value = !!getApiKey(); });
onUnmounted(() => { stopPolling(); });
</script>

<style scoped lang="scss">
.agnes-image { max-width: 900px; margin: 0 auto; padding: 20px;
  .page-title { margin-bottom: 20px; font-size: 22px; font-weight: 600; }
  .section-card { margin-bottom: 20px; border-radius: 8px; }
  .config-row { display: flex; gap: 12px; align-items: center; flex-wrap: wrap; .key-input { max-width: 420px; } }
  .tip { font-size: 12px; color: #909399; margin-left: 6px; }
  .action-row { margin-top: 8px; display: flex; gap: 12px; flex-wrap: wrap; }
  .image-list { width: 100%; }
  .image-row { display: flex; gap: 8px; margin-bottom: 8px; align-items: center; }
  .task-info .task-status { display: flex; align-items: center; gap: 12px; margin-bottom: 12px; }
  .task-id { font-family: monospace; color: #606266; font-size: 13px; }
  .progress-bar { display: flex; align-items: center; gap: 16px; padding: 4px 0; }
  .image-result .image-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; margin-bottom: 16px; }
  .image-item { border-radius: 8px; overflow: hidden; border: 1px solid #e4e7ed; background: #f5f7fa;
    .result-image { width: 100%; height: 280px; object-fit: contain; cursor: zoom-in; transition: transform 0.2s; &:hover { transform: scale(1.02); } }
    .image-placeholder { height: 280px; display: flex; align-items: center; justify-content: center; color: #909399; }
    .image-actions { display: flex; gap: 8px; padding: 10px; background: #f5f7fa; }
  }
}
</style>
