<template>
  <div class="comment-management">
    <!-- 头部筛选 -->
    <div class="table-header">
      <el-form :inline="true" :model="queryParams" class="filter-form">
        <el-form-item label="关键字">
          <el-input v-model="queryParams.keyword" placeholder="搜索昵称或内容" clearable style="width: 200px;"
            @clear="handleSearch" />
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="状态过滤" clearable style="width: 140px;"
            @change="handleSearch">
            <el-option label="待审核" :value="0" />
            <el-option label="已通过" :value="1" />
            <el-option label="未通过" :value="2" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" border style="width: 100%; margin-top: 16px;">
      <el-table-column prop="id" label="ID" width="80" align="center" />
      <el-table-column prop="nickname" label="昵称" width="120" show-overflow-tooltip />
      <el-table-column prop="email" label="邮箱" width="150" show-overflow-tooltip />
      <el-table-column prop="website" label="网址" width="150" show-overflow-tooltip />
      <el-table-column prop="content" label="评论正文" min-width="250" show-overflow-tooltip />

      <el-table-column label="状态" width="100" align="center">
        <template #default="scope">
          <el-tag :type="getStatusType(scope.row.status)">
            {{ getStatusLabel(scope.row.status) }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="createTime" label="时间" width="180" align="center" />

      <el-table-column label="审核操作" width="180" align="center">
        <template #default="scope">
          <el-button v-if="scope.row.status !== 1" size="small" type="success" link :icon="Check"
            @click="handleStatusUpdate(scope.row, 1)">
            通过
          </el-button>
          <el-button v-if="scope.row.status !== 2" size="small" type="warning" link :icon="Close"
            @click="handleStatusUpdate(scope.row, 2)">
            驳回
          </el-button>
        </template>
      </el-table-column>

      <el-table-column label="管理" width="100" align="center" fixed="right">
        <template #default="scope">
          <el-button size="small" type="danger" link :icon="Delete" @click="handleDelete(scope.row)">
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Delete, Check, Close } from '@element-plus/icons-vue'
import { getCommentPage, updateCommentStatus, deleteComment } from '@/api/comment'

const loading = ref(false)
const tableData = ref([])
const total = ref(0)

const queryParams = reactive({
  current: 1,
  size: 10,
  keyword: '',
  status: null
})

const getList = async () => {
  loading.value = true
  try {
    const res = await getCommentPage(queryParams)
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

const getStatusType = (status) => {
  if (status === 1) return 'success'
  if (status === 2) return 'danger'
  return 'info'
}

const getStatusLabel = (status) => {
  if (status === 1) return '已通过'
  if (status === 2) return '未通过'
  return '待审核'
}

// 快速审核修改评论状态
const handleStatusUpdate = async (row, status) => {
  const statusName = status === 1 ? '通过' : '驳回'
  try {
    await updateCommentStatus(row.id, status)
    ElMessage.success(`已${statusName}该评论`)
    getList()
  } catch (error) { }
}

const handleDelete = (row) => {
  ElMessageBox.confirm('确定要永久删除此条评论吗？此操作不可撤销。', '删除确认', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await deleteComment(row.id)
      ElMessage.success('评论删除成功')
      getList()
    } catch (error) { }
  }).catch(() => { })
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.comment-management {
  background-color: #fff;
  padding: 24px;
  border-radius: 8px;

  .table-header {
    background-color: var(--el-fill-color-light);
    padding: 16px;
    border-radius: 4px;
    margin-bottom: 8px;

    .filter-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: -18px;
    }
  }

  .pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>