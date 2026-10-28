<template>
  <div class="article-management">
    <!-- 头部筛选与操作区 -->
    <div class="filter-header">
      <el-form :inline="true" :model="queryParams" class="filter-form">
        <el-form-item label="关键字">
          <el-input v-model="queryParams.keyword" placeholder="搜索文章标题" clearable style="width: 200px;"
            @clear="handleSearch" />
        </el-form-item>

        <el-form-item label="分类">
          <el-select v-model="queryParams.categoryId" placeholder="筛选分类" clearable style="width: 150px;"
            @change="handleSearch">
            <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="标签">
          <el-select v-model="queryParams.tagId" placeholder="筛选标签" clearable style="width: 150px;"
            @change="handleSearch">
            <el-option v-for="item in tagOptions" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="状态">
          <el-select v-model="queryParams.status" placeholder="筛选状态" clearable style="width: 120px;"
            @change="handleSearch">
            <el-option label="草稿" :value="0" />
            <el-option label="已发布" :value="1" />
          </el-select>
        </el-form-item>

        <el-form-item>
          <el-button type="primary" :icon="Search" @click="handleSearch">搜索</el-button>
          <!-- 1. 修改为直接跳转到撰写文章页面 -->
          <el-button :icon="Plus" type="success" @click="goWritePage()">撰写文章</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <el-table :data="tableData" v-loading="loading" border style="width: 100%; margin-top: 16px;">
      <el-table-column prop="id" label="ID" width="80" align="center" />

      <el-table-column prop="title" label="标题" min-width="200" show-overflow-tooltip />

      <el-table-column label="封面" width="120" align="center">
        <template #default="scope">
          <el-image v-if="scope.row.coverImage" style="width: 80px; height: 45px; border-radius: 4px;"
            :src="scope.row.coverImage" fit="cover" />
          <span v-else class="text-secondary">无</span>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="100" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
            {{ scope.row.status === 1 ? '已发布' : '草稿' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="投影来源" width="130" align="center">
        <template #default="scope">
          <el-tooltip v-if="scope.row.needsReview === 1"
            content="记忆已修改，但这篇文章被人工编辑过，未自动覆盖，待人工合并"
            placement="top">
            <el-tag type="danger" size="small">待合并</el-tag>
          </el-tooltip>
          <el-tooltip v-else-if="scope.row.sourceMemoryId"
            content="由拾光记忆投影而来，改记忆会同步本文章"
            placement="top">
            <el-tag type="info" size="small">记忆投影</el-tag>
          </el-tooltip>
          <span v-else class="text-secondary">-</span>
        </template>
      </el-table-column>

      <el-table-column label="评论" width="100" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.allowComment === 1 ? 'success' : 'danger'">
            {{ scope.row.allowComment === 1 ? '允许' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="createTime" label="发布时间" width="180" align="center" />

      <el-table-column label="操作" width="180" align="center" fixed="right">
        <template #default="scope">
          <!-- 2. 修改为携带 ID 跳转到编辑文章页面 -->
          <el-button size="small" :icon="Edit" @click="goWritePage(scope.row.id)">
            编辑
          </el-button>
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
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Edit, Delete, Search } from '@element-plus/icons-vue'
// 导入 Pinia Store
import { useArticleStore } from '@/stores/article'
import { storeToRefs } from 'pinia'

const articleStore = useArticleStore()
const { publicCategories: categoryOptions, publicTags: tagOptions } = storeToRefs(articleStore)

const router = useRouter()

// 将 store 中的 adminArticles 别名定义为 tableData
const { adminArticles: tableData, adminPagination } = storeToRefs(articleStore)

// 使用计算属性获取文章总数
const total = computed(() => adminPagination.value.total)

const loading = ref(false)

// 列表查询条件
const queryParams = reactive({
  current: 1,
  size: 10,
  keyword: '',
  categoryId: null,
  tagId: null,
  status: null
})

// 分类/标签由 store.initPublicMeta() 全局缓存，此处无需再次请求

// 获取列表数据
const getList = async () => {
  loading.value = true
  try {
    await articleStore.fetchAdminArticles(queryParams)
  } catch (error) {
    console.error('获取文章列表失败:', error)
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

// 4. 重构跳转逻辑：代替原 openDrawer 方法
const goWritePage = (id = null) => {
  if (id) {
    // 编辑：携带文章 ID 跳转
    router.push({ name: 'WriteArticle', query: { id } })
  } else {
    // 新增：直接跳转
    router.push({ name: 'WriteArticle' })
  }
}

// 删除文章
const handleDelete = (row) => {
  ElMessageBox.confirm(`确定要彻底删除文章 "${row.title}" 吗？此操作无法恢复。`, '删除警告', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(async () => {
    try {
      await articleStore.removeArticle(row.id)
      ElMessage.success('文章已删除')
      getList()
    } catch (error) {
      console.error('删除文章失败:', error)
    }
  }).catch(() => { })
}

onMounted(async () => {
  // 分类/标签已在 App.vue 全局初始化
  getList()
})
</script>

<style lang="scss" scoped>
.article-management {
  background-color: #fff;
  padding: 24px;
  border-radius: 8px;

  .filter-header {
    background-color: var(--el-fill-color-light);
    padding: 16px;
    border-radius: 4px;
    margin-bottom: 8px;

    .filter-form {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      margin-bottom: -18px; // 抵消 form-item 底部的默认 margin-bottom
    }
  }

  .text-secondary {
    color: var(--el-text-color-secondary);
    font-size: 0.875rem;
  }

  .pagination-container {
    margin-top: 20px;
    display: flex;
    justify-content: flex-end;
  }
}
</style>