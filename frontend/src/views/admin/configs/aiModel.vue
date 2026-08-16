<template>
  <div class="ai-provider-page">
    <el-card shadow="never">
      <template #header>
        <div class="card-header">
          <span class="title">🤖 AI 对话供应商</span>
          <div class="header-actions">
            <el-select v-model="templateId" placeholder="选择供应商模板" style="width: 180px" class="template-select">
              <el-option
                v-for="t in templates" :key="t.key"
                :value="t.key"
                :label="`模板 · ${t.name}`"
              />
            </el-select>
            <el-button :icon="Plus" @click="addFromTemplate">添加</el-button>
          </div>
        </div>
      </template>

      <!-- 供应商列表 -->
      <el-table :data="providers" v-loading="loading" border style="width: 100%">
        <el-table-column label="激活" width="70" align="center">
          <template #default="scope">
            <el-radio
              :model-value="scope.row.isActive === 1"
              :value="true"
              @change="handleActivate(scope.row)"
            />
          </template>
        </el-table-column>

        <el-table-column label="启用" width="70" align="center">
          <template #default="scope">
            <el-switch
              :model-value="scope.row.enabled === 1"
              @change="val => toggleEnabled(scope.row, val)"
            />
          </template>
        </el-table-column>

        <el-table-column prop="name" label="名称" min-width="110" />
        <el-table-column prop="baseUrl" label="接口地址" min-width="260" show-overflow-tooltip />
        <el-table-column prop="model" label="模型" min-width="140" />
        <el-table-column prop="apiKey" label="API Key" min-width="150">
          <template #default="scope">
            <span v-if="scope.row.apiKey">••••{{ scope.row.apiKey.slice(-4) }}</span>
            <el-tag v-else type="warning" size="small">未配置</el-tag>
          </template>
        </el-table-column>

        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="scope">
            <el-button size="small" :icon="Edit" @click="openEdit(scope.row)">编辑</el-button>
            <el-button
              size="small"
              type="danger"
              :icon="Delete"
              :disabled="scope.row.isActive === 1"
              @click="handleDelete(scope.row)"
            >删除</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>

    <!-- 编辑/新增弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑供应商' : '添加供应商'" width="560px">
      <el-form :model="form" label-width="110px" ref="formRef" :rules="rules">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="供应商名称，如 Agnes" />
        </el-form-item>
        <el-form-item label="接口地址" prop="baseUrl">
          <el-input v-model="form.baseUrl" placeholder="https://api.xxx.com/v1/chat/completions" />
        </el-form-item>
        <el-form-item label="模型" prop="model">
          <el-input v-model="form.model" placeholder="如 agnes-2.5-flash / deepseek-v4-flash" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input
            v-model="form.apiKey"
            type="password"
            show-password
            :placeholder="isEdit ? '留空保留原值' : '填入 API Key'"
          />
          <div class="hint">{{ isEdit ? '留空或输入含 * 的内容将保留原 Key 不变' : '对话使用该供应商的密钥' }}</div>
        </el-form-item>
        <el-form-item label="启用">
          <el-switch v-model="form.enabled" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="handleSave">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete } from '@element-plus/icons-vue'
import {
  getAiProviders, createAiProvider, updateAiProvider, deleteAiProvider, activateAiProvider
} from '@/api/aiProvider'

const loading = ref(false)
const saving = ref(false)
const providers = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const templateId = ref('agnes')

const templates = [
  { key: 'agnes', name: 'Agnes', url: 'https://api.agnes-ai.cn/v1/chat/completions', model: 'agnes-2.5-flash' },
  { key: 'deepseek', name: 'DeepSeek', url: 'https://api.deepseek.com/v1/chat/completions', model: 'deepseek-v4-flash' },
  { key: 'mimo', name: 'MiMo', url: '', model: '' }
]

const form = reactive({ id: null, name: '', baseUrl: '', model: '', apiKey: '', enabled: 1 })

const rules = {
  name: [{ required: true, message: '请填写名称', trigger: 'blur' }],
  baseUrl: [{ required: true, message: '请填写接口地址', trigger: 'blur' }],
  model: [{ required: true, message: '请填写模型', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const res = await getAiProviders()
    // 拦截器已返回 res.data（即数组本身），做兼容
    providers.value = Array.isArray(res) ? res : (res?.data || [])
  } catch { } finally { loading.value = false }
}

function addFromTemplate() {
  const t = templates.find(x => x.key === templateId.value)
  isEdit.value = false
  Object.assign(form, { id: null, name: t.name, baseUrl: t.url, model: t.model, apiKey: '', enabled: 1 })
  dialogVisible.value = true
}

function openEdit(row) {
  isEdit.value = true
  Object.assign(form, { id: row.id, name: row.name, baseUrl: row.baseUrl, model: row.model, apiKey: '', enabled: row.enabled })
  dialogVisible.value = true
}

async function handleSave() {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    saving.value = true
    try {
      if (isEdit.value) {
        await updateAiProvider(form.id, { ...form })
        ElMessage.success('已保存')
      } else {
        await createAiProvider({ ...form })
        ElMessage.success('已添加')
      }
      dialogVisible.value = false
      load()
    } catch (e) {
      ElMessage.error(e?.message || '保存失败')
    } finally {
      saving.value = false
    }
  })
}

async function handleActivate(row) {
  try {
    await activateAiProvider(row.id)
    ElMessage.success(`已激活 ${row.name}`)
    load()
  } catch (e) {
    ElMessage.error(e?.message || '激活失败')
  }
}

async function toggleEnabled(row, val) {
  try {
    await updateAiProvider(row.id, { ...row, enabled: val ? 1 : 0, apiKey: '' })
    ElMessage.success(val ? '已启用' : '已禁用')
    load()
  } catch (e) {
    ElMessage.error(e?.message || '操作失败')
  }
}

function handleDelete(row) {
  ElMessageBox.confirm(`确定删除供应商「${row.name}」？`, '警告', {
    confirmButtonText: '删除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteAiProvider(row.id)
      ElMessage.success('已删除')
      load()
    } catch (e) {
      ElMessage.error(e?.message || '删除失败')
    }
  }).catch(() => {})
}

onMounted(load)
</script>

<style scoped lang="scss">
.ai-provider-page {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
    .title { font-size: 1.1rem; font-weight: 600; }
    .header-actions { display: flex; gap: 8px; align-items: center; }
  }
  .hint {
    font-size: 0.75rem;
    color: #94a3b8;
    margin-top: 4px;
  }
}
</style>
