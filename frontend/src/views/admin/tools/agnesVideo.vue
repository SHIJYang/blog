<template>
  <div class="agnes-video">
    <h2 class="page-title">Agnes Video V2.0 视频生成</h2>

    <!-- API Key 配置（纯前端，存 localStorage） -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <span>API Key 配置</span>
      </template>
      <div class="config-row">
        <el-input v-model="apiKeyInput" type="password" show-password placeholder="请输入 Agnes AI API Key"
          class="key-input" @keyup.enter="saveApiKey" />
        <el-button type="primary" :loading="savingKey" @click="saveApiKey">保存 Key</el-button>
        <el-tag :type="hasApiKey ? 'success' : 'warning'" size="large">
          {{ hasApiKey ? 'Key 已配置' : '未配置' }}
        </el-tag>
        <el-button v-if="hasApiKey" type="danger" plain :loading="savingKey" @click="clearApiKey">清除 Key</el-button>
      </div>
      <p v-if="backendError" class="tip error-tip">{{ backendError }}</p>
      <p class="tip">纯前端直连 Agnes API（不需要后端），Key 仅保存在浏览器 localStorage，用于创建任务与查询结果。生成视频请保持本页打开，任务完成会自动拉取结果。</p>
    </el-card>

    <!-- 生成参数 -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <span>生成参数</span>
      </template>

      <el-form label-width="110px" label-position="left">
        <el-form-item label="生成模式">
          <el-radio-group v-model="mode" @change="onModeChange">
            <el-radio value="t2v">文生视频</el-radio>
            <el-radio value="i2v">图生视频</el-radio>
            <el-radio value="keyframes">关键帧动画</el-radio>
          </el-radio-group>
        </el-form-item>

        <!-- 文生视频 -->
        <template v-if="mode === 't2v'">
          <el-form-item label="提示词">
            <el-input v-model="prompt" type="textarea" :rows="5"
              placeholder="例如：A cinematic shot of a cat walking on the beach at sunset, soft ocean waves, warm golden lighting, realistic motion" />
          </el-form-item>
        </template>

        <!-- 图生视频 -->
        <template v-else-if="mode === 'i2v'">
          <el-form-item label="提示词">
            <el-input v-model="prompt" type="textarea" :rows="4" placeholder="描述图片中要发生的运动…" />
          </el-form-item>
          <el-form-item label="图片 URL">
            <el-input v-model="imageUrl" placeholder="https://… 可公开访问的图片地址" />
          </el-form-item>
          <el-form-item label="预览">
            <el-image v-if="imageUrl" :src="imageUrl" fit="contain" class="preview-img"
              :preview-src-list="[imageUrl]" />
            <span v-else class="tip">图生视频需要提供图片 URL</span>
          </el-form-item>
        </template>

        <!-- 关键帧动画 -->
        <template v-else>
          <el-form-item label="提示词">
            <el-input v-model="prompt" type="textarea" :rows="4"
              placeholder="描述关键帧之间的过渡动画，例如：camera zoom in, character turns around…" />
          </el-form-item>
          <el-form-item label="关键帧图片">
            <div class="keyframes-list">
              <div v-for="(frame, i) in keyframes" :key="i" class="keyframe-row">
                <el-input v-model="keyframes[i]" placeholder="https://… 第 {{ i + 1 }} 帧图片 URL" />
                <el-button v-if="keyframes.length > 1" type="danger" :icon="Delete" circle
                  @click="keyframes.splice(i, 1)" />
              </div>
              <el-button type="primary" plain :icon="Plus" @click="keyframes.push('')">添加关键帧</el-button>
              <p class="tip">至少 2 帧，按顺序传入（extra_body.image 数组），角色/风格一致的图过渡最自然</p>
            </div>
          </el-form-item>
        </template>

        <!-- 通用参数 -->
        <el-form-item label="画面比例">
          <el-radio-group v-model="aspectRatio" @change="onRatioChange">
            <el-radio value="16:9">16:9 横版</el-radio>
            <el-radio value="9:16">9:16 竖版</el-radio>
            <el-radio value="1:1">1:1 方形</el-radio>
            <el-radio value="4:3">4:3</el-radio>
            <el-radio value="3:4">3:4</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="目标时长">
          <el-select v-model="durationPreset" style="width: 200px" @change="onDurationChange">
            <el-option label="约 3 秒 (81帧@24fps)" value="3" />
            <el-option label="约 5 秒 (121帧@24fps)" value="5" />
            <el-option label="约 10 秒 (241帧@24fps)" value="10" />
            <el-option label="约 18 秒 (441帧@24fps)" value="18" />
          </el-select>
        </el-form-item>

        <el-form-item label="帧率">
          <el-input-number v-model="frameRate" :min="1" :max="60" />
          <span class="tip"> 24 或 30 运动最流畅</span>
        </el-form-item>

        <el-form-item label="分辨率档位">
          <el-select v-model="resolution" style="width: 200px" @change="onResolutionChange">
            <el-option label="480p（最快）" value="480p" />
            <el-option label="720p（推荐）" value="720p" />
            <el-option label="1080p（最清晰，较慢）" value="1080p" />
          </el-select>
        </el-form-item>

        <el-form-item label="负向提示词">
          <el-input v-model="negativePrompt" type="textarea" :rows="2"
            placeholder="需要避免的内容，如：blurry, low quality, extra fingers, distorted face" />
        </el-form-item>

        <el-form-item label="随机种子">
          <el-input-number v-model="seed" :min="0" :step="1" placeholder="留空则随机" />
          <el-button v-if="seed !== undefined && seed !== null && seed !== ''" link type="primary"
            @click="seed = undefined">清空</el-button>
          <span class="tip"> 固定 seed 可复现相同结果</span>
        </el-form-item>
      </el-form>

      <div class="action-row">
        <el-button type="primary" size="large" :loading="creating" :disabled="!canCreate" @click="createTask">
          {{ creating ? '创建任务中…' : '🚀 创建生成任务' }}
        </el-button>
        <el-button size="large" @click="resetForm">重置</el-button>
      </div>
    </el-card>

    <!-- 任务与结果 -->
    <el-card class="section-card" shadow="never">
      <template #header>
        <span>任务与结果</span>
      </template>

      <!-- 任务状态 -->
      <div v-if="task" class="task-info">
        <div class="task-status">
          <el-tag :type="statusTagType" size="large">{{ task.status }}</el-tag>
          <span class="task-id">video_id: {{ task.video_id || task.id }}</span>
        </div>

        <div v-if="task.status === 'queued' || task.status === 'in_progress'" class="progress-bar">
          <span class="tip">视频生成中，完成后自动展示，请保持页面打开…</span>
          <el-button type="primary" plain :loading="polling" @click="refreshCurrent">刷新状态</el-button>
        </div>

        <!-- 完成 → 展示视频 -->
        <div v-if="task.status === 'completed' && videoUrl" class="video-result">
          <video :src="videoUrl" controls autoplay muted loop class="result-video" />
          <div class="video-meta">
            <span>分辨率: {{ task.size }}</span>
            <span>时长: {{ task.seconds }}s</span>
            <span v-if="sizeMapping?.adjusted" class="tip">
              （请求尺寸已映射为 {{ sizeMapping.resolution }} / {{ sizeMapping.ratio }}）
            </span>
          </div>
          <div class="action-row">
            <el-button type="primary" @click="downloadVideo">下载视频</el-button>
            <el-button @click="copyVideoUrl">复制视频 URL</el-button>
          </div>
        </div>

        <!-- 失败 -->
        <el-alert v-if="task.status === 'failed'" type="error" :title="task.error?.message || '生成失败，请查看错误信息'" show-icon
          :closable="false">
          <template v-if="task.error?.code">
            <p class="error-code">错误码：{{ task.error.code }}</p>
          </template>
        </el-alert>
      </div>

      <el-empty v-else description="还没有任务，填写上方参数开始生成" />
    </el-card>

    <!-- 历史任务 -->
    <el-card v-if="history.length" class="section-card" shadow="never">
      <template #header>
        <span>历史任务</span>
      </template>
      <el-table :data="history" size="small">
        <el-table-column prop="created_at" label="创建时间" width="170">
          <template #default="{ row }">{{ formatTime(row.created_at) }}</template>
        </el-table-column>
        <el-table-column prop="mode" label="模式" width="90" />
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
            <el-button v-if="row.status === 'completed' && row.videoUrl" link type="primary" @click="playHistory(row)">
              播放
            </el-button>
            <el-button v-else-if="['queued', 'in_progress'].includes(row.status)" link type="primary"
              @click="refreshHistory(row)">
              刷新
            </el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue';
