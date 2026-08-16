import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  createArticle,
  updateArticle,
  getArticlePage,
  getArticleById,
  deleteArticle,
  getPublicArticlePage,
  getPublicArticleDetail
} from '@/api/article'
import {
  createCategory,
  updateCategory,
  getCategoryPage,
  deleteCategory,
  getPublicCategories
} from '@/api/category'
import {
  createTag,
  updateTag,
  getTagPage,
  deleteTag,
  getPublicTags
} from '@/api/tag'

export const useArticleStore = defineStore('article', () => {
  // ================= 状态 (State) =================

  // 后台管理状态
  const adminArticles = ref([])
  const adminPagination = ref({
    current: 1,
    size: 10,
    total: 0,
    pages: 1
  })

  // 前台公开状态
  const publicArticles = ref([])
  const publicPagination = ref({
    current: 1,
    size: 9,
    total: 0,
    pages: 1
  })

  // 当前选中的文章详情
  const currentArticle = ref(null)

  // 全局缓存：分类列表（前台公开）
  const publicCategories = ref([])

  // 全局缓存：标签列表（前台公开）
  const publicTags = ref([])

  // ================= 异步操作 (Actions) =================

  /**
   * 初始化全局缓存：一次性加载分类和标签
   */
  async function initPublicMeta() {
    try {
      const [catRes, tagRes] = await Promise.all([
        getPublicCategories(),
        getPublicTags()
      ])
      publicCategories.value = Array.isArray(catRes) ? catRes : (catRes?.records || [])
      publicTags.value = Array.isArray(tagRes) ? tagRes : (tagRes?.records || [])
    } catch (err) {
      console.error('初始化分类/标签缓存失败:', err)
    }
  }

  /**
   * 获取后台文章分页列表
   */
  async function fetchAdminArticles(params) {
    try {
      const res = await getArticlePage(params)
      adminArticles.value = res.records
      adminPagination.value = {
        current: res.current,
        size: res.size,
        total: res.total,
        pages: res.pages
      }
      return res
    } catch (error) {
      console.error('获取后台文章列表失败:', error)
      throw error
    }
  }

  /**
   * 根据ID获取后台文章详情
   */
  async function fetchArticleById(id) {
    try {
      const res = await getArticleById(id)
      currentArticle.value = res
      return res
    } catch (error) {
      console.error('获取文章详情失败:', error)
      throw error
    }
  }

  /**
   * 创建文章
   */
  async function addArticle(data) {
    try {
      return await createArticle(data)
    } catch (error) {
      console.error('创建文章失败:', error)
      throw error
    }
  }

  /**
   * 更新文章
   */
  async function editArticle(data) {
    try {
      return await updateArticle(data)
    } catch (error) {
      console.error('更新文章失败:', error)
      throw error
    }
  }

  /**
   * 删除文章
   */
  async function removeArticle(id) {
    try {
      return await deleteArticle(id)
    } catch (error) {
      console.error('删除文章失败:', error)
      throw error
    }
  }

  /**
   * 获取前台公开文章分页列表
   */
  async function fetchPublicArticles(params) {
    try {
      const res = await getPublicArticlePage(params)
      publicArticles.value = res.records
      publicPagination.value = {
        current: res.current,
        size: res.size,
        total: res.total,
        pages: res.pages
      }
      return res
    } catch (error) {
      console.error('获取公开文章列表失败:', error)
      throw error
    }
  }

  /**
   * 获取前台公开文章详情
   */
  async function fetchPublicArticleDetail(id) {
    try {
      const res = await getPublicArticleDetail(id)
      currentArticle.value = res
      return res
    } catch (error) {
      console.error('获取公开文章详情失败:', error)
      throw error
    }
  }

  // ================= 分类管理 =================

  async function addCategory(data) {
    try {
      return await createCategory(data)
    } catch (error) {
      console.error('创建分类失败:', error)
      throw error
    }
  }

  async function updateCategoryAction(data) {
    try {
      return await updateCategory(data)
    } catch (error) {
      console.error('更新分类失败:', error)
      throw error
    }
  }

  async function fetchAdminCategories(params) {
    try {
      return await getCategoryPage(params)
    } catch (error) {
      console.error('获取分类列表失败:', error)
      throw error
    }
  }

  async function removeCategory(id) {
    try {
      return await deleteCategory(id)
    } catch (error) {
      console.error('删除分类失败:', error)
      throw error
    }
  }

  // ================= 标签管理 =================

  async function addTag(data) {
    try {
      return await createTag(data)
    } catch (error) {
      console.error('创建标签失败:', error)
      throw error
    }
  }

  async function updateTagAction(data) {
    try {
      return await updateTag(data)
    } catch (error) {
      console.error('更新标签失败:', error)
      throw error
    }
  }

  async function fetchAdminTags(params) {
    try {
      return await getTagPage(params)
    } catch (error) {
      console.error('获取标签列表失败:', error)
      throw error
    }
  }

  async function removeTag(id) {
    try {
      return await deleteTag(id)
    } catch (error) {
      console.error('删除标签失败:', error)
      throw error
    }
  }

  // 清空当前文章详情
  function clearCurrentArticle() {
    currentArticle.value = null
  }

  return {
    // 文章状态
    adminArticles,
    adminPagination,
    publicArticles,
    publicPagination,
    currentArticle,
    // 全局缓存
    publicCategories,
    publicTags,
    // 文章操作
    fetchAdminArticles,
    fetchArticleById,
    addArticle,
    editArticle,
    removeArticle,
    fetchPublicArticles,
    fetchPublicArticleDetail,
    clearCurrentArticle,
    // 初始化全局缓存
    initPublicMeta,
    // 分类操作
    addCategory,
    updateCategoryAction,
    fetchAdminCategories,
    removeCategory,
    // 标签操作
    addTag,
    updateTagAction,
    fetchAdminTags,
    removeTag
  }
})
