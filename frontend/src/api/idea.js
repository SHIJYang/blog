import request from '@/utils/request'

/**
 * 念生：想法工作台。
 * targetUserId 仅管理员用于切换查看某个用户。
 */

export function getIdeaStates() {
  return request({ url: '/idea/states', method: 'get' })
}

export function getIdeaBoard(targetUserId) {
  return request({
    url: '/idea/board',
    method: 'get',
    params: targetUserId ? { targetUserId } : {}
  })
}

export function getIdeaDetail(id, targetUserId) {
  return request({
    url: `/idea/${id}`,
    method: 'get',
    params: targetUserId ? { targetUserId } : {}
  })
}

export function createIdea(data) {
  return request({ url: '/idea', method: 'post', data })
}

export function updateIdea(id, data, targetUserId) {
  return request({
    url: `/idea/${id}`,
    method: 'put',
    data,
    params: targetUserId ? { targetUserId } : {}
  })
}

export function deleteIdea(id, targetUserId) {
  return request({
    url: `/idea/${id}`,
    method: 'delete',
    params: targetUserId ? { targetUserId } : {}
  })
}

/** 状态迁移：后端按 idea_transition_rule 校验，非法迁移返回 409 */
export function moveIdea(id, toState, reason, targetUserId) {
  return request({
    url: `/idea/${id}/move`,
    method: 'patch',
    data: { toState, reason },
    params: targetUserId ? { targetUserId } : {}
  })
}

export function getIdeaHistory(id, targetUserId) {
  return request({
    url: `/idea/${id}/history`,
    method: 'get',
    params: targetUserId ? { targetUserId } : {}
  })
}

export function analyzeIdea(id, targetUserId) {
  return request({
    url: `/idea/${id}/analyze`,
    method: 'post',
    params: targetUserId ? { targetUserId } : {}
  })
}

export function challengeIdea(id, targetUserId) {
  return request({
    url: `/idea/${id}/challenge`,
    method: 'post',
    params: targetUserId ? { targetUserId } : {}
  })
}

export function getIdeaAnalyses(id, kind, targetUserId) {
  return request({
    url: `/idea/${id}/analyses`,
    method: 'get',
    params: { ...(kind ? { kind } : {}), ...(targetUserId ? { targetUserId } : {}) }
  })
}

/** 拾光闪光点 → 想法 */
export function createIdeaFromInsight(insightId) {
  return request({
    url: '/idea/from-insight',
    method: 'post',
    data: { insightId }
  })
}

// ==================== Prompt 治理（仅 ADMIN） ====================

export function getAiPrompts(scene) {
  return request({
    url: '/admin/ai-prompts',
    method: 'get',
    params: scene ? { scene } : {}
  })
}

export function createAiPrompt(data) {
  return request({ url: '/admin/ai-prompts', method: 'post', data })
}

export function activateAiPrompt(id) {
  return request({ url: `/admin/ai-prompts/${id}/activate`, method: 'post' })
}
