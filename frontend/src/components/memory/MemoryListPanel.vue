<!--
  记忆功能组件 2：MemoryListPanel
  - 「小念记得」记忆列表 + 关键词搜索
  - 每条记忆展示：封面缩略图 + 标题 + 状态 + 首情绪标签
  - 每个记忆的操作：编辑 / 凝聚 / 发布 / 隐私 / 废弃
  - 点击某条 → emit('select', item)；增删改后 → emit('refresh')
  依赖父层提供：当前选中 id (activeId)，数据已由父层拉取后经 refresh 触发本组件重新加载
-->
<template>
  <div class="memory-list-panel">
    <p class="side-title">小念记得</p>

    <!-- 搜索记忆 -->
    <el-input v-model="keyword" placeholder="搜索记忆…" size="small" clearable style="margin-bottom: 8px"
      @keyup.enter="doSearch" @clear="doSearch" />

    <!-- 记忆列表 -->
    <div v-if="memories.length" class="memory-list">
      <div v-for="m in memories" :key="m.id" class="memory-item" :class="{ active: m.id === activeId }"
        @click="emit('select', m)">
        <el-image v-if="m.coverThumb || m.coverPhoto" :src="m.coverThumb || m.coverPhoto" fit="cover" class="memory-thumb" />
        <div class="memory-meta">
          <span class="memory-title">{{ m.autoTitle || '未命名' }}</span>
          <span class="memory-sub">
            {{ statusLabel(m.status) }}
            <el-tag v-if="m.emotions" size="small" type="warning" style="margin-left:4px">{{ m.emotions.split(',')[0] }}</el-tag>
            <el-tag v-if="m.publishedArticleId" size="small" type="success" style="margin-left:4px">已发布</el-tag>
          </span>
          <span class="memory-actions" @click.stop>
            <el-button size="small" link type="primary" @click="emit('edit', m)">编辑</el-button>
            <el-button v-if="m.status === 2 || m.status === 3" size="small" link type="warning" @click="emit('condense', m)">凝聚</el-button>
            <el-button v-if="m.status === 3 && !m.publishedArticleId" size="small" link type="success" @click="emit('publish', m)">发布</el-button>
            <el-button size="small" link @click="emit('privacy', m)">隐私</el-button>
            <el-button size="small" link type="danger" @click="emit('abandon', m)">废弃</el-button>
          </span>
        </div>
        <!-- 记忆闪光点（点击可展开/收起，可手动添加/删除） -->
        <div class="memory-insights">
          <template v-if="m._expanded">
            <div v-if="m._insights && m._insights.length" class="insight-list">
              <div v-for="ins in m._insights" :key="ins.id" class="insight-item" @click.stop>
                <el-tag v-if="ins.emotion" size="small" type="warning" style="margin-right:4px">{{ ins.emotion }}</el-tag>
                <span class="insight-text">{{ ins.content }}</span>
                <el-button size="small" link type="danger" class="insight-del" @click="removeInsight(m, ins)">删</el-button>
              </div>
            </div>
            <div v-else class="insight-empty">（还没有闪光点，AI 凝聚时自动提炼，也可以自己添加）</div>
            <div class="insight-add">
              <el-input v-model="m._newInsight" size="small" placeholder="记一个闪光点…" maxlength="200"
                @keyup.enter="addInsight(m)" />
              <el-button size="small" type="primary" :loading="m._adding" @click="addInsight(m)">添加</el-button>
            </div>
          </template>
          <el-button v-if="m.status === 3" size="small" link @click.stop="toggleInsights(m)">
            {{ m._expanded ? '收起闪光点' : '闪光点' }}
          </el-button>
        </div>
      </div>
    </div>

    <!-- 无记忆引导 -->
    <div v-else-if="!memories.length" class="memory-empty">
      <p class="empty-title">还没有被小念记住的事</p>
      <p class="empty-tip">用下面的方式，珍藏第一段日常：</p>
      <div class="empty-guide">
        <div class="guide-item"><span class="guide-num">1</span>上传一张照片，小念会温柔开口</div>
        <div class="guide-item"><span class="guide-num">2</span>和拾光聊几句，静待它替你收进心里</div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { getMemoryList, searchMemory, getMemoryPublishStatus, getMemoryInsights, addMemoryInsight, deleteMemoryInsight } from '@/api/memory'

// 类型
export type MemItem = {
  id: number
  coverPhoto?: string
  coverThumb?: string
  autoTitle?: string
  status?: number
  emotions?: string
  privacy?: number
  publishedArticleId?: number | null
}

const props = withDefaults(defineProps<{
  /** 当前选中的记忆 id（高亮用） */
  activeId?: number | null
  /** 外部刷新信号（值变化即重新加载列表） */
  refreshTick?: number
}>(), { activeId: null, refreshTick: 0 })

const emit = defineEmits<{
  (e: 'select', item: MemItem): void
  (e: 'edit', item: MemItem): void
  (e: 'condense', item: MemItem): void
  (e: 'publish', item: MemItem): void
  (e: 'privacy', item: MemItem): void
  (e: 'abandon', item: MemItem): void
}>()

const memories = ref<MemItem[]>([])
const keyword = ref('')

