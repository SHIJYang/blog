<!--
  拾光碎念 · 生活页（组装层）
  负责：
  - 登录态判断
  - 布局（左侧上传+列表 / 右侧对话）
  - 组件协作（选中记忆、操作后的刷新）
  - 记忆级操作：凝聚 / 发布 / 废弃（这些需要确认弹窗，放在组装层统一处理）
  子组件（src/components/memory/）：
    MemoryUploader      上传照片创建记忆
    MemoryListPanel     小念记得列表 + 搜索 + 每条操作按钮
    ChatConversation    与选中记忆对话（文字/图片/验证码）
    MemoryEditDialog    编辑记忆（标题/情绪/正文）
    MemoryPrivacyDialog 隐私设置
-->
<template>
  <div class="life-page">
    <!-- 未登录 -->
    <div v-if="!loggedIn" class="center-block">
      <p class="muted-text">这里是被温柔收藏的私人时光。</p>
      <el-button type="primary" round @click="goLogin">登录后开始记录</el-button>
    </div>

    <!-- 已登录 -->
    <div v-else class="life-container">
      <!-- 左侧：上传 + 记忆列表 -->
      <aside class="life-side">
        <MemoryUploader @created="handleCreated" />
        <div class="memory-section">
          <MemoryListPanel
            :active-id="currentMemoryId"
            :refresh-tick="refreshTick"
            @select="handleSelect"
            @edit="handleEdit"
            @condense="handleCondense"
            @publish="handlePublish"
            @privacy="handlePrivacy"
            @abandon="handleAbandon"
          />
        </div>
      </aside>

      <!-- 右侧：对话区 -->
      <main class="life-main">
        <div v-if="!currentMemoryId" class="center-block">
          <p class="muted-text">上传一张照片，开始和拾光说话。</p>
          <p class="muted-small">或从左侧选择一段记忆继续。</p>
        </div>
        <ChatConversation v-else
          :memory-id="currentMemoryId"
          :cover="currentCover"
          :title="currentTitle"
          :emotions="currentEmotions"
          @message-sent="doRefresh"
        />
      </main>
    </div>

    <!-- 编辑记忆弹窗 -->
    <MemoryEditDialog v-model="editVisible" :memory-id="editMemoryId" @saved="doRefresh" />

    <!-- 隐私设置弹窗 -->
    <MemoryPrivacyDialog v-model="privacyVisible" :memory-id="privacyMemoryId" :current="privacyCurrent" @saved="doRefresh" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import MemoryUploader from '@/components/memory/MemoryUploader.vue'
import MemoryListPanel, { type MemItem } from '@/components/memory/MemoryListPanel.vue'
import ChatConversation from '@/components/memory/ChatConversation.vue'
import MemoryEditDialog from '@/components/memory/MemoryEditDialog.vue'
import MemoryPrivacyDialog from '@/components/memory/MemoryPrivacyDialog.vue'
import { condenseMemory, publishMemory, abandonMemory } from '@/api/memory'

const router = useRouter()

// ============ 登录态 ============
const loggedIn = computed(() => !!localStorage.getItem('token'))
function goLogin() { router.push({ path: '/login', query: { redirect: '/home' } }) }

// ============ 当前选中记忆（单一数据源） ============
const currentMemoryId = ref<number | null>(null)
const currentCover = ref('')
const currentTitle = ref('')
const currentEmotions = ref('')
const refreshTick = ref(0)

// 列表操作后触发刷新
function doRefresh() { refreshTick.value++ }

// 上传创建成功后：刷新列表 + 进入该记忆对话
function handleCreated(note: any) {
  doRefresh()
  if (note?.id) {
    currentMemoryId.value = note.id
    currentCover.value = note.coverThumb || note.coverPhoto || ''
    currentTitle.value = note.autoTitle || '我的记忆'
    currentEmotions.value = note.emotions || ''
  }
}

// 选中某条记忆 → 进入对话
function handleSelect(m: MemItem) {
  currentMemoryId.value = m.id
  currentCover.value = m.coverThumb || m.coverPhoto || ''
  currentTitle.value = m.autoTitle || '我的记忆'
  currentEmotions.value = m.emotions || ''
}

// ============ 编辑 ============
const editVisible = ref(false)
const editMemoryId = ref<number | null>(null)
function handleEdit(m: MemItem) { editMemoryId.value = m.id; editVisible.value = true }

// ============ 凝聚（对话中 → 已成念） ============
function handleCondense(m: MemItem) {
  ElMessageBox.confirm(`将把「${m.autoTitle || '这条记忆'}」凝聚为日记，是否继续？`, '凝聚确认', {
    confirmButtonText: '凝聚', cancelButtonText: '取消', type: 'info'
  }).then(async () => {
    try {
      await condenseMemory(m.id, undefined)
      ElMessage.success('凝聚完成')
      doRefresh()
    } catch (e: any) { ElMessage.error(e?.message || '凝聚失败') }
  }).catch(() => {})
}

// ============ 发布（已成念 → 博客文章） ============
function handlePublish(m: MemItem) {
  ElMessageBox.confirm(`把「${m.autoTitle || '这条记忆'}」发布为博客文章？`, '发布确认', {
    confirmButtonText: '发布', cancelButtonText: '取消', type: 'info'
  }).then(async () => {
    try {
      const aid = await publishMemory(m.id)
      ElMessage.success('已发布为文章')
      doRefresh()
    } catch (e: any) { ElMessage.error(e?.message || '发布失败') }
  }).catch(() => {})
}

// ============ 隐私 ============
const privacyVisible = ref(false)
const privacyMemoryId = ref<number | null>(null)
const privacyCurrent = ref(1)
function handlePrivacy(m: MemItem) {
  privacyMemoryId.value = m.id
  privacyCurrent.value = m.privacy ?? 1
  privacyVisible.value = true
}

// ============ 废弃（删除） ============
function handleAbandon(m: MemItem) {
  ElMessageBox.confirm(`确定废弃「${m.autoTitle || '这条记忆'}」吗？废弃后不可恢复。`, '废弃确认', {
    confirmButtonText: '确定废弃', cancelButtonText: '取消', type: 'warning'
  }).then(async () => {
    try {
      await abandonMemory(m.id)
      ElMessage.success('已废弃')
      if (currentMemoryId.value === m.id) { currentMemoryId.value = null }
      doRefresh()
    } catch (e: any) { ElMessage.error(e?.message || '操作失败') }
  }).catch(() => {})
}
</script>

<style scoped lang="scss">
.life-page { max-width: 1000px; margin: 0 auto; padding: 20px 24px 60px; }

.center-block {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  min-height: 55vh; gap: 12px;
}
.muted-text { font-size: 1.05rem; color: #64748b; }
.muted-small { font-size: 0.8rem; color: #94a3b8; }

.life-container { display: grid; grid-template-columns: 280px 1fr; gap: 20px; min-height: 70vh; }

.life-side {
  .memory-section { margin-top: 20px; }
}

.life-main {
  background: #fff; border: 1px solid #e2e8f0; border-radius: 16px;
  display: flex; flex-direction: column; overflow: hidden;
}
</style>