import { ElMessage } from 'element-plus';
import { Plus, Delete } from '@element-plus/icons-vue';
// ==================== Agnes API 直连 ====================
const API_BASE = 'https://api.agnes-ai.cn';
const API_KEY_STORAGE = 'agnes_api_key';

function getApiKey() {
  return localStorage.getItem(API_KEY_STORAGE) || 'sk-2gaqtdDxCFtc5Azoc9AWXgqpXuKSM6qAfKmJHh8OHItygW16';
}

// ==================== 常量 ====================
const MODEL = 'agnes-video-v2.0';
// 分辨率档位 → (width, height)，与文档三档标准对应
const RESOLUTION_MAP = {
  '480p': { '16:9': [832, 448], '9:16': [448, 832], '1:1': [576, 576], '4:3': [640, 480], '3:4': [480, 640] },
  '720p': { '16:9': [1280, 720], '9:16': [720, 1280], '1:1': [768, 768], '4:3': [960, 720], '3:4': [720, 960] },
  '1080p': { '16:9': [1920, 1080], '9:16': [1080, 1920], '1:1': [1152, 1152], '4:3': [1440, 1080], '3:4': [1080, 1440] }
};
// 时长预设 → (num_frames, frame_rate)
const DURATION_MAP = {
  '3': [81, 24],
  '5': [121, 24],
  '10': [241, 24],
  '18': [441, 24]
};

