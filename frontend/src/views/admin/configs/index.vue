<template>
  <div class="config-management">
    <!-- 操作头 -->
    <div class="table-header">
      <el-input v-model="queryParams.keyword" placeholder="搜索配置键或配置名称" clearable style="width: 260px;"
        @clear="handleSearch" @keyup.enter="handleSearch">
        <template #append>
          <el-button :icon="Search" @click="handleSearch" />
        </template>
      </el-input>
      <el-button type="primary" :icon="Plus" @click="openDialog()">增加新配置项</el-button>
    </div>

    <!-- 表格展示 -->
    <el-table :data="tableData" v-loading="loading" border style="width: 100%; margin-top: 16px;">
      <el-table-column prop="configName" label="配置名称" min-width="150" />
      <el-table-column prop="configKey" label="配置Key" min-width="150" />
      <el-table-column prop="configValue" label="配置Value" min-width="200" show-overflow-tooltip />

      <!-- 新增：是否公开列 -->
      <el-table-column prop="isPublic" label="是否公开" width="100" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.isPublic === 1 ? 'success' : 'info'">
            {{ scope.row.isPublic === 1 ? '公开' : '私有' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="scope">
          <el-button size="small" :icon="Edit" @click="openDialog(scope.row)">
            修改值
          </el-button>
          <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(scope.row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-container" v-if="total > queryParams.size">
      <el-pagination v-model:current-page="queryParams.current" v-model:page-size="queryParams.size"
        layout="total, prev, pager, next" :total="total" @current-change="handleCurrentChange" />
    </div>

    <!-- 弹窗 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '修改配置值' : '添加系统配置项'" width="500px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="配置名称" prop="configName">
          <el-input v-model="form.configName" placeholder="例如：博客主标题" />
        </el-form-item>

        <el-form-item label="配置 Key" prop="configKey">
          <el-input v-model="form.configKey" :disabled="isEdit" placeholder="例如：sys.blog.title" />
        </el-form-item>

        <el-form-item label="配置 Value" prop="configValue">
          <el-input v-model="form.configValue" type="textarea" rows="3" placeholder="请输入配置的真实参数内容" />
        </el-form-item>

        <!-- 新增：是否公开选项 -->
        <el-form-item label="是否公开" prop="isPublic">
          <el-radio-group v-model="form.isPublic">
            <el-radio :value="1">公开</el-radio>
            <el-radio :value="0">私有</el-radio>
          </el-radio-group>

        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search } from '@element-plus/icons-vue'
import { getConfigPage, createConfig, updateConfig, deleteConfig } from '@/api/config'

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
const isEdit = ref(true)
const tableData = ref([])
const total = ref(0)
const formRef = ref(null)

const queryParams = reactive({
  current: 1,
  size: 10,
  keyword: ''
})

const form = reactive({
  id: null,
  configName: '',
  configKey: '',
  configValue: '',
  isPublic: 1  // 默认改为公开（1-公开，0-私有）
})

const rules = {
  configName: [{ required: true, message: '配置描述名称不能为空', trigger: 'blur' }],
  configKey: [{ required: true, message: '配置Key标识键必填', trigger: 'blur' }],
  configValue: [{ required: true, message: '具体配置参数值必填', trigger: 'blur' }],
  isPublic: [{ required: true, message: '请选择是否公开', trigger: 'change' }]
}

const getList = async () => {
  loading.value = true
  try {
    const res = await getConfigPage(queryParams)
    tableData.value = res?.records || []
    total.value = res?.total || 0
  } catch (error) {
  } finally {
    loading.value = false
  }
}

const handleSearch = () => {
  queryParams.current = 1
  getList()
}

const handleCurrentChange = (val) => {
  queryParams.current = val
  getList()
}

const openDialog = (row = null) => {
  if (row) {
    isEdit.value = true
    form.id = row.id
    form.configName = row.configName
    form.configKey = row.configKey
    form.configValue = row.configValue
    form.isPublic = row.isPublic  // 赋值是否公开
  } else {
    isEdit.value = false
    form.id = null
    form.configName = ''
    form.configKey = ''
    form.configValue = ''
    form.isPublic = 1  // 默认公开
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
}

const handleSubmit = () => {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      // ConfigSaveDto 不包含 id 字段：新增/更新均按 configKey 识别，提交时剔除 id
      const { id, ...payload } = form
      if (isEdit.value) {
        await updateConfig(payload)
        ElMessage.success('配置更新成功')
      } else {
        await createConfig(payload)
        ElMessage.success('新配置注入成功')
      }
      dialogVisible.value = false
      getList()
    } catch (error) {
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`警告：删除系统配置 "${row.configName}" 会导致前后台读取异常，确定删除吗？`, '警告', {
    confirmButtonText: '确定移除',
    cancelButtonText: '点错了',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteConfig(row.id)
      ElMessage.success('配置已被删除')
      getList()
    } catch (error) { }
  }).catch(() => { })
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.config-management {
  background-color: #fff;
  padding: 24px;
  border-radius: 8px;

  .table-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }


}
</style>