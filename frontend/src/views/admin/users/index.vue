<template>
  <div class="user-management">
    <div class="table-header">
      <el-input v-model="queryParams.keyword" placeholder="搜索用户名或昵称" clearable style="width: 260px;"
        @clear="handleSearch" @keyup.enter="handleSearch">
        <template #append>
          <el-button :icon="Search" @click="handleSearch" />
        </template>
      </el-input>
      <el-button type="primary" :icon="Plus" @click="openDialog()">添加用户</el-button>
    </div>

    <!-- 表格区 -->
    <el-table :data="tableData" v-loading="loading" border style="width: 100%; margin-top: 16px;">
      <el-table-column label="头像" width="80" align="center">
        <template #default="scope">
          <el-avatar :src="scope.row.avatar" :size="40">
            {{ scope.row.nickname?.charAt(0) }}
          </el-avatar>
        </template>
      </el-table-column>
      <el-table-column prop="id" label="ID" width="80" align="center" />
      <el-table-column prop="username" label="用户名" width="150" />
      <el-table-column prop="nickname" label="昵称" width="150" />
      <el-table-column prop="email" label="邮箱" min-width="180" show-overflow-tooltip />
      <el-table-column prop="role" label="角色" width="120" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.role === 'ADMIN' ? 'danger' : (scope.row.role === 'AUTHOR' ? 'warning' : 'success')">
            {{ scope.row.role === 'ADMIN' ? '系统管理员' : (scope.row.role === 'AUTHOR' ? '作者' : '读者') }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column label="状态" width="100" align="center">
        <template #default="scope">
          <el-tag :type="scope.row.status === 1 ? 'success' : 'info'">
            {{ scope.row.status === 1 ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>

      <el-table-column prop="createTime" label="创建时间" width="180" align="center" />

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

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑用户资料' : '添加系统用户'" width="550px" @closed="resetForm">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="头像" prop="avatar">
          <div class="avatar-wrapper">
            <el-avatar :src="form.avatar" :size="80" class="preview-avatar">
              {{ form.nickname?.charAt(0) }}
            </el-avatar>
            <div class="avatar-input">
              <el-input v-model="form.avatar" placeholder="请输入头像图片URL地址" clearable />
              <div class="avatar-tip">支持输入网络图片链接（如：https://xxx.jpg）</div>
            </div>
          </div>
        </el-form-item>

        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" :disabled="!!form.id" placeholder="请输入用户名" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password
            :placeholder="form.id ? '不修改请留空' : '请输入密码 (最少 6 位)'" />
        </el-form-item>

        <el-form-item label="用户昵称" prop="nickname">
          <el-input v-model="form.nickname" placeholder="显示的名字" />
        </el-form-item>

        <el-form-item label="邮箱地址" prop="email">
          <el-input v-model="form.email" placeholder="邮箱" />
        </el-form-item>

        <el-form-item label="系统角色" prop="role">
          <el-select v-model="form.role" style="width: 100%;">
            <el-option label="系统管理员 (ADMIN)" value="ADMIN" />
            <el-option label="作者 (AUTHOR)" value="AUTHOR" />
            <el-option label="读者 (READER)" value="READER" />
          </el-select>
        </el-form-item>

        <el-form-item label="使用状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio :value="1">启用</el-radio>
            <el-radio :value="0">锁定禁用</el-radio>
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
import { getUserPage, createUser, updateUser, deleteUser, getUserById } from '@/api/user'

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
  username: '',
  password: '',
  nickname: '',
  email: '',
  avatar: '',      // 头像URL
  role: 'READER',
  status: 1
})

const rules = {
  username: [{ required: true, message: '用户名不能为空', trigger: 'blur' }],
  nickname: [{ required: true, message: '昵称不能为空', trigger: 'blur' }],
  email: [
    { required: true, message: '邮箱地址不能为空', trigger: 'blur' },
    { type: 'email', message: '邮箱格式不规范', trigger: 'blur' }
  ]
}

const getList = async () => {
  loading.value = true
  try {
    const res = await getUserPage(queryParams)
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

const openDialog = async (row = null) => {
  if (row) {
    try {
      const res = await getUserById(row.id)
      const detail = res || {}
      form.id = detail.id
      form.username = detail.username
      form.password = ''
      form.nickname = detail.nickname
      form.email = detail.email
      form.avatar = detail.avatar || ''  // 赋值头像URL
      form.role = detail.role
      form.status = detail.status
    } catch (e) { }
  } else {
    form.id = null
    form.username = ''
    form.password = ''
    form.nickname = ''
    form.email = ''
    form.avatar = ''   // 清空头像
    form.role = 'READER'
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

    // 如果是新建，必须填密码
    if (!form.id && !form.password) {
      ElMessage.warning('新用户必须设置初始密码')
      return
    }

    submitLoading.value = true
    try {
      if (form.id) {
        await updateUser(form)
        ElMessage.success('用户资料更新成功')
      } else {
        await createUser(form)
        ElMessage.success('用户创建成功')
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
  ElMessageBox.confirm(`确定要注销并删除该账户 "${row.username}" 吗？`, '删除警告', {
    confirmButtonText: '确定删除',
    cancelButtonText: '取消',
    type: 'danger'
  }).then(async () => {
    try {
      await deleteUser(row.id)
      ElMessage.success('用户已被永久移除')
      getList()
    } catch (error) { }
  }).catch(() => { })
}

onMounted(() => {
  getList()
})
</script>

<style lang="scss" scoped>
.user-management {
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

  // 头像相关样式
  .avatar-wrapper {
    display: flex;
    gap: 16px;
    align-items: flex-start;

    .preview-avatar {
      flex-shrink: 0;
      border: 1px solid #e4e7ed;
    }

    .avatar-input {
      flex: 1;

      .avatar-tip {
        font-size: 12px;
        color: #909399;
        margin-top: 8px;
        line-height: 1.4;
      }
    }
  }
}
</style>