// ==================== 状态 ====================
const mode = ref('t2v');
const prompt = ref('');
const imageUrl = ref('');
const keyframes = ref(['', '']);
const aspectRatio = ref('9:16');
const resolution = ref('720p');
const durationPreset = ref('5');
const frameRate = ref(24);
const negativePrompt = ref('');
const seed = ref(undefined);

const width = ref(720);
const height = ref(1280);
const numFrames = ref(121);

const creating = ref(false);
const polling = ref(false);
const savingKey = ref(false);
const apiKeyInput = ref('');
const task = ref(null);
const videoUrl = ref('');
const history = ref([]);
const hasApiKey = ref(false);
const backendError = ref('');

// ==================== 计算属性 ====================
const canCreate = computed(() => {
  if (!prompt.value.trim()) return false;
  if (mode.value === 'i2v' && !imageUrl.value.trim()) return false;
  if (mode.value === 'keyframes') {
    const valid = keyframes.value.filter((u) => u.trim());
    if (valid.length < 2) return false;
  }
  return true;
});

const statusTagType = computed(() => {
  const map = { completed: 'success', failed: 'danger', in_progress: 'warning', queued: 'info' };
  return map[task.value?.status] || 'info';
});

// 尺寸映射信息：兼容 metadata.size_mapping 或顶层 size_mapping
const sizeMapping = computed(() => task.value?.metadata?.size_mapping || task.value?.size_mapping || null);

// ==================== 方法 ====================
// 保存 API Key 到 localStorage
function saveApiKey() {
  const key = apiKeyInput.value.trim();
  if (!key) {
    ElMessage.warning('请输入 API Key');
    return;
  }
  localStorage.setItem(API_KEY_STORAGE, key);
  hasApiKey.value = true;
  apiKeyInput.value = '';
  backendError.value = '';
  ElMessage.success('API Key 已保存到浏览器');
}

function clearApiKey() {
  localStorage.removeItem(API_KEY_STORAGE);
  hasApiKey.value = false;
  apiKeyInput.value = '';
  ElMessage.success('API Key 已清除');
}

// 调用 Agnes API（直接 fetch，不经过后端，带 30s 超时）
async function agnesFetch(path, options = {}) {
  const key = getApiKey();
  if (!key) {
    throw new Error('请先在上方配置 Agnes API Key');
  }
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), 30000);
  try {
    const res = await fetch(`${API_BASE}${path}`, {
      ...options,
      signal: controller.signal,
      headers: {
        'Authorization': `Bearer ${key}`,
        'Content-Type': 'application/json',
        ...(options.headers || {})
      }
    });

    // 尝试解析 JSON（失败时返回文本）
    let body = null;
    const text = await res.text();
    try {
      body = text ? JSON.parse(text) : null;
    } catch {
      body = text;
    }

    if (!res.ok) {
      const msg =
        (body && typeof body === 'object' && (body.message || body.error?.message)) ||
        `HTTP ${res.status}`;
      throw new Error(msg);
    }
    return body;
  } catch (error) {
    if (error.name === 'AbortError') {
      throw new Error('请求超时（30s），请检查网络/代理');
    }
    throw error;
  } finally {
    clearTimeout(timer);
  }
}

function onModeChange() {
  if (mode.value === 'keyframes' && keyframes.value.length < 2) {
    keyframes.value = ['', ''];
  }
}

