<template>
  <div class="link-management">
    <!-- 头部搜索与操作 -->
    <div class="table-header">
      <el-input v-model="queryParams.keyword" placeholder="搜索友链名称" clearable style="width: 260px;" @clear="handleSearch"
        @keyup.enter="handleSearch">
        <template #append>
          <el-button :icon="Search" @click="handleSearch" />
        </template>
      </el-input>
      <el-button type="primary" :icon="Plus" @click="openDialog()">新增友链</el-button>
    </div>

    <!-- 表格展示 -->
    <el-table :data="tableData" v-loading="loading" border style="width: 100%; margin-top: 16px;">
      <el-table-column prop="id" label="ID" width="80" align="center" />
      <el-table-column prop="name" label="网站名称" width="150" />

      <el-table-column label="Logo" width="100" align="center">
        <template #default="scope">
          <el-image v-if="scope.row.logo" style="width: 40px; height: 40px; border-radius: 50%;" :src="scope.row.logo"
            fit="cover" />
          <span v-else>-</span>
        </template>
      </el-table-column>

      <el-table-column prop="url" label="链接网址" min-width="180" show-overflow-tooltip />
      <el-table-column prop="description" label="网站简介" min-width="200" show-overflow-tooltip />
      <el-table-column prop="sort" label="排序" width="80" align="center" />

      <el-table-column label="状态" width="100" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
            {{ scope.row.status === 1 ? '显示' : '隐藏' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="scope">
          <el-button size="small" :icon="Edit" @click="openDialog(scope.row)">编辑</el-button>
          <el-button size="small" type="danger" :icon="Delete" @click="handleDelete(scope.row)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-container">
      <el-pagination v-model:current-page="queryParams.current" v-model:page-size="queryParams.size"
        :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper" :total="total"
        @size-change="handleSizeChange" @current-change="handleCurrentChange" />
    </div>

    <!-- 弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑友情链接' : '新增友情链接'" width="500px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="网站名称" prop="name">
          <el-input v-model="form.name" placeholder="例如：GitHub" />
        </el-form-item>

        <el-form-item label="链接网址" prop="url">
          <el-input v-model="form.url" placeholder="例如：https://github.com" />
        </el-form-item>

        <el-form-item label="Logo 图标" prop="logo">
          <el-input v-model="form.logo" placeholder="请输入 Logo 图片 URL" />
        </el-form-item>

        <el-form-item label="简介描述" prop="description">
          <el-input v-model="form.description" type="textarea" rows="2" placeholder="请输入简介" />
        </el-form-item>

        <el-form-item label="排列序号" prop="sort">
          <el-input-number v-model="form.sort" :min="1" />
        </el-form-item>

        <el-form-item label="发布状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">显示</el-radio>
            <el-radio :value="0">隐藏</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitLoading" @click="handleSubmit">确认</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search } from '@element-plus/icons-vue'
import { getLinkPage, createLink, updateLink, deleteLink } from '@/api/link'

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
  url: '',
  logo: '',
  description: '',
  sort: 1,
  status: 1
})

const rules = {
  name: [{ required: true, message: '链接名称不能为空', trigger: 'blur' }],
  url: [
    { required: true, message: '链接地址不能为空', trigger: 'blur' },
    { type: 'url', message: '请输入正确的URL格式', trigger: 'blur' }
  ],
  sort: [{ required: true, message: '排列顺序必填', trigger: 'blur' }],
  status: [{ required: true, message: '状态必选', trigger: 'change' }]
}

const getList = async () => {
  loading.value = true
  try {
    const res = await getLinkPage(queryParams)
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
    form.url = row.url
    form.logo = row.logo || ''
    form.description = row.description || ''
    form.sort = row.sort
    form.status = row.status
  } else {
    form.id = null
    form.name = ''
    form.url = ''
    form.logo = ''
    form.description = ''
    form.sort = 10
    form.status = 1
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
      if (form.id) {
        await updateLink(form)
        ElMessage.success('友链更新成功')
      } else {
        await createLink(form)
        ElMessage.success('友链添加成功')
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
  ElMessageBox.confirm(`确定要移除友链 "${row.name}" 吗？`, '移除确认', {
    confirmButtonText: '移除',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteLink(row.id)
      ElMessage.success('友链已成功下线')
      getList()
    } catch (error) { }
  }).catch(() => { })
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.link-management {
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