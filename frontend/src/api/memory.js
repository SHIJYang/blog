import request from '@/utils/request'

/**
 * Memory 记忆管理接口
 * 封装自 api.txt 中 /api/memory/* 端点。
 */

/** 统计概览 */
export function getMemoryStat(userId) {
  return request({
    url: '/memory/stat',
    method: 'get',
    params: userId ? { userId } : {}
  })
}

/**
 * 记忆列表（分页）
 * @param {object} query MemoryQueryDto: { current?, size?, status?, order? }
 * @param {number} [userId] 管理员可选：指定用户；不传=全部
 */
export function getMemoryList(query, userId) {
  return request({
    url: '/memory/list',
    method: 'get',
    params: userId ? { query, userId } : { query }
  })
}

/**
 * 记忆搜索（分页）
 * @param {object} dto MemorySearchDto: { keyword?, current?, size? }
 * @param {number} [userId] 管理员可选
 */
export function searchMemory(dto, userId) {
  return request({
    url: '/memory/search',
    method: 'get',
    params: userId ? { dto, userId } : { dto }
  })
}

/** 记忆详情 */
export function getMemoryDetail(memoryId) {
  return request({
    url: '/memory/detail',
    method: 'get',
    params: { memoryId }
  })
}

/** 获取验证码（用于对话防护） */
export function getMemoryCaptcha() {
  return request({
    url: '/memory/captcha',
    method: 'get'
  })
}

/** AI 开场白 */
export function getMemoryOpening(memoryId) {
  return request({
    url: '/memory/ai/opening',
    method: 'get',
    params: { memoryId }
  })
}

/** 发布状态 */
export function getMemoryPublishStatus(memoryId) {
  return request({
    url: '/memory/publish-status',
    method: 'get',
    params: { memoryId }
  })
}

/**
 * 创建记忆（上传文件）
 * @param {File} file 记忆源文件
 */
export function createMemory(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/memory/create',
    method: 'post',
    data: formData
  })
}

/**
 * 日记转为博客公开文章（幂等：已发布则返回已有文章ID）
 * @param {number} memoryId 记忆 ID
 * @returns {Promise<number>} 文章 ID
 */
export function publishMemory(memoryId) {
  return request({
    url: '/memory/publish',
    method: 'post',
    data: { memoryId }
  })
}

/**
 * 废弃记忆（状态置为已废弃 + 清理向量）
 * @param {number} memoryId 记忆 ID
 */
export function abandonMemory(memoryId) {
  return request({
    url: '/memory/abandon',
    method: 'post',
    data: { memoryId }
  })
}

/**
 * 浓缩/自定义标题
 * @param {number} memoryId
 * @param {string} [customTitle]
 */
export function condenseMemory(memoryId, customTitle) {
  return request({
    url: '/memory/condense',
    method: 'post',
    data: { memoryId, customTitle }
  })
}

/**
 * 文字对话
 * @param {object} payload ChatSendDto: { memoryId, content, captchaCode, captchaKey }
 */
export function chatSendMemory(payload) {
  return request({
    url: '/memory/chat/send',
    method: 'post',
    data: payload
  })
}

/**
 * 图片对话
 * @param {number} memoryId
 * @param {File} file 图片文件
 */
export function chatImgMemory(memoryId, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/memory/chat/img',
    method: 'post',
    data: formData,
    params: { memoryId }
  })
}

/**
 * 查询某条记忆的一句话知识点
 * @param {number} memoryId
 * @returns {Promise<Array<{id,content,emotion}>>}
 */
export function getMemoryInsights(memoryId) {
  return request({
    url: '/memory/insights',
    method: 'get',
    params: { memoryId }
  })
}

/**
 * 用户手动添加一条记忆闪光点（自动向量化，后续聊天可提起）
 * @param {number} memoryId
 * @param {string} content
 */
export function addMemoryInsight(memoryId, content) {
  return request({
    url: '/memory/insight/add',
    method: 'post',
    data: { memoryId, content }
  })
}

/**
 * 删除一条记忆闪光点（仅本人/管理员）
 * @param {number} insightId
 */
export function deleteMemoryInsight(insightId) {
  return request({
    url: '/memory/insight/delete',
    method: 'post',
    data: { insightId }
  })
}

/**
 * 设置记忆隐私等级（0-公开 1-私密 2-封存）
 * @param {number} memoryId
 * @param {number} privacy 0/1/2
 */
export function setMemoryPrivacy(memoryId, privacy) {
  return request({
    url: '/memory/privacy',
    method: 'post',
    data: { memoryId, privacy }
  })
}

/**
 * 编辑记忆（标题 / 情绪标签多选 / 正文）
 * @param {object} params { memoryId, autoTitle?, emotions?:string[], finalDiary? }
 */
export function updateMemory(params) {
  return request({
    url: '/memory/update',
    method: 'post',
    data: params
  })
}

/**
 * 当前用户情绪标签统计
 * @param {number} [userId] 管理员可选
 * @returns {Promise<Array<{emotion:string,count:number}>>}
 */
export function getMemoryEmotionStats(userId) {
  return request({
    url: '/memory/emotion/stats',
    method: 'get',
    params: userId ? { userId } : {}
  })
}

/**
 * 触发情绪归并
 * @param {number} [userId] 管理员可选
 */
export function mergeMemoryEmotions(userId) {
  return request({
    url: '/memory/emotion/merge',
    method: 'post',
    params: userId ? { userId } : {}
  })
}