function onRatioChange() {
  applyResolution();
}

function onResolutionChange() {
  applyResolution();
}

function onDurationChange() {
  numFrames.value = DURATION_MAP[durationPreset.value][0];
  frameRate.value = DURATION_MAP[durationPreset.value][1];
}

// 根据分辨率档位 + 宽高比计算 width/height
function applyResolution() {
  const [w, h] = RESOLUTION_MAP[resolution.value][aspectRatio.value];
  width.value = w;
  height.value = h;
}

function resetForm() {
  stopPolling();
  mode.value = 't2v';
  prompt.value = '';
  imageUrl.value = '';
  keyframes.value = ['', ''];
  aspectRatio.value = '9:16';
  resolution.value = '720p';
  durationPreset.value = '5';
  frameRate.value = 24;
  negativePrompt.value = '';
  seed.value = undefined;
  task.value = null;
  videoUrl.value = '';
  onDurationChange();
  applyResolution();
}

function buildPayload() {
  const payload = {
    model: MODEL,
    prompt: prompt.value.trim(),
    width: width.value,
    height: height.value,
    num_frames: numFrames.value,
    frame_rate: frameRate.value
  };
  if (negativePrompt.value.trim()) payload.negative_prompt = negativePrompt.value.trim();
  if (seed.value !== undefined && seed.value !== null && seed.value !== '') payload.seed = seed.value;

  if (mode.value === 'i2v') {
    payload.image = imageUrl.value.trim();
  } else if (mode.value === 'keyframes') {
    const frames = keyframes.value.map((u) => u.trim()).filter(Boolean);
    payload.mode = 'keyframes';
    payload.extra_body = { mode: 'keyframes', image: frames };
  }
  return payload;
}

// ==================== 轮询控制（单链，防重入） ====================
let pollTimer = null;
let pollChainVideoId = null; // 当前轮询链对应的 video_id，新链会取代旧链
let pollStartTime = 0; // 当前轮询链开始时间，用于超时保护
const POLL_MAX_MS = 20 * 60 * 1000; // 最多等 20 分钟（生成通常几分钟内完成）

// 停止当前轮询链
function stopPolling() {
  if (pollTimer) {
    clearTimeout(pollTimer);
    pollTimer = null;
  }
  pollChainVideoId = null;
  polling.value = false;
}

// 启动一条新的轮询链（自动先停旧的）
function startPolling(target) {
  if (!target?.video_id) return;
  stopPolling();
  pollChainVideoId = target.video_id;
  pollStartTime = Date.now();
  pollOnce(target);
}

// 轮询一次，未完成则 5 秒后继续（同一链内）
async function pollOnce(target) {
  // 已被新链取代或已停止，放弃本次请求结果
  if (pollChainVideoId !== target.video_id) return;

  polling.value = true;
  try {
    const result = await agnesFetch(`/agnesapi?video_id=${encodeURIComponent(target.video_id)}`);
    if (pollChainVideoId !== target.video_id) return; // 已被取代/停止

    // 更新当前任务（保留原始 video_id，兼容轮询响应只有 id 的情况）
    const curId = task.value && (task.value.video_id || task.value.id);
    if (curId && curId === target.video_id) {
      task.value = {
        ...result,
        video_id: task.value.video_id || result.video_id || result.id,
        mode: task.value.mode,
        prompt: task.value.prompt
      };
      videoUrl.value = result.metadata?.url || result.url || ''; // 兼容 url 在顶层或 metadata 内
    }

    // 记录历史（用轮询时的 video_id，避免被响应覆盖）
    const existingIdx = history.value.findIndex((h) => h.video_id === target.video_id);
    const record = {
      ...result,
      video_id: target.video_id,
      videoUrl: result.metadata?.url || result.url || '',
      created_at: result.created_at || Date.now() / 1000,
      mode: target.mode || mode.value,
      prompt: result.prompt || target.prompt || prompt.value
    };
    if (existingIdx >= 0) {
      history.value[existingIdx] = record;
    } else {
      history.value.unshift(record);
    }

    if (result.status === 'completed') {
      ElMessage.success('视频生成完成！');
      stopPolling();
    } else if (result.status === 'failed') {
      ElMessage.error(result.error?.message || '生成失败');
      stopPolling();
    } else if (pollChainVideoId === target.video_id) {
      // 未完成且未被取代，5 秒后继续轮询（超时保护：排队过久视为异常，停止自动轮询）
      if (Date.now() - pollStartTime > POLL_MAX_MS) {
        ElMessage.warning('任务排队/生成时间过长（超过 20 分钟），已停止自动轮询，可稍后手动刷新');
        stopPolling();
      } else {
        pollTimer = setTimeout(() => pollOnce(target), 5000);
      }
    }
  } catch (error) {
    console.error(error);
    ElMessage.error('查询失败：' + (error?.message || '请检查 API Key 与网络'));
    stopPolling(); // 出错停止轮询，可手动点刷新
  } finally {
    polling.value = false;
  }
}

