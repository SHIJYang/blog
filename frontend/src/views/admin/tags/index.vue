<template>
  <div class="tag-management">
    <!-- 头部搜索与操作区 -->
    <div class="table-header">
      <div class="search-box">
        <el-input v-model="queryParams.keyword" placeholder="搜索标签名称" clearable style="width: 260px;"
          @clear="handleSearch" @keyup.enter="handleSearch">
          <template #append>
            <el-button :icon="Search" @click="handleSearch" />
          </template>
        </el-input>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增标签</el-button>
    </div>

    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" border style="width: 100%; margin-top: 16px;">
      <el-table-column prop="id" label="ID" width="100" align="center" />
      <el-table-column prop="name" label="标签名称" min-width="150" />
      <el-table-column prop="createTime" label="创建时间" width="220" align="center" />

      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="scope">
          <el-button size="small" :icon="Edit" @click="openDialog(scope.row)">
            编辑
          </el-button>
          <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(scope.row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页器 -->
    <div class="pagination-container">
      <el-pagination v-model:current-page="queryParams.current" v-model:page-size="queryParams.size"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" :total="total"
        @size-change="handleSizeChange" @current-change="handleCurrentChange" />
    </div>

    <!-- 新增 / 编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑标签' : '新增标签'" width="400px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="标签名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入标签名称" maxlength="30" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">
            确定
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search } from '@element-plus/icons-vue'
import {
  getTagPage,
  createTag,
  updateTag,
  deleteTag
} from '@/api/tag'

const loading = ref(false)
const submitLoading = ref(false)
const dialogVisible = ref(false)
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
  name: ''
})

const rules = {
  name: [
    { required: true, message: '标签名称不能为空', trigger: 'blur' },
    { validator: (rule, value, callback) => {
      if (!value || !value.trim()) {
        callback(new Error('标签名称不能为纯空格'))
      } else {
        callback()
      }
    }, trigger: 'blur' }
  ]
}

const getList = async () => {
  loading.value = true
  try {
    const res = await getTagPage(queryParams)
    tableData.value = res?.records || []
    total.value = res?.total || 0
  } catch (error) {
    ElMessage.error(error?.message || '获取标签列表失败')
  } finally {
    loading.value = false
  }
}

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

const openDialog = (row = null) => {
  if (row) {
    form.id = row.id
    form.name = row.name
  } else {
    form.id = null
    form.name = ''
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
  form.id = null
  form.name = ''
}

const handleSubmit = () => {
  formRef.value?.validate(async (valid) => {
    if (!valid) {
      submitLoading.value = false
      return
    }
    submitLoading.value = true
    try {
      if (form.id) {
        await updateTag({ id: form.id, name: form.name })
        ElMessage.success('修改成功')
      } else {
        await createTag({ name: form.name })
        ElMessage.success('创建成功')
      }
      dialogVisible.value = false
      getList()
    } catch (error) {
      ElMessage.error(error?.message || '操作失败，请稍后重试')
    } finally {
      submitLoading.value = false
    }
  })
}

const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要删除标签 "${row.name}" 吗？此操作不可逆。`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteTag(row.id)
      ElMessage.success('删除成功')
      getList()
    } catch (error) {
      ElMessage.error(error?.message || '删除失败，请稍后重试')
    }
  }).catch(() => { })
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.tag-management {
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