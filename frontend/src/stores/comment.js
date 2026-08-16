import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { getPublicCommentPage, postPublicComment } from '@/api/comment'

export const useCommentStore = defineStore('comment', () => {
  const comments = ref([])        // 当前文章评论树
  const total = ref(0)           // 总评论数（后端返回的总数）
  const loading = ref(false)
  const error = ref(null)
  const currentArticleId = ref(null)

  const isEmpty = computed(() => comments.value.length === 0)

  // 递归统计（备用，后端 total 已可信）
  function countAll(list) {
    return list.reduce((sum, c) => sum + 1 + countAll(c.replies || []), 0)
  }

  async function fetchComments(articleId, page = 1, size = 20) {
    if (!articleId) return

    if (currentArticleId.value && currentArticleId.value !== articleId) {
      clearComments()
    }
    currentArticleId.value = articleId

    loading.value = true
    error.value = null

    try {
      const res = await getPublicCommentPage({
        articleId,
        current: page,
        size
      })

      const data = res.data || res
      const list = data.records || []

      if (page === 1) {
        comments.value = list
        total.value = data.total ?? countAll(list)
      } else {
        comments.value.push(...list)
        total.value = data.total ?? countAll(comments.value)
      }
    } catch (e) {
      error.value = e.message || '评论加载失败'
      comments.value = []
      total.value = 0
    } finally {
      loading.value = false
    }
  }

  async function addComment(data) {
    await postPublicComment(data)
    // 发表成功后重新加载第一页
    if (currentArticleId.value) {
      await fetchComments(currentArticleId.value, 1)
    }
  }

  async function addReply(parentId, replyData) {
    await postPublicComment({
      ...replyData,
      parentId
    })
    if (currentArticleId.value) {
      await fetchComments(currentArticleId.value, 1)
    }
  }

  function clearComments() {
    comments.value = []
    total.value = 0
    error.value = null
    currentArticleId.value = null
  }

  return {
    comments,
    total,
    loading,
    error,
    currentArticleId,
    isEmpty,
    fetchComments,
    addComment,
    addReply,
    clearComments
  }
})