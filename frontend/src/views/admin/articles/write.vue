<template>
  <div class="write-page-container" v-loading="pageLoading">
    <!-- 顶部状态与操作栏 -->
    <header class="write-header">
      <div class="header-left">
        <el-button link :icon="ArrowLeft" @click="goBack">返回列表</el-button>
        <el-divider direction="vertical" />
        <span class="editor-status-text">
          {{ form.id ? '编辑模式' : '新创作' }}
        </span>
      </div>
      <div class="header-right">
        <el-button :loading="submitLoading" @click="handleSave(0)">保存草稿</el-button>
        <el-button type="primary" :loading="submitLoading" @click="handleSave(1)">发布文章</el-button>
      </div>
    </header>

    <!-- 沉浸式编辑主体区 -->
    <div class="write-body">
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" class="write-form">
        <el-row :gutter="24" style="height: 100%;">
          <!-- 左侧：文章核心内容 -->
          <el-col :span="17" :xs="24" :sm="17" class="editor-main-col">
            <!-- 沉浸式无边框标题输入 -->
            <el-form-item prop="title" class="title-form-item">
              <input v-model="form.title" placeholder="请输入文章标题..." class="immersive-title-input" maxlength="100" />
            </el-form-item>

            <!-- AiEditor 富文本编辑器 -->
            <div class="editor-wrapper">
              <AiEditor ref="aiEditorRef" v-model="form.content" placeholder="开始你的创作..." />
            </div>
          </el-col>

          <!-- 右侧：文章属性设置面板 -->
          <el-col :span="7" :xs="24" :sm="7" class="editor-sidebar-col">
            <div class="sidebar-inner-card">
              <h3 class="panel-title">文章配置</h3>

              <el-form-item label="文章摘要" prop="summary">
                <el-input v-model="form.summary" type="textarea" :rows="4" placeholder="不填写将自动从正文截取..." maxlength="200"
                  show-word-limit />
              </el-form-item>

              <el-form-item label="封面图片 URL" prop="coverImage">
                <el-input v-model="form.coverImage" placeholder="请输入图片 URL 地址..." clearable />
              </el-form-item>

              <el-form-item label="所属分类" prop="categoryIds">
                <el-select v-model="form.categoryIds" multiple placeholder="请选择分类(多选)" style="width: 100%;">
                  <el-option v-for="item in categoryOptions" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>

              <el-form-item label="文章标签" prop="tagIds">
                <el-select v-model="form.tagIds" multiple placeholder="请选择标签(多选)" style="width: 100%;">
                  <el-option v-for="item in tagOptions" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>

              <el-form-item label="是否允许评论" prop="allowComment">
                <el-radio-group v-model="form.allowComment">
                  <el-radio :value="1">允许评论</el-radio>
                  <el-radio :value="0">禁止评论</el-radio>
                </el-radio-group>
              </el-form-item>
            </div>
          </el-col>
        </el-row>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'

import AiEditor from '@/components/AiEditor.vue'
import { useArticleStore } from '@/stores/article'
import { storeToRefs } from 'pinia'

const route = useRoute()
const router = useRouter()
const articleStore = useArticleStore()

const formRef = ref(null)
const aiEditorRef = ref(null)
const pageLoading = ref(false)
const submitLoading = ref(false)

const { publicCategories: categoryOptions, publicTags: tagOptions } = storeToRefs(articleStore)

const form = reactive({
  id: null,
  title: '',
  content: '',
  summary: '',
  coverImage: '',
  status: 1,
  allowComment: 1,
  categoryIds: [],
  tagIds: []
})

const rules = {
  title: [{ required: true, message: '请填写文章标题', trigger: 'blur' }]
}