// 手动刷新当前任务
function refreshCurrent() {
  if (!task.value?.video_id) return;
  startPolling(task.value);
}

// 历史记录里刷新某条任务
function refreshHistory(row) {
  if (!row?.video_id) return;
  startPolling(row);
}

// 创建视频任务（直连 Agnes API，防重复点击）
async function createTask() {
  if (creating.value) return; // 防止重复提交
  creating.value = true;
  try {
    const data = await agnesFetch('/v1/videos', {
      method: 'POST',
      body: JSON.stringify(buildPayload())
    });
    task.value = {
      ...data,
      mode: mode.value,
      prompt: prompt.value.trim()
    };
    videoUrl.value = '';
    ElMessage.success('任务已创建，正在排队生成…');
    // 创建后自动开始轮询
    startPolling(task.value);
  } catch (error) {
    console.error(error);
    ElMessage.error('创建失败：' + (error?.message || '请检查 API Key 与网络'));
  } finally {
    creating.value = false;
  }
}

function playHistory(row) {
  task.value = row;
  videoUrl.value = row.videoUrl;
}

function downloadVideo() {
  if (!videoUrl.value) return;
  const a = document.createElement('a');
  a.href = videoUrl.value;
  a.download = `${task.value.video_id}.mp4`;
  a.target = '_blank';
  a.click();
}

async function copyVideoUrl() {
  try {
    await navigator.clipboard.writeText(videoUrl.value);
    ElMessage.success('视频 URL 已复制');
  } catch {
    ElMessage.warning('复制失败，请手动复制');
  }
}

function formatTime(ts) {
  if (!ts) return '-';
  const d = new Date(ts * 1000);
  const pad = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

onMounted(() => {
  applyResolution();
  onDurationChange();
  hasApiKey.value = !!getApiKey(); // 读取本地保存的 Key
});

onUnmounted(() => {
  stopPolling(); // 离开页面时清理轮询定时器
});
</script>

<style scoped lang="scss">
.agnes-video {
  max-width: 900px;
  margin: 0 auto;
  padding: 20px;

  .page-title {
    margin-bottom: 20px;
    font-size: 22px;
    font-weight: 600;
  }

  .section-card {
    margin-bottom: 20px;
    border-radius: 8px;
  }

  .config-row {
    display: flex;
    gap: 12px;
    align-items: center;
    flex-wrap: wrap;

    .key-input {
      max-width: 420px;
    }
  }

  .error-tip {
    color: #f56c6c;
  }

  .error-code {
    font-family: monospace;
    font-size: 12px;
    margin-top: 6px;
    opacity: 0.8;
  }

  .tip {
    font-size: 12px;
    color: #909399;
    margin-left: 6px;
  }

  .preview-img {
    width: 160px;
    height: 160px;
    border-radius: 6px;
    border: 1px solid #dcdfe6;
  }

  .keyframes-list {
    width: 100%;

    .keyframe-row {
      display: flex;
      gap: 8px;
      margin-bottom: 8px;
      align-items: center;
    }
  }

  .action-row {
    margin-top: 8px;
    display: flex;
    gap: 12px;
    flex-wrap: wrap;
  }

  .task-info {
    .task-status {
      display: flex;
      align-items: center;
      gap: 12px;
      margin-bottom: 12px;

      .task-id {
        font-family: monospace;
        color: #606266;
        font-size: 13px;
      }

      .task-progress {
        color: #909399;
        font-size: 13px;
      }
    }

    .progress-bar {
      display: flex;
      align-items: center;
      gap: 16px;
      padding: 4px 0;

      .el-progress {
        flex: 1;
      }
    }

    .video-result {
      .result-video {
        width: 100%;
        max-width: 480px;
        border-radius: 8px;
        background: #000;
      }

      .video-meta {
        margin: 10px 0;
        font-size: 13px;
        color: #606266;
        display: flex;
        gap: 16px;
        flex-wrap: wrap;
      }
    }
  }
}
</style>