// 加载列表（含发布状态补齐）
async function loadList() {
  try {
    const res = await getMemoryList({ current: 1, size: 50, status: null, order: 'create_desc' })
    memories.value = res?.records || []
    // MemoryListVo 不含 publishedArticleId，仅为已成念(3)补齐
    for (const m of memories.value) {
      if (m.status === 3) {
        try { m.publishedArticleId = await getMemoryPublishStatus(m.id) } catch { m.publishedArticleId = null }
      } else {
        m.publishedArticleId = null
      }
    }
  } catch (e) { /* 静默 */ }
}

// 搜索
async function doSearch() {
  const kw = keyword.value.trim()
  try {
    if (kw) {
      const res = await searchMemory({ keyword: kw, current: 1, size: 50 })
      memories.value = res?.records || []
      for (const m of memories.value) {
        if (m.status === 3) {
          try { m.publishedArticleId = await getMemoryPublishStatus(m.id) } catch { m.publishedArticleId = null }
        } else m.publishedArticleId = null
      }
    } else {
      loadList()
    }
  } catch (e) { ElMessage.error('搜索失败') }
}

// 展开/收起某条记忆的记忆闪光点
async function toggleInsights(m: any) {
  m._expanded = !m._expanded
  if (m._expanded && !m._insights) {
    m._insights = await loadInsights(m.id)
  }
}

// 拉取某条记忆的闪光点
async function loadInsights(id: number) {
  try {
    const res = await getMemoryInsights(id)
    return Array.isArray(res) ? res : []
  } catch (e) {
    return []
  }
}

// 用户手动添加闪光点
async function addInsight(m: any) {
  const text = (m._newInsight || '').trim()
  if (!text) { ElMessage.warning('先写点什么') ; return }
  if (m._adding) return
  m._adding = true
  try {
    await addMemoryInsight(m.id, text)
    m._newInsight = ''
    m._insights = await loadInsights(m.id)
    ElMessage.success('闪光点已记下')
    emit('refresh')
  } catch (e: any) {
    ElMessage.error(e?.message || '添加失败')
  } finally { m._adding = false }
}

// 删除闪光点
async function removeInsight(m: any, ins: any) {
  try {
    await deleteMemoryInsight(ins.id)
    m._insights = (m._insights || []).filter((x: any) => x.id !== ins.id)
    ElMessage.success('已删除')
  } catch (e: any) {
    ElMessage.error(e?.message || '删除失败')
  }
}

// 父层触发刷新
watch(() => props.refreshTick, () => loadList(), { immediate: true })

// 状态文案
function statusLabel(s?: number) {
  if (s === 1) return '未开始'
  if (s === 2) return '对话中'
  if (s === 3) return '已成念'
  if (s === 4) return '已废弃'
  return ''
}
</script>

<style scoped lang="scss">
.memory-list-panel {
  .side-title {
    font-size: 0.95rem;
    color: #1e293b;
    margin: 0 0 8px;
    font-weight: 700;
  }

  .memory-list {
    .memory-item {
      display: flex;
      gap: 10px;
      align-items: center;
      flex-wrap: wrap;
      padding: 8px;
      border-radius: 8px;
      cursor: pointer;
      transition: background 0.2s;

      &:hover { background: #f1f5f9; }
      &.active { background: #e0e7ff; }

      .memory-thumb {
        width: 40px;
        height: 40px;
        border-radius: 6px;
        flex-shrink: 0;
      }

      .memory-insights {
        width: 100%;
        margin-top: 4px;
        .insight-list {
          .insight-item {
            display: flex;
            align-items: flex-start;
            gap: 4px;
            padding: 4px 6px;
            font-size: 0.8rem;
            color: #475569;
            background: #f8fafc;
            border-radius: 6px;
            margin-bottom: 4px;

            .insight-text {
              flex: 1;
              min-width: 0;
            }
            .insight-del {
              flex-shrink: 0;
              padding: 0 2px;
            }
          }
        }
        .insight-empty {
          font-size: 0.75rem;
          color: #94a3b8;
          padding: 4px 6px;
        }
        .insight-add {
          display: flex;
          gap: 6px;
          margin-top: 6px;
        }
      }

      .memory-meta {
        display: flex;
        flex-direction: column;
        min-width: 0;
        flex: 1;

        .memory-title {
          font-size: 0.85rem;
          color: #1e293b;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .memory-sub { font-size: 0.72rem; color: #94a3b8; margin-top: 2px; }
        .memory-actions { margin-top: 4px; }
      }
      &:hover .memory-actions { /* 动作按钮常显 */ }
    }
  }

  .memory-empty {
    border: 1px dashed #e2e8f0;
    border-radius: 12px;
    padding: 24px 16px;
    text-align: center;
    background: #f8fafc;
    .empty-title { font-size: 0.95rem; color: #334155; font-weight: 600; margin: 0 0 8px; }
    .empty-tip { font-size: 0.82rem; color: #94a3b8; margin: 0 0 14px; }
    .empty-guide {
      display: flex; flex-direction: column; gap: 10px; align-items: flex-start; padding-left: 30px;
      .guide-item {
        display: flex; align-items: center; gap: 8px; font-size: 0.82rem; color: #475569;
        .guide-num {
          width: 20px; height: 20px; border-radius: 50%; background: #2563eb; color: #fff;
          font-size: 0.72rem; display: inline-flex; align-items: center; justify-content: center; flex-shrink: 0;
        }
      }
    }
  }
}
</style>