const initArticleData = async () => {
  const id = route.query.id
  if (!id) return

  pageLoading.value = true
  try {
    const detail = await articleStore.fetchArticleById(id)
    if (detail) {
      form.id = detail.id
      form.title = detail.title
      form.content = detail.content
      form.summary = detail.summary || ''
      form.coverImage = detail.coverImage || ''
      form.status = detail.status
      form.allowComment = detail.allowComment
      form.categoryIds = (detail.categories || []).map(c => c.id)
      form.tagIds = (detail.tags || []).map(t => t.id)
    }
  } catch (error) {
    ElMessage.error('文章数据加载失败')
  } finally {
    pageLoading.value = false
  }
}

const handleSave = (status) => {
  if (!form.title.trim()) {
    ElMessage.warning('请输入文章标题')
    return
  }

  // 优先从 AiEditor 实例取最新 HTML
  const editorHtml = aiEditorRef.value?.getHtml() || ''
  if (editorHtml.trim() === '' || editorHtml.trim() === '<p></p>') {
    ElMessage.warning('文章内容不能为空')
    return
  }
  form.content = editorHtml

  formRef.value?.validate(async (valid) => {
    if (!valid) return
    submitLoading.value = true
    form.status = status

    try {
      if (form.id) {
        await articleStore.editArticle(form)
        ElMessage.success(status === 1 ? '文章更新并发布成功' : '草稿保存成功')
      } else {
        await articleStore.addArticle(form)
        ElMessage.success(status === 1 ? '文章发表成功' : '草稿保存成功')
      }

      router.push({ name: 'AdminArticles' })
    } catch (error) {
      console.error(error)
    } finally {
      submitLoading.value = false
    }
  })
}

const goBack = () => {
  if (form.title.trim() || form.content.trim()) {
    ElMessageBox.confirm('未保存的数据将会丢失，确定要返回吗？', '提示', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      type: 'warning'
    }).then(() => {
      router.back()
    }).catch(() => { })
  } else {
    router.back()
  }
}

onMounted(async () => {
  await initArticleData()
})
</script>

<style scoped lang="scss">
.write-page-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background-color: #fafafa;
  overflow: hidden;
}

.write-header {
  height: 60px;
  background-color: #ffffff;
  border-bottom: 1px solid #f3f4f6;
  padding: 0 24px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  z-index: 10;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .editor-status-text {
    font-size: 0.85rem;
    color: #9ca3af;
    font-weight: 500;
  }
}

.write-body {
  flex: 1;
  overflow: hidden;
  padding: 24px;
}

.write-form {
  height: 100%;
}

.editor-main-col {
  height: 100%;
  display: flex;
  flex-direction: column;
  gap: 16px;
  overflow-y: auto;
  padding-right: 12px;

  .title-form-item {
    margin-bottom: 0;
  }

  .immersive-title-input {
    width: 100%;
    border: none;
    outline: none;
    font-size: 1.8rem;
    font-weight: 800;
    color: #111827;
    background: transparent;
    padding: 8px 0;
    border-bottom: 2px solid transparent;
    transition: border-color 0.3s;

    &::placeholder {
      color: #d1d5db;
    }

    &:focus {
      border-color: #f3f4f6;
    }
  }
}

// 编辑器包裹容器 —— 关键：让 AiEditor 填满剩余空间
.editor-wrapper {
  background-color: #ffffff;
  border-radius: 12px;
  border: 1px solid #f3f4f6;
  min-height: 0; // 防止 flex 子元素溢出
  flex: 1;
  overflow: hidden; // AiEditor 内部自带滚动条
}

.editor-sidebar-col {
  height: 100%;
  overflow-y: auto;
}

.sidebar-inner-card {
  background-color: #ffffff;
  border-radius: 12px;
  border: 1px solid #f3f4f6;
  padding: 20px;

  .panel-title {
    font-size: 1rem;
    font-weight: 700;
    color: #111827;
    margin-top: 0;
    margin-bottom: 20px;
    padding-bottom: 10px;
    border-bottom: 1px solid #f9fafb;
  }
}
</style>