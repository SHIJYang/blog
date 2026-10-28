<template>
  <div class="memory-management" v-loading="pageLoading">
    <!-- 头部分区 -->
    <div class="section-card">
      <div class="section-header">
        <h3>记忆总览</h3>
        <div class="header-actions">
          <el-upload :show-file-list="false" :auto-upload="false" :before-upload="beforeUpload"
            accept="image/*">
            <el-button type="primary" :icon="Upload" :loading="uploading">上传照片创建记忆</el-button>
          </el-upload>
        </div>
      </div>
      <div v-if="stat" class="stat-grid">
        <div class="stat-item">
          <div class="stat-value">{{ stat.continueDays ?? 0 }}</div>
          <div class="stat-label">连续记录天数</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ stat.totalMemory ?? 0 }}</div>
          <div class="stat-label">累计记忆（已成念）</div>
        </div>
        <div class="stat-item">
          <div class="stat-value">{{ stat.chattingCount ?? 0 }}</div>
          <div class="stat-label">进行中记忆</div>
        </div>
        <div class="stat-item">
          <div class="stat-value stat-date">{{ formatDateTime(stat.lastRecordTime) }}</div>
          <div class="stat-label">最后记录时间</div>
        </div>
      </div>
      <el-empty v-else description="暂无统计数据" :image-size="60" />

      <!-- 情绪锚点分布 -->
      <div v-if="emotionStats.length" class="emotion-block">
        <div class="emotion-header">
          <span class="emotion-title">情绪锚点分布</span>
          <el-button size="small" :loading="mergingEmotion" link type="primary" @click="handleMergeEmotions">归并情绪标签</el-button>
        </div>
        <div class="emotion-tags">
          <el-tag v-for="item in emotionStats" :key="item.emotion" size="large" round class="emotion-tag">
            {{ item.emotion }}
            <span class="emotion-count">×{{ item.count }}</span>
          </el-tag>
        </div>
      </div>
      <el-empty v-else description="还没有情绪标签，先上传照片和拾光聊聊天吧" :image-size="60" />
    </div>

    <!-- 列表筛选 -->
    <div class="section-card">
      <div class="filter-bar">
        <el-select v-if="isAdmin" v-model="queryParams.userId" placeholder="用户筛选" clearable filterable style="width: 180px"
          @change="handleSearch">
          <el-option v-for="u in userOptions" :key="u.id" :label="(u.nickname || u.username) + ' (#' + u.id + ')'" :value="u.id" />
        </el-select>
        <el-input v-model="queryParams.keyword" placeholder="搜索标题 / 对话 / 日记" clearable style="width: 220px"
          @clear="handleSearch" @keyup.enter="handleSearch" />
        <el-select v-model="queryParams.status" placeholder="状态筛选" clearable style="width: 150px"
          @change="handleSearch">
          <el-option label="未开始" :value="1" />
          <el-option label="对话中" :value="2" />
          <el-option label="已成念" :value="3" />
          <el-option label="已废弃" :value="4" />
        </el-select>
        <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        <div class="pin-bar">
          <el-tag v-if="!pinStatus.hasPin" type="info" size="small">未设置封存 PIN</el-tag>
          <template v-else>
            <el-tag :type="pinStatus.unlocked ? 'success' : 'warning'" size="small">
              {{ pinStatus.unlocked ? '封存已解锁' : '封存已锁定' }}
            </el-tag>
            <el-button v-if="!pinStatus.unlocked" size="small" link type="primary" @click="openPinVerify">输入 PIN</el-button>
          </template>
          <el-button size="small" link @click="openPinSet">{{ pinStatus.hasPin ? '重置 PIN' : '设置 PIN' }}</el-button>
        </div>
      </div>
    </div>

    <!-- 数据表格 -->
    <div class="section-card">
      <el-table :data="tableData" v-loading="loading" border style="width: 100%">
        <el-table-column prop="id" label="ID" width="70" align="center" />
        <el-table-column v-if="isAdmin" width="120" align="center">
          <template #header><span>用户</span></template>
          <template #default="scope">
            <span>{{ scope.row.username || ('#' + scope.row.userId) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="封面" width="90" align="center">
          <template #default="scope">
            <el-image v-if="scope.row.coverThumb || scope.row.coverPhoto" :src="photoUrl(scope.row.coverThumb || scope.row.coverPhoto)" style="width: 60px; height: 60px; border-radius: 6px;"
              fit="cover" />
            <span v-else class="text-secondary">无</span>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="160" show-overflow-tooltip>
          <template #default="scope">
            <el-tag v-if="scope.row.kind === 2" size="small" type="info" style="margin-right:4px;">碎念</el-tag>
            {{ scope.row.autoTitle || '(未命名)' }}
          </template>
        </el-table-column>
        <el-table-column label="日记摘要" min-width="220" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.diaryPreview || '-' }}</template>
        </el-table-column>
        <el-table-column label="消息数" width="80" align="center">
          <template #default="scope">{{ scope.row.messageCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="情绪" width="140" align="center">
          <template #default="scope">
            <template v-if="scope.row.emotions">
              <el-tag v-for="e in scope.row.emotions.split(',').slice(0,3)" :key="e" size="small" type="warning" style="margin:1px;">
                {{ e }}
              </el-tag>
            </template>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
        <el-table-column label="隐私" width="90" align="center">
          <template #default="scope">
            <el-tag :type="getPrivacyType(scope.row.privacy)">{{ getPrivacyLabel(scope.row.privacy) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="状态" width="100" align="center">
          <template #default="scope">
            <el-tag :type="getStatusType(scope.row.status)">{{ getStatusLabel(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" width="160" align="center" />
        <!-- 发布状态 -->
        <el-table-column label="文章" width="110" align="center">
          <template #default="scope">
            <el-link v-if="scope.row.publishedArticleId" type="success" underline="never"
              @click="openArticle(scope.row.publishedArticleId)">
              已发布
            </el-link>
            <span v-else class="text-secondary">未发布</span>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="360" align="center" fixed="right">
          <template #default="scope">
            <el-button size="small" link type="primary" @click="openDetail(scope.row)">详情</el-button>
            <el-button size="small" link @click="openEditDialog(scope.row)">编辑</el-button>
            <el-button v-if="scope.row.status === 3 && !scope.row.publishedArticleId" size="small" link type="success"
              @click="handlePublish(scope.row)">转文章</el-button>
            <el-button size="small" link @click="openCondense(scope.row)">浓缩</el-button>
            <el-button size="small" link type="warning" @click="openChat(scope.row)">对话</el-button>
            <el-button size="small" link type="info" @click="openPrivacy(scope.row)">隐私</el-button>
            <el-button v-if="scope.row.status !== 4" size="small" link type="danger" @click="handleAbandon(scope.row)">废弃</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-container">
        <el-pagination v-model:current-page="queryParams.current" v-model:page-size="queryParams.size"
          :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" :total="total"
          @size-change="handleSizeChange" @current-change="handleCurrentChange" />
      </div>
    </div>

    <!-- 详情弹窗 -->
    <el-dialog v-model="detailVisible" title="记忆详情" width="680px">
      <div v-loading="detailLoading" class="detail-body">
        <template v-if="currentDetail">
          <div class="detail-note">
            <p><b>ID：</b>{{ currentDetail.note?.id }}</p>
            <p><b>标题：</b>{{ currentDetail.note?.autoTitle || '-' }}</p>
            <p><b>状态：</b>{{ getStatusLabel(currentDetail.note?.status) }}</p>
            <p><b>隐私：</b>{{ getPrivacyLabel(currentDetail.note?.privacy) }}</p>
            <p v-if="currentDetail.note?.emotions"><b>情绪：</b>{{ currentDetail.note.emotions }}</p>
            <p><b>创建时间：</b>{{ formatDateTime(currentDetail.note?.createTime) }}</p>
            <p><b>封面：</b>
              <el-image v-if="currentDetail.note?.coverPhoto" :src="photoUrl(currentDetail.note.coverPhoto)" style="width:120px; border-radius:6px;"
                fit="cover" />
              <span v-else>-</span>
            </p>
          </div>

          <div v-if="currentDetail.note?.finalDiary" class="detail-section">
            <h4>日记正文</h4>
            <pre class="diary-pre">{{ currentDetail.note.finalDiary }}</pre>
          </div>

          <div v-if="currentDetail.messages?.length" class="detail-section">
            <h4>对话记录（{{ currentDetail.messages.length }}）</h4>
            <div v-for="m in currentDetail.messages" :key="m.id" class="msg-item" :class="'type-' + (m.msgType || 1)">
              <span v-if="m.imgUrl" class="msg-img"><el-image :src="photoUrl(m.imgUrl)" style="max-width:180px; border-radius:6px;" fit="cover" /></span>
              <span v-if="m.content" class="msg-text">{{ m.content }}</span>
              <span class="msg-time">{{ formatDateTime(m.createTime) }}</span>
            </div>
          </div>

          <!-- AI 开场白 -->
          <div v-if="openingText" class="detail-section">
            <h4>AI 开场白</h4>
            <p class="opening-text">{{ openingText }}</p>
          </div>

          <div v-if="!currentDetail.note && !currentDetail.messages?.length" class="text-secondary">（无更多数据）</div>
        </template>
      </div>
    </el-dialog>

    <!-- 浓缩弹窗 -->
    <el-dialog v-model="condenseVisible" title="凝聚记忆 / 自定义标题" width="480px">
      <p class="tip">凝聚后将 AI 对话生成为日记，可自定义标题（留空由 AI 命名）。</p>
      <el-form label-width="100px" style="margin-top: 12px;">
        <el-form-item label="记忆标题">
          <el-input v-model="condenseForm.customTitle" placeholder="输入自定义标题（可选）" clearable />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="condenseVisible = false">取消</el-button>
          <el-button type="primary" :loading="condenseLoading" @click="handleCondense">凝聚</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 隐私设置弹窗 -->
    <el-dialog v-model="privacyVisible" title="设置记忆隐私" width="480px">
      <el-form label-width="100px">
        <el-form-item label="隐私等级">
          <el-radio-group v-model="privacyForm.privacy">
            <el-radio :value="0">公开</el-radio>
            <el-radio :value="1">私密</el-radio>
            <el-radio :value="2">封存</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="说明">
          <div class="privacy-tip">
            <p><b>公开</b>：<span class="danger-text">立即投影为博客公开文章</span>，所有人可见；改回私密/封存会立即下架。</p>
            <p><b>私密</b>：仅本人可见，AI 正常温柔关联（默认）。</p>
            <p><b>封存</b>：<span class="danger-text">本人也需 PIN 解锁</span>才能查看，AI 永不召回，不参与统计。</p>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="privacyVisible = false">取消</el-button>
          <el-button type="primary" :loading="privacyLoading" @click="handleSetPrivacy">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 编辑记忆弹窗 -->
    <el-dialog v-model="editVisible" title="编辑记忆" width="560px">
      <el-form label-width="80px">
        <el-form-item label="标题">
          <el-input v-model="editForm.autoTitle" placeholder="记忆标题" maxlength="100" />
        </el-form-item>
        <el-form-item label="情绪标签">
          <el-select v-model="editForm.emotions" multiple filterable allow-create default-first-option
            placeholder="选择或输入情绪标签" style="width: 100%">
            <el-option v-for="e in emotionOptions" :key="e" :label="e" :value="e" />
          </el-select>
        </el-form-item>
        <el-form-item label="正文">
          <el-input v-model="editForm.finalDiary" type="textarea" :rows="6" placeholder="这段记忆的正文（日记）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editVisible = false">取消</el-button>
          <el-button type="primary" :loading="editSaving" @click="handleEditSave">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 对话弹窗 -->
    <el-dialog v-model="chatVisible" title="与记忆对话" width="560px">
      <div class="chat-box" v-loading="chatLoading">
        <div v-if="chatHistory.length" class="chat-history">
          <div v-for="(item, i) in chatHistory" :key="i" class="chat-msg" :class="item.role">
            <div class="chat-role">{{ item.role === 'me' ? '我' : 'AI' }}</div>
            <div class="chat-content">{{ item.content }}</div>
          </div>
        </div>
        <el-empty v-else description="开始和念念对话吧" :image-size="60" />

        <!-- 验证码 -->
        <div v-if="chatVisible" class="chat-captcha">
          <img v-if="captchaImage" :src="captchaImage" class="captcha-img" alt="验证码" />
          <el-input v-model="chatForm.captchaCode" placeholder="验证码" style="width: 110px" />
          <el-button :icon="Refresh" circle @click="loadCaptcha" />
        </div>

        <div class="chat-input-bar">
          <el-input v-model="chatForm.content" placeholder="输入消息，Enter 发送" @keyup.enter="handleChatSend" />
          <el-button type="primary" :loading="chatSending" @click="handleChatSend">发送</el-button>
        </div>
      </div>
    </el-dialog>
    <!-- 设置封存 PIN -->
    <el-dialog v-model="pinSetVisible" title="设置封存 PIN" width="400px">
      <p class="tip">4~6 位数字。封存记忆的列表、详情、对话都需要先输入 PIN 解锁。</p>
      <el-form label-width="90px" style="margin-top:12px;">
        <el-form-item label="新 PIN">
          <el-input v-model="pinForm.pin" type="password" maxlength="6" show-password placeholder="4~6 位数字" />
        </el-form-item>
        <el-form-item label="确认 PIN">
          <el-input v-model="pinForm.confirm" type="password" maxlength="6" show-password placeholder="再输入一次" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="pinSetVisible = false">取消</el-button>
          <el-button type="primary" :loading="pinSaving" @click="handlePinSet">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 解锁封存 -->
    <el-dialog v-model="pinVerifyVisible" title="解锁封存记忆" width="380px">
      <p class="tip">解锁令牌 5 分钟无操作失效，连续错误 5 次锁定 30 分钟。</p>
      <el-form label-width="70px" style="margin-top:12px;">
        <el-form-item label="PIN">
          <el-input v-model="verifyForm.pin" type="password" maxlength="6" show-password placeholder="输入 PIN" @keyup.enter="handlePinVerify" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="pinVerifyVisible = false">取消</el-button>
          <el-button type="primary" :loading="pinVerifying" @click="handlePinVerify">解锁</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Upload, Refresh } from '@element-plus/icons-vue'
import {
  getMemoryStat,
  getMemoryList,
  searchMemory,
  getMemoryDetail,
  getMemoryCaptcha,
  getMemoryOpening,
  getMemoryPublishStatus,
  createMemory,
  publishMemory,
  abandonMemory,
  condenseMemory,
  chatSendMemory,
  setMemoryPrivacy,
  getMemoryEmotionStats,
  mergeMemoryEmotions,
  updateMemory,
  getMemoryPinStatus,
  setMemoryPin,
  verifyMemoryPin
} from '@/api/memory'
import { getUserPage } from '@/api/user'
import { photoUrl } from '@/utils/photo'

const router = useRouter()

// ============ 状态 ============
const pageLoading = ref(false)
const loading = ref(false)
const uploading = ref(false)
const detailLoading = ref(false)
const condenseLoading = ref(false)
const chatLoading = ref(false)
const chatSending = ref(false)

const stat = ref(null)
const tableData = ref([])
const total = ref(0)
const emotionStats = ref([])
const mergingEmotion = ref(false)
const isAdmin = localStorage.getItem('role') === 'ADMIN'
const userOptions = ref([])

const detailVisible = ref(false)
const condenseVisible = ref(false)
const chatVisible = ref(false)
const privacyVisible = ref(false)
const privacyLoading = ref(false)
const privacyForm = reactive({ memoryId: null, privacy: 1 })

// 封存 PIN
const pinStatus = ref({ hasPin: false, locked: false, unlocked: false })
const pinSetVisible = ref(false)
const pinVerifyVisible = ref(false)
const pinSaving = ref(false)
const pinVerifying = ref(false)
const pinForm = reactive({ pin: '', confirm: '' })
const verifyForm = reactive({ pin: '' })

// 编辑
const editVisible = ref(false)
const editSaving = ref(false)
const editForm = reactive({ memoryId: null, autoTitle: '', emotions: [], finalDiary: '' })
const emotionOptions = ref([])

const currentDetail = ref(null)
const openingText = ref('')
const condenseForm = reactive({ memoryId: null, customTitle: '' })
const chatForm = reactive({ memoryId: null, content: '', captchaCode: '', captchaKey: '' })
const chatHistory = ref([])
const captchaImage = ref('')

const queryParams = reactive({
  current: 1,
  size: 10,
  keyword: '',
  status: null,
  order: 'create_desc',
  userId: null
})

// ============ 列表（后端返回 MyBatis-Plus Page: { records, total, size, current, pages }） ============
async function getList() {
  loading.value = true
  try {
    let res
    if (queryParams.keyword) {
      res = await searchMemory({
        keyword: queryParams.keyword,
        current: queryParams.current,
        size: queryParams.size
      }, queryParams.userId || undefined)
    } else {
      res = await getMemoryList({
        current: queryParams.current,
        size: queryParams.size,
        status: queryParams.status,
        order: queryParams.order
      }, queryParams.userId || undefined)
    }
    tableData.value = res?.records || []
    total.value = res?.total || 0

    // MemoryListVo 不含 publishedArticleId，仅对“已成念”状态逐行查询发布状态
    const rows = tableData.value
    if (rows.length) {
      await Promise.all(rows.map(async (row) => {
        if (row.status !== 3) {
          row.publishedArticleId = null
          return
        }
        try {
          const aid = await getMemoryPublishStatus(row.id)
          row.publishedArticleId = aid || null
        } catch (e) {
          row.publishedArticleId = null
        }
      }))
    }
  } catch (e) {
    ElMessage.error(e?.message || '获取记忆列表失败')
  } finally {
    loading.value = false
  }
}

// ============ 统计 ============
async function loadStat() {
  try {
    const res = await getMemoryStat(queryParams.userId || undefined)
    stat.value = res || null
  } catch (e) {
    stat.value = null
  }
}

// ============ 操作栏 ============
const handleSearch = () => {
  queryParams.current = 1
  getList()
}

const handleSizeChange = (val) => {
  queryParams.size = val
  getList()
}

const handleCurrentChange = (val) => {
  queryParams.current = val
  getList()
}

// ============ 上传创建 ============
function beforeUpload(file) {
  uploading.value = true
  ;(async () => {
    try {
      await createMemory(file)
      ElMessage.success('记忆创建成功')
      loadStat()
      getList()
    } catch (e) {
      ElMessage.error(e?.message || '上传失败')
    } finally {
      uploading.value = false
    }
  })()
  return false // 阻止默认上传，由我们手动处理
}

// ============ 详情 + AI 开场白 ============
async function openDetail(row) {
  detailVisible.value = true
  detailLoading.value = true
  currentDetail.value = null
  openingText.value = ''
  try {
    const res = await getMemoryDetail(row.id)
    currentDetail.value = res || null
    // AI 开场白
    const opening = await getMemoryOpening(row.id).catch(() => null)
    openingText.value = opening?.opening || ''
  } catch (e) {
    ElMessage.error(e?.message || '加载详情失败')
  } finally {
    detailLoading.value = false
  }
}

// 打开发布后的文章
function openArticle(articleId) {
  router.push({ name: 'ArticleBrowse', params: { id: articleId } })
}

// ============ 转文章（日记 → 博客文章，幂等） ============
async function handlePublish(row) {
  ElMessageBox.confirm(`将把记忆 #${row.id}「${row.autoTitle || ''}」转为博客文章，是否继续？`, '发布为文章', {
    confirmButtonText: '发布',
    cancelButtonText: '取消',
    type: 'info'
  }).then(async () => {
    try {
      const articleId = await publishMemory(row.id)
      ElMessage.success('已发布为文章')
      getList()
    } catch (e) {
      ElMessage.error(e?.message || '发布失败')
    }
  }).catch(() => { })
}

// ============ 废弃 ============
function handleAbandon(row) {
  ElMessageBox.confirm(`确定废弃记忆 #${row.id} 吗？废弃后不可恢复。`, '废弃确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await abandonMemory(row.id)
      ElMessage.success('已废弃')
      loadStat()
      getList()
    } catch (e) {
      ElMessage.error(e?.message || '操作失败')
    }
  }).catch(() => { })
}

// ============ 浓缩 ============
function openCondense(row) {
  condenseForm.memoryId = row.id
  condenseForm.customTitle = row.autoTitle || ''
  condenseVisible.value = true
}

async function handleCondense() {
  condenseLoading.value = true
  try {
    await condenseMemory(condenseForm.memoryId, condenseForm.customTitle || undefined)
    ElMessage.success('凝聚完成')
    condenseVisible.value = false
    getList()
  } catch (e) {
    ElMessage.error(e?.message || '凝聚失败')
  } finally {
    condenseLoading.value = false
  }
}

// ============ 对话（含验证码） ============
async function loadCaptcha() {
  chatLoading.value = true
  try {
    // 后端返回 { key, image(dataURL) }
    const res = await getMemoryCaptcha()
    chatForm.captchaKey = res?.key || ''
    captchaImage.value = res?.image || ''
  } catch (e) {
    captchaImage.value = ''
  } finally {
    chatLoading.value = false
  }
}

function openChat(row) {
  chatVisible.value = true
  chatHistory.value = []
  chatForm.memoryId = row.id
  chatForm.content = ''
  chatForm.captchaCode = ''
  chatForm.captchaKey = ''
  loadCaptcha()
}

async function handleChatSend() {
  if (!chatForm.content.trim()) return
  if (!chatForm.captchaCode.trim()) {
    ElMessage.warning('请先填写验证码')
    return
  }
  chatSending.value = true
  const question = chatForm.content.trim()
  chatHistory.value.push({ role: 'me', content: question })
  chatForm.content = ''
  try {
    // 后端返回 ChatReplyVo { reply, status, recalled, relatedMemories }
    const res = await chatSendMemory({
      memoryId: chatForm.memoryId,
      content: question,
      captchaCode: chatForm.captchaCode,
      captchaKey: chatForm.captchaKey
    })
    chatHistory.value.push({ role: 'ai', content: res?.reply || '(无回复)' })
    // 成功后刷新验证码（一次性）
    loadCaptcha()
  } catch (e) {
    ElMessage.error(e?.message || '发送失败')
    chatHistory.value.pop()
  } finally {
    chatSending.value = false
  }
}

// ============ 情绪归并 ============
async function handleMergeEmotions() {
  mergingEmotion.value = true
  try {
    await mergeMemoryEmotions(queryParams.userId || undefined)
    ElMessage.success('情绪标签已归并')
    loadEmotionStats()
    getList()
  } catch (e) {
    ElMessage.error(e?.message || '归并失败')
  } finally {
    mergingEmotion.value = false
  }
}

// ============ 展示辅助 ============
function getStatusType(status) {
  if (status === 3) return 'success'   // 已成念
  if (status === 4) return 'info'      // 已废弃
  if (status === 2) return 'warning'   // 对话中
  return 'primary'                     // 未开始(1)
}

function getStatusLabel(status) {
  if (status === 3) return '已成念'
  if (status === 4) return '已废弃'
  if (status === 2) return '对话中'
  if (status === 1) return '未开始'
  return '未知'
}

function getPrivacyType(privacy) {
  if (privacy === 0) return 'success'   // 公开
  if (privacy === 2) return 'info'      // 封存
  return 'warning'                     // 私密(1)
}

function getPrivacyLabel(privacy) {
  if (privacy === 0) return '公开'
  if (privacy === 2) return '封存'
  return '私密'
}

// ============ 隐私设置 ============
function openPrivacy(row) {
  privacyForm.memoryId = row.id
  privacyForm.privacy = row.privacy ?? 1
  privacyVisible.value = true
}

async function handleSetPrivacy() {
  const target = privacyForm.privacy
  // 设公开 = 立即发博客（D12）；封存 = 本人也需 PIN（D2）
  if (target === 0) {
    try {
      await ElMessageBox.confirm(
        '设为公开后会立即投影为博客公开文章，所有人可见。确定继续？',
        '公开确认',
        { confirmButtonText: '确认公开', cancelButtonText: '取消', type: 'warning' }
      )
    } catch (e) {
      return
    }
  }
  if (target === 2) {
    if (!pinStatus.value.hasPin) {
      ElMessage.warning('请先设置封存 PIN，否则封存后无法再查看')
      openPinSet()
      return
    }
    try {
      await ElMessageBox.confirm(
        '封存后本人也需要输入 PIN 才能查看，AI 永不召回。确定继续？',
        '封存确认',
        { confirmButtonText: '确认封存', cancelButtonText: '取消', type: 'warning' }
      )
    } catch (e) {
      return
    }
  }
  privacyLoading.value = true
  try {
    await setMemoryPrivacy(privacyForm.memoryId, target)
    ElMessage.success('隐私设置已更新')
    privacyVisible.value = false
    getList()
  } catch (e) {
    ElMessage.error(e?.message || '设置失败')
  } finally {
    privacyLoading.value = false
  }
}

// ============ 封存 PIN ============
async function loadPinStatus() {
  try {
    const res = await getMemoryPinStatus()
    pinStatus.value = res || { hasPin: false, locked: false, unlocked: false }
  } catch (e) {
    pinStatus.value = { hasPin: false, locked: false, unlocked: false }
  }
}

function openPinSet() {
  pinForm.pin = ''
  pinForm.confirm = ''
  pinSetVisible.value = true
}

async function handlePinSet() {
  const pin = String(pinForm.pin || '').trim()
  if (!/^\d{4,6}$/.test(pin)) {
    ElMessage.warning('PIN 需为 4~6 位数字')
    return
  }
  if (pin !== String(pinForm.confirm || '').trim()) {
    ElMessage.warning('两次输入的 PIN 不一致')
    return
  }
  pinSaving.value = true
  try {
    await setMemoryPin(pin)
    ElMessage.success('PIN 已保存，请重新解锁')
    // 换 PIN 会吊销旧令牌
    localStorage.removeItem('sealedToken')
    pinSetVisible.value = false
    await loadPinStatus()
    getList()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    pinSaving.value = false
  }
}

function openPinVerify() {
  verifyForm.pin = ''
  pinVerifyVisible.value = true
}

async function handlePinVerify() {
  const pin = String(verifyForm.pin || '').trim()
  if (!/^\d{4,6}$/.test(pin)) {
    ElMessage.warning('PIN 需为 4~6 位数字')
    return
  }
  pinVerifying.value = true
  try {
    const res = await verifyMemoryPin(pin)
    if (res?.token) {
      localStorage.setItem('sealedToken', res.token)
    }
    pinVerifyVisible.value = false
    ElMessage.success('已解锁，封存记忆现在可见')
    await loadPinStatus()
    getList()
  } catch (e) {
    ElMessage.error(e?.message || '解锁失败')
  } finally {
    pinVerifying.value = false
  }
}

// ============ 编辑记忆 ============
async function openEditDialog(row) {
  loadEmotionOptions()
  editForm.memoryId = row.id
  editForm.autoTitle = row.autoTitle || ''
  editForm.emotions = row.emotions ? String(row.emotions).split(',').map(s => s.trim()).filter(Boolean) : []
  editForm.finalDiary = ''
  // 尝试拉取详情正文
  try {
    const detail = await getMemoryDetail(row.id)
    editForm.finalDiary = detail?.note?.finalDiary || ''
  } catch (e) {
    editForm.finalDiary = ''
  }
  editVisible.value = true
}

async function loadEmotionOptions() {
  try {
    const res = await getMemoryEmotionStats(queryParams.userId || undefined)
    const list = Array.isArray(res) ? res : []
    const seen = new Set()
    const arr = list.map(i => i?.emotion).filter(e => e && !seen.has(e) && seen.add(e))
    const presets = ['松弛', '治愈', '疲惫', '开心', '平淡', '想念', '充实']
    presets.forEach(p => { if (!seen.has(p)) { seen.add(p); arr.push(p) } })
    emotionOptions.value = arr
  } catch (e) {
    emotionOptions.value = ['松弛', '治愈', '疲惫', '开心', '平淡', '想念', '充实']
  }
}

async function handleEditSave() {
  if (editForm.memoryId == null) return
  editSaving.value = true
  try {
    await updateMemory({
      memoryId: editForm.memoryId,
      autoTitle: editForm.autoTitle.trim() || undefined,
      emotions: editForm.emotions,
      finalDiary: editForm.finalDiary
    })
    ElMessage.success('已保存')
    editVisible.value = false
    getList()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    editSaving.value = false
  }
}

function formatDateTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

// ============ 情绪统计 ============
async function loadEmotionStats() {
  try {
    const res = await getMemoryEmotionStats(queryParams.userId || undefined)
    emotionStats.value = Array.isArray(res) ? res : []
  } catch (e) {
    emotionStats.value = []
  }
}

// 管理员加载用户下拉
async function loadUsers() {
  if (!isAdmin) return
  try {
    const res = await getUserPage({ current: 1, size: 200, keyword: '' })
    userOptions.value = res?.records || []
  } catch (e) {
    userOptions.value = []
  }
}

onMounted(() => {
  loadPinStatus()
  loadStat()
  loadEmotionStats()
  loadUsers()
  getList()
})
</script>

<style lang="scss" scoped>
.memory-management {
  .section-card {
    background-color: #fff;
    padding: 20px 24px;
    border-radius: 8px;
    margin-bottom: 16px;

    .section-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;

      h3 {
        margin: 0;
        font-size: 1rem;
        font-weight: 700;
      }
    }
  }

  .stat-grid {
    display: grid;
    grid-template-columns: repeat(4, 1fr);
    gap: 16px;

    .stat-item {
      background: var(--el-fill-color-light);
      border-radius: 8px;
      padding: 16px;
      text-align: center;

      .stat-value {
        font-size: 1.4rem;
        font-weight: 800;
        color: var(--el-color-primary);

        &.stat-date {
          font-size: 0.95rem;
        }
      }

      .stat-label {
        font-size: 0.8rem;
        color: var(--el-text-color-secondary);
        margin-top: 4px;
      }
    }
  }

  // 情绪锚点分布
  .emotion-block {
    margin-top: 20px;
    border-top: 1px solid var(--el-border-color-lighter);
    padding-top: 16px;

    .emotion-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 10px;

      .emotion-title {
        font-size: 0.9rem;
        font-weight: 700;
        color: var(--el-text-color-primary);
      }
    }

    .emotion-tags {
      display: flex;
      flex-wrap: wrap;
      gap: 8px;

      .emotion-tag {
        cursor: default;

        .emotion-count {
          font-size: 12px;
          opacity: 0.75;
          margin-left: 4px;
        }
      }
    }
  }

  .privacy-tip {
    p {
      margin: 2px 0;
      font-size: 13px;
      color: var(--el-text-color-regular);
      line-height: 1.7;
    }
  }

  .filter-bar {
    display: flex;
    gap: 12px;
    align-items: center;
    flex-wrap: wrap;
  }

  .pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }

  .text-secondary {
    color: var(--el-text-color-secondary);
    font-size: 0.875rem;
  }

  .tip {
    font-size: 12px;
    color: #909399;
    margin-bottom: 8px;
  }

  // 详情
  .detail-body {
    .detail-note {
      background: var(--el-fill-color-light);
      padding: 14px 16px;
      border-radius: 6px;
      font-size: 13px;
      line-height: 1.9;
      margin-bottom: 16px;

      p {
        margin: 0;
      }
    }

    .detail-section {
      margin-bottom: 16px;

      h4 {
        margin: 0 0 8px;
        font-size: 0.9rem;
        color: var(--el-text-color-regular);
        border-left: 3px solid var(--el-color-primary);
        padding-left: 8px;
      }

      .diary-pre {
        max-height: 240px;
        overflow: auto;
        background: #f6f8fa;
        padding: 12px;
        border-radius: 6px;
        font-size: 13px;
        white-space: pre-wrap;
        word-break: break-word;
      }

      .opening-text {
        background: #f0f9eb;
        color: #529b2e;
        padding: 12px;
        border-radius: 6px;
        font-size: 13px;
      }
    }
  }

  .msg-item {
    display: flex;
    flex-direction: column;
    gap: 4px;
    padding: 8px 12px;
    margin-bottom: 8px;
    border-radius: 6px;
    font-size: 13px;

    &.type-1 { background: #ecf5ff; }
    &.type-2 { background: var(--el-fill-color-light); }
    &.type-3 { background: #fdf6ec; }

    .msg-img { line-height: 0; }
    .msg-time {
      font-size: 11px;
      color: #909399;
    }
  }

  // 对话
  .chat-box {
    .chat-history {
      max-height: 360px;
      overflow: auto;
      margin-bottom: 12px;
    }

    .chat-msg {
      margin-bottom: 10px;

      .chat-role {
        font-size: 12px;
        color: #909399;
        margin-bottom: 2px;
      }

      .chat-content {
        background: var(--el-fill-color-light);
        padding: 8px 12px;
        border-radius: 6px;
        font-size: 13px;
        white-space: pre-wrap;
        word-break: break-word;
      }

      &.me .chat-content {
        background: #ecf5ff;
      }
    }

    .chat-captcha {
      display: flex;
      gap: 8px;
      align-items: center;
      margin-bottom: 12px;

      .captcha-img {
        width: 120px;
        height: 40px;
        border-radius: 4px;
        border: 1px solid #dcdfe6;
        object-fit: cover;
      }
    }

    .chat-input-bar {
      display: flex;
      gap: 8px;
      align-items: center;
    }
  }
}
.pin-bar {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}

.danger-text {
  color: #f56c6c;
  font-weight: 600;
}
</style>
