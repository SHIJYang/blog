<template>
  <div class="category-management">
    <!-- 头部搜索与操作区 -->
    <div class="table-header">
      <div class="search-box">
        <el-input v-model="queryParams.keyword" placeholder="搜索分类名称" clearable style="width: 260px;"
          @clear="handleSearch" @keyup.enter="handleSearch">
          <template #append>
            <el-button :icon="Search" @click="handleSearch" />
          </template>
        </el-input>
      </div>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增分类</el-button>
    </div>

    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" border style="width: 100%; margin-top: 16px;">
      <el-table-column prop="id" label="ID" width="100" align="center" />
      <el-table-column prop="name" label="分类名称" min-width="150" />
      <el-table-column prop="description" label="分类描述" min-width="250" show-overflow-tooltip />
      <el-table-column prop="createTime" label="创建时间" width="180" align="center" />

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
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑分类' : '新增分类'" width="500px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
        <el-form-item label="名称" prop="name">
          <el-input v-model="form.name" placeholder="请输入分类名称" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="描述" prop="description">
          <el-input v-model="form.description" type="textarea" rows="3" placeholder="请输入分类描述" maxlength="200"
            show-word-limit />
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
  getCategoryPage,
  createCategory,
  updateCategory,
  deleteCategory
} from '@/api/category'

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
  name: '',
  description: ''
})

const rules = {
  name: [{ required: true, message: '分类名称不能为空', trigger: 'blur' }]
}

const getList = async () => {
  loading.value = true
  try {
    const res = await getCategoryPage(queryParams)
    // 假设后端分页数据结构存放在 res.data 中，常见包装为 { records: [], total: 0 } 或直接返回 Page 对象
    tableData.value = res?.records || []
    total.value = res?.total || 0
  } catch (error) {
    // 异常已由拦截器处理
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
    form.description = row.description
  } else {
    form.id = null
    form.name = ''
    form.description = ''
  }
  dialogVisible.value = true
}

const resetForm = () => {
  formRef.value?.resetFields()
  form.id = null
  form.name = ''
  form.description = ''
}

const handleSubmit = () => {
  formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    try {
      if (form.id) {
        await updateCategory(form)
        ElMessage.success('修改成功')
      } else {
        await createCategory(form)
        ElMessage.success('创建成功')
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
  ElMessageBox.confirm(`确定要删除分类 "${row.name}" 吗？此操作不可逆。`, '警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteCategory(row.id)
      ElMessage.success('删除成功')
      getList()
    } catch (error) { }
  }).catch(() => { })
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.category-management {
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