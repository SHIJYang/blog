import { ref, computed } from 'vue'
import { defineStore } from 'pinia'
import { getPublicCommentPage, postPublicComment } from '@/api/comment'

export const useCommentStore = defineStore('comment', () => {
  const comments = ref([])        // 当前文章评论树（根评论带 replies）
  const total = ref(0)           // 后端返回的根评论总数
  const loading = ref(false)
  const loadingMore = ref(false)
  const error = ref(null)
  const currentArticleId = ref(null)
  const page = ref(1)
  const pages = ref(1)
  const size = 10

  const isEmpty = computed(() => comments.value.length === 0)
  const hasMore = computed(() => page.value < pages.value)

  /** 递归找出所有评论（含回复）的文本，用于判断刚发的评论是否已可见 */
  function flatten(list = comments.value) {
    return list.flatMap(c => [c, ...flatten(c.replies || [])])
  }

  function hasCommentWithContent(content) {
    const text = String(content || '').trim()
    if (!text) return false
    return flatten().some(c => String(c.content || '').trim() === text)
  }

  async function fetchComments(articleId, targetPage = 1) {
    if (!articleId) return

    if (currentArticleId.value && currentArticleId.value !== articleId) {
      clearComments()
    }
    currentArticleId.value = articleId

    const isFirst = targetPage === 1
    if (isFirst) {
      loading.value = true
      error.value = null
    } else {
      loadingMore.value = true
    }

    try {
      const data = await getPublicCommentPage({
        articleId,
        current: targetPage,
        size
      })

      const list = data?.records || []

      if (isFirst) {
        comments.value = list
        total.value = data?.total ?? list.length
      } else {
        const seen = new Set(comments.value.map(c => c.id))
        comments.value.push(...list.filter(c => !seen.has(c.id)))
        total.value = data?.total ?? total.value
      }

      page.value = data?.current ?? targetPage
      pages.value = Math.max(1, data?.pages ?? 1)
    } catch (e) {
      error.value = e?.message || '评论加载失败'
      if (isFirst) {
        comments.value = []
        total.value = 0
        page.value = 1
        pages.value = 1
      }
    } finally {
      loading.value = false
      loadingMore.value = false
    }
  }

  /** 加载下一页（“加载更多”） */
  function fetchMore() {
    if (!currentArticleId.value || loading.value || loadingMore.value || !hasMore.value) return
    return fetchComments(currentArticleId.value, page.value + 1)
  }

  async function addComment(data) {
    const created = await postPublicComment(data)
    if (currentArticleId.value) {
      await fetchComments(currentArticleId.value, 1)
    }
    return created
  }

  async function addReply(parentId, replyData) {
    const created = await postPublicComment({
      ...replyData,
      parentId
    })
    if (currentArticleId.value) {
      await fetchComments(currentArticleId.value, 1)
    }
    return created
  }

  function clearComments() {
    comments.value = []
    total.value = 0
    error.value = null
    currentArticleId.value = null
    page.value = 1
    pages.value = 1
    loading.value = false
    loadingMore.value = false
  }

  return {
    comments,
    total,
    loading,
    loadingMore,
    error,
    currentArticleId,
    page,
    pages,
    isEmpty,
    hasMore,
    hasCommentWithContent,
    fetchComments,
    fetchMore,
    addComment,
    addReply,
    clearComments
  }
})
