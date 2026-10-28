<template>
  <div class="idea-board-page" v-loading="loading">
    <!-- 工具栏 -->
    <div class="section-card toolbar">
      <div class="toolbar-left">
        <el-select v-if="isAdmin" v-model="viewUserId" placeholder="用户筛选" clearable filterable style="width: 200px"
          @change="loadBoard">
          <el-option v-for="u in userOptions" :key="u.id" :label="(u.nickname || u.username) + ' (#' + u.id + ')'" :value="u.id" />
        </el-select>
        <el-input v-model="keyword" placeholder="搜索想法内容 / 标签" clearable style="width: 240px" />
        <el-tag v-for="s in states" :key="s.code" size="small" effect="plain">
          {{ s.name }} {{ countOf(s.code) }}
        </el-tag>
      </div>
      <div class="toolbar-right">
        <el-button :icon="MagicStick" @click="openPromptDialog">念生提示词</el-button>
        <el-button type="primary" :icon="Plus" @click="openCreate">新建想法</el-button>
      </div>
    </div>

    <!-- 看板 -->
    <div class="board">
      <div v-for="state in states" :key="state.code" class="board-column"
        :class="{ 'drop-active': dragOverState === state.code }"
        @dragover.prevent="onDragOver(state.code)"
        @dragleave="onDragLeave(state.code)"
        @drop.prevent="onDrop(state.code)">
        <div class="column-header">
          <span class="dot" :style="{ background: state.color || '#909399' }"></span>
          <span class="column-name">{{ state.name }}</span>
          <span class="column-count">{{ filteredIdeas(state.code).length }}</span>
          <el-tag v-if="state.isTerminal === 1" size="small" type="info">终态</el-tag>
        </div>
        <div class="column-body">
          <div v-for="idea in filteredIdeas(state.code)" :key="idea.id" class="idea-card"
            draggable="true"
            @dragstart="onDragStart(idea)"
            @dragend="onDragEnd">
            <div class="card-top">
              <span class="card-id">#{{ idea.id }}</span>
              <el-tag v-if="idea.source === 2" size="small" type="success">来自闪光点</el-tag>
              <span v-if="isAdmin" class="card-user">{{ idea.username || ('#' + idea.userId) }}</span>
            </div>
            <div class="card-content">{{ idea.content }}</div>
            <div class="card-tags" v-if="idea.tags">
              <el-tag v-for="t in String(idea.tags).split(',').filter(Boolean)" :key="t" size="small" effect="plain">
                {{ t }}
              </el-tag>
            </div>
            <div class="card-foot">
              <span class="priority">P{{ idea.priority ?? 0 }}</span>
              <span class="time">{{ formatDateTime(idea.createTime) }}</span>
              <el-dropdown trigger="click" @command="(cmd) => handleCardCommand(cmd, idea)">
                <el-button size="small" link type="primary">操作</el-button>
                <template #dropdown>
                  <el-dropdown-menu>
                    <el-dropdown-item command="edit">编辑</el-dropdown-item>
                    <el-dropdown-item command="move" :disabled="!idea.allowedTargets?.length">状态迁移</el-dropdown-item>
                    <el-dropdown-item command="history">迁移历史</el-dropdown-item>
                    <el-dropdown-item command="analyze" divided>AI 分析</el-dropdown-item>
                    <el-dropdown-item command="challenge">AI 质疑</el-dropdown-item>
                    <el-dropdown-item command="delete" divided>删除</el-dropdown-item>
                  </el-dropdown-menu>
                </template>
              </el-dropdown>
            </div>
          </div>
          <el-empty v-if="!filteredIdeas(state.code).length" description="暂无想法" :image-size="50" />
        </div>
      </div>
    </div>

    <!-- 新建 / 编辑 -->
    <el-dialog v-model="editVisible" :title="editForm.id ? '编辑想法' : '新建想法'" width="560px">
      <el-form label-width="80px">
        <el-form-item label="内容">
          <el-input v-model="editForm.content" type="textarea" :rows="6" maxlength="4000" show-word-limit
            placeholder="把脑子里的那个念头原样写下来" />
        </el-form-item>
        <el-form-item label="标签">
          <el-select v-model="editForm.tags" multiple filterable allow-create default-first-option
            placeholder="回车创建标签（最多 8 个）" style="width: 100%">
            <el-option v-for="t in tagOptions" :key="t" :label="t" :value="t" />
          </el-select>
        </el-form-item>
        <el-form-item label="优先级">
          <el-select v-model="editForm.priority" style="width: 120px">
            <el-option v-for="p in 10" :key="p - 1" :label="'P' + (p - 1)" :value="p - 1" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="editVisible = false">取消</el-button>
          <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 状态迁移 -->
    <el-dialog v-model="moveVisible" title="状态迁移" width="420px">
      <p class="tip">允许的目标由 idea_transition_rule 表决定，非法迁移会被后端拒绝（409）。</p>
      <el-form label-width="90px" style="margin-top:12px;">
        <el-form-item label="目标状态">
          <el-select v-model="moveForm.toState" style="width: 100%">
            <el-option v-for="code in moveTargets" :key="code" :label="stateLabel(code)" :value="code" />
          </el-select>
        </el-form-item>
        <el-form-item label="迁移理由">
          <el-input v-model="moveForm.reason" type="textarea" :rows="3" maxlength="255" placeholder="可选：为什么迁移" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="moveVisible = false">取消</el-button>
          <el-button type="primary" :loading="moving" @click="handleMove">迁移</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 迁移历史 -->
    <el-drawer v-model="historyVisible" title="迁移历史" size="420px">
      <div v-loading="historyLoading">
        <el-timeline v-if="historyList.length">
          <el-timeline-item v-for="h in historyList" :key="h.id" :timestamp="formatDateTime(h.createTime)">
            <div class="history-line">
              <b>{{ h.fromState ? stateLabel(h.fromState) : '起点' }}</b>
              <span class="arrow">→</span>
              <b>{{ stateLabel(h.toState) }}</b>
              <el-tag size="small" type="info">{{ h.trigger }}</el-tag>
            </div>
            <div v-if="h.reason" class="history-reason">{{ h.reason }}</div>
          </el-timeline-item>
        </el-timeline>
        <el-empty v-else description="暂无迁移记录" :image-size="60" />
      </div>
    </el-drawer>

    <!-- AI 分析 / 质疑 -->
    <el-drawer v-model="analysisVisible" :title="analysisTitle" size="520px">
      <div v-loading="analysisLoading">
        <el-alert v-if="analysis?.degraded === 1" type="warning" :closable="false" show-icon
          :title="analysis?.result?.message || '模型输出无法解析，已降级为纯文本'" style="margin-bottom:12px;" />
        <template v-if="analysisSections.length">
          <div v-for="sec in analysisSections" :key="sec.key" class="analysis-section">
            <h4>{{ sec.label }}</h4>
            <ul>
              <li v-for="(item, i) in sec.items" :key="i">{{ item }}</li>
            </ul>
          </div>
        </template>
        <pre v-else-if="analysis?.result?.raw" class="raw-text">{{ analysis.result.raw }}</pre>
        <div class="analysis-meta" v-if="analysis">
          模型：{{ analysis.model || '-' }} · 场景：{{ analysis.promptScene || '-' }}
        </div>

        <h4 class="prev-title">历史分析</h4>
        <el-collapse v-if="analysisHistory.length">
          <el-collapse-item v-for="a in analysisHistory" :key="a.id"
            :title="(a.kind === 'challenge' ? '质疑' : '分析') + ' · ' + formatDateTime(a.createTime)">
            <pre class="raw-text">{{ JSON.stringify(a.result, null, 2) }}</pre>
          </el-collapse-item>
        </el-collapse>
        <el-empty v-else description="还没有历史分析" :image-size="50" />
      </div>
    </el-drawer>

    <!-- Prompt 治理 -->
    <el-dialog v-model="promptVisible" title="念生提示词（Prompt 治理）" width="760px">
      <el-table :data="prompts" v-loading="promptLoading" border max-height="320">
        <el-table-column prop="scene" label="场景" width="180" />
        <el-table-column prop="version" label="版本" width="70" align="center" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="scope">
            <el-tag :type="scope.row.isActive === 1 ? 'success' : 'info'" size="small">
              {{ scope.row.isActive === 1 ? '生效中' : '备用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="内容" min-width="220" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.content }}</template>
        </el-table-column>
        <el-table-column label="操作" width="90" align="center">
          <template #default="scope">
            <el-button v-if="scope.row.isActive !== 1" size="small" link type="primary"
              @click="handleActivatePrompt(scope.row)">启用</el-button>
            <span v-else class="text-secondary">-</span>
          </template>
        </el-table-column>
      </el-table>

      <el-divider content-position="left">新增版本</el-divider>
      <el-form label-width="80px">
        <el-form-item label="场景">
          <el-select v-model="promptForm.scene" style="width: 260px">
            <el-option label="分析（analyze）" value="niansheng.analyze" />
            <el-option label="质疑（challenge）" value="niansheng.challenge" />
          </el-select>
        </el-form-item>
        <el-form-item label="内容">
          <el-input v-model="promptForm.content" type="textarea" :rows="6" placeholder="必须要求模型输出严格 JSON" />
        </el-form-item>
        <el-form-item label="备注">
          <el-input v-model="promptForm.remark" maxlength="255" placeholder="可选" />
        </el-form-item>
        <el-form-item label="立即启用">
          <el-switch v-model="promptForm.activate" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="promptVisible = false">关闭</el-button>
          <el-button type="primary" :loading="promptSaving" @click="handleCreatePrompt">保存新版本</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, MagicStick } from '@element-plus/icons-vue'
import {
  getIdeaBoard,
  createIdea,
  updateIdea,
  deleteIdea,
  moveIdea,
  getIdeaHistory,
  analyzeIdea,
  challengeIdea,
  getIdeaAnalyses,
  getAiPrompts,
  createAiPrompt,
  activateAiPrompt
} from '@/api/idea'
import { getUserPage } from '@/api/user'

const isAdmin = localStorage.getItem('role') === 'ADMIN'
const loading = ref(false)
const keyword = ref('')
const viewUserId = ref(null)
const userOptions = ref([])
const boards = ref([])
const states = ref([])

const editVisible = ref(false)
const saving = ref(false)
const editForm = reactive({ id: null, content: '', tags: [], priority: 0 })
const tagOptions = ref(['产品', '写作', '技术', '生活', '商业', '待验证'])

const moveVisible = ref(false)
const moving = ref(false)
const moveForm = reactive({ id: null, fromState: '', toState: '', reason: '' })
const moveTargets = ref([])

const historyVisible = ref(false)
const historyLoading = ref(false)
const historyList = ref([])

const analysisVisible = ref(false)
const analysisLoading = ref(false)
const analysis = ref(null)
const analysisHistory = ref([])
const analysisTitle = ref('AI 分析')

const promptVisible = ref(false)
const promptLoading = ref(false)
const promptSaving = ref(false)
const prompts = ref([])
const promptForm = reactive({ scene: 'niansheng.analyze', content: '', remark: '', activate: false })

const draggedIdea = ref(null)
const dragOverState = ref('')

const analysisLabels = {
  claims: '核心主张',
  assumptions: '隐含假设',
  risks: '可能踩的坑',
  questions: '值得追问',
  counterpoints: '反面论据',
  blindSpots: '盲区',
  falsify: '可证伪的观察'
}

const analysisSections = computed(() => {
  const result = analysis.value?.result
  if (!result || typeof result !== 'object') return []
  return Object.keys(analysisLabels)
    .filter((k) => Array.isArray(result[k]) && result[k].length)
    .map((k) => ({ key: k, label: analysisLabels[k], items: result[k] }))
})

function stateLabel(code) {
  const s = states.value.find((x) => x.code === code)
  return s ? s.name : code
}

function countOf(code) {
  return filteredIdeas(code).length
}

function filteredIdeas(code) {
  const col = boards.value.find((c) => c.code === code)
  const list = col?.ideas || []
  const kw = keyword.value.trim()
  if (!kw) return list
  return list.filter((i) => (i.content || '').includes(kw) || (i.tags || '').includes(kw))
}

function formatDateTime(t) {
  if (!t) return '-'
  return String(t).replace('T', ' ').slice(0, 19)
}

async function loadBoard() {
  loading.value = true
  try {
    boards.value = await getIdeaBoard(viewUserId.value || undefined) || []
    // 列顺序以 states 为准（boards 已按 sort 返回）
    states.value = boards.value.map((c) => ({
      code: c.code,
      name: c.name,
      sort: c.sort,
      color: c.color,
      isTerminal: c.isTerminal
    }))
  } catch (e) {
    ElMessage.error(e?.message || '加载看板失败')
  } finally {
    loading.value = false
  }
}

async function loadUsers() {
  if (!isAdmin) return
  try {
    const res = await getUserPage({ current: 1, size: 200, keyword: '' })
    userOptions.value = res?.records || []
  } catch (e) {
    userOptions.value = []
  }
}

// ==================== 新建 / 编辑 ====================
function openCreate() {
  editForm.id = null
  editForm.content = ''
  editForm.tags = []
  editForm.priority = 0
  editVisible.value = true
}

function openEdit(idea) {
  editForm.id = idea.id
  editForm.content = idea.content || ''
  editForm.tags = idea.tags ? String(idea.tags).split(',').filter(Boolean) : []
  editForm.priority = idea.priority ?? 0
  editVisible.value = true
}

async function handleSave() {
  if (!editForm.content.trim()) {
    ElMessage.warning('想法内容不能为空')
    return
  }
  saving.value = true
  const payload = {
    content: editForm.content.trim(),
    tags: editForm.tags,
    priority: editForm.priority
  }
  try {
    if (editForm.id) {
      await updateIdea(editForm.id, payload, viewUserId.value || undefined)
    } else {
      await createIdea(payload)
    }
    ElMessage.success('已保存')
    editVisible.value = false
    loadBoard()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

// ==================== 状态迁移 ====================
function openMove(idea) {
  moveForm.id = idea.id
  moveForm.fromState = idea.state
  moveTargets.value = idea.allowedTargets || []
  moveForm.toState = moveTargets.value[0] || ''
  moveForm.reason = ''
  moveVisible.value = true
}

async function handleMove() {
  if (!moveForm.toState) {
    ElMessage.warning('请选择目标状态')
    return
  }
  moving.value = true
  try {
    await moveIdea(moveForm.id, moveForm.toState, moveForm.reason, viewUserId.value || undefined)
    ElMessage.success('状态已迁移')
    moveVisible.value = false
    loadBoard()
  } catch (e) {
    ElMessage.error(e?.message || '迁移失败')
  } finally {
    moving.value = false
  }
}

// ==================== 拖拽 ====================
function onDragStart(idea) {
  draggedIdea.value = idea
}

function onDragEnd() {
  draggedIdea.value = null
  dragOverState.value = ''
}

function onDragOver(code) {
  if (draggedIdea.value && draggedIdea.value.state !== code) {
    dragOverState.value = code
  }
}

function onDragLeave(code) {
  if (dragOverState.value === code) {
    dragOverState.value = ''
  }
}

async function onDrop(code) {
  const idea = draggedIdea.value
  dragOverState.value = ''
  draggedIdea.value = null
  if (!idea || idea.state === code) return
  try {
    await moveIdea(idea.id, code, '看板拖拽', viewUserId.value || undefined)
    ElMessage.success('已移动到「' + stateLabel(code) + '」')
    loadBoard()
  } catch (e) {
    ElMessage.error(e?.message || '不允许的迁移')
  }
}

// ==================== 历史 ====================
async function openHistory(idea) {
  historyVisible.value = true
  historyLoading.value = true
  historyList.value = []
  try {
    historyList.value = await getIdeaHistory(idea.id, viewUserId.value || undefined) || []
  } catch (e) {
    ElMessage.error(e?.message || '加载历史失败')
  } finally {
    historyLoading.value = false
  }
}

// ==================== AI ====================
async function runAnalysis(idea, kind) {
  analysisVisible.value = true
  analysisLoading.value = true
  analysis.value = null
  analysisHistory.value = []
  analysisTitle.value = kind === 'challenge' ? 'AI 质疑' : 'AI 分析'
  try {
    analysis.value = kind === 'challenge'
      ? await challengeIdea(idea.id, viewUserId.value || undefined)
      : await analyzeIdea(idea.id, viewUserId.value || undefined)
    analysisHistory.value = await getIdeaAnalyses(idea.id, undefined, viewUserId.value || undefined) || []
  } catch (e) {
    ElMessage.error(e?.message || 'AI 调用失败')
  } finally {
    analysisLoading.value = false
  }
}

// ==================== 菜单 ====================
async function handleCardCommand(cmd, idea) {
  if (cmd === 'edit') return openEdit(idea)
  if (cmd === 'move') return openMove(idea)
  if (cmd === 'history') return openHistory(idea)
  if (cmd === 'analyze') return runAnalysis(idea, 'analyze')
  if (cmd === 'challenge') return runAnalysis(idea, 'challenge')
  if (cmd === 'delete') {
    try {
      await ElMessageBox.confirm(`确定删除想法 #${idea.id}？删除后不进回收站。`, '删除确认', {
        confirmButtonText: '删除',
        cancelButtonText: '取消',
        type: 'warning'
      })
      await deleteIdea(idea.id, viewUserId.value || undefined)
      ElMessage.success('已删除')
      loadBoard()
    } catch (e) { /* 用户取消 */ }
  }
}

// ==================== Prompt 治理 ====================
async function openPromptDialog() {
  promptVisible.value = true
  await loadPrompts()
}

async function loadPrompts() {
  promptLoading.value = true
  try {
    prompts.value = await getAiPrompts() || []
  } catch (e) {
    ElMessage.error(e?.message || '加载提示词失败')
  } finally {
    promptLoading.value = false
  }
}

async function handleActivatePrompt(row) {
  try {
    await activateAiPrompt(row.id)
    ElMessage.success('已启用 v' + row.version)
    loadPrompts()
  } catch (e) {
    ElMessage.error(e?.message || '启用失败')
  }
}

async function handleCreatePrompt() {
  if (!promptForm.content.trim()) {
    ElMessage.warning('提示词内容不能为空')
    return
  }
  promptSaving.value = true
  try {
    await createAiPrompt({
      scene: promptForm.scene,
      content: promptForm.content.trim(),
      remark: promptForm.remark,
      activate: promptForm.activate
    })
    ElMessage.success('新版本已保存')
    promptForm.content = ''
    promptForm.remark = ''
    promptForm.activate = false
    loadPrompts()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    promptSaving.value = false
  }
}

onMounted(() => {
  loadUsers()
  loadBoard()
})
</script>

<style lang="scss" scoped>
.idea-board-page {
  padding: 4px;
}

.toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 16px;
  margin-bottom: 12px;
  background: #fff;
  border-radius: 8px;
}

.toolbar-left,
.toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.board {
  display: grid;
  grid-template-columns: repeat(6, minmax(220px, 1fr));
  gap: 12px;
  overflow-x: auto;
  padding-bottom: 8px;
}

.board-column {
  background: #f5f7fa;
  border-radius: 8px;
  min-height: 260px;
  display: flex;
  flex-direction: column;
  border: 1px dashed transparent;
  transition: border-color 0.15s, background 0.15s;

  &.drop-active {
    border-color: #409eff;
    background: #ecf5ff;
  }
}

.column-header {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 10px 12px;
  border-bottom: 1px solid #ebeef5;
  font-weight: 600;
  color: #303133;
}

.dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
}

.column-count {
  margin-left: auto;
  font-size: 12px;
  color: #909399;
  background: #fff;
  border-radius: 10px;
  padding: 0 8px;
}

.column-body {
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  overflow-y: auto;
  max-height: 62vh;
}

.idea-card {
  background: #fff;
  border-radius: 6px;
  padding: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.06);
  cursor: grab;
  transition: box-shadow 0.15s;

  &:hover {
    box-shadow: 0 3px 10px rgba(0, 0, 0, 0.1);
  }
}

.card-top {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 11px;
  color: #909399;
}

.card-user {
  margin-left: auto;
}

.card-content {
  margin: 8px 0;
  font-size: 13px;
  line-height: 1.5;
  color: #303133;
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
  white-space: pre-wrap;
}

.card-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 6px;
}

.card-foot {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: #909399;
}

.priority {
  font-weight: 600;
  color: #e6a23c;
}

.time {
  margin-left: auto;
}

.tip {
  font-size: 12px;
  color: #909399;
  margin: 0;
}

.history-line {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
}

.arrow {
  color: #909399;
}

.history-reason {
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.analysis-section {
  margin-bottom: 14px;

  h4 {
    margin: 0 0 6px;
    font-size: 14px;
    color: #303133;
  }

  ul {
    margin: 0;
    padding-left: 18px;
    color: #606266;
    font-size: 13px;
    line-height: 1.7;
  }
}

.raw-text {
  white-space: pre-wrap;
  word-break: break-word;
  background: #f5f7fa;
  padding: 10px;
  border-radius: 6px;
  font-size: 12px;
  color: #606266;
}

.analysis-meta {
  font-size: 12px;
  color: #909399;
  margin: 8px 0 16px;
}

.prev-title {
  font-size: 14px;
  margin: 12px 0 8px;
  color: #303133;
}

@media (max-width: 1200px) {
  .board {
    grid-template-columns: repeat(6, 240px);
  }
}
</style>
