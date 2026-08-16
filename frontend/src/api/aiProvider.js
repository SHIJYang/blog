import request from '@/utils/request'

/**
 * AI 对话供应商管理（数据库版）
 * 供应商入 ai_provider 表，支持增删改查、激活/禁用
 */

/** 查询全部供应商（Key 已脱敏） */
export function getAiProviders() {
  return request({
    url: '/admin/ai-provider',
    method: 'get'
  })
}

/** 新增供应商 */
export function createAiProvider(data) {
  return request({
    url: '/admin/ai-provider',
    method: 'post',
    data
  })
}

/** 更新供应商（Key 回传空/脱敏时保留原值） */
export function updateAiProvider(id, data) {
  return request({
    url: `/admin/ai-provider/${id}`,
    method: 'put',
    data
  })
}

/** 删除供应商 */
export function deleteAiProvider(id) {
  return request({
    url: `/admin/ai-provider/${id}`,
    method: 'delete'
  })
}

/** 激活供应商 */
export function activateAiProvider(id) {
  return request({
    url: `/admin/ai-provider/${id}/activate`,
    method: 'post'
  })
}

/** 测试连通性 */
export function testAiProvider(id) {
  return request({
    url: `/admin/ai-provider/${id}/test`,
    method: 'post'
  })
}
