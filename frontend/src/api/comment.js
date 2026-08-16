import request from '@/utils/request'

// 后台管理接口
export function updateCommentStatus(id, status) {
  return request({
    url: `/admin/comments/${id}/status`,
    method: 'put',
    params: { status }
  })
}

export function getCommentPage(params) {
  return request({
    url: '/admin/comments/page',
    method: 'get',
    params
  })
}

export function deleteComment(id) {
  return request({
    url: `/admin/comments/${id}`,
    method: 'delete'
  })
}

// 前台公开接口
export function postPublicComment(data) {
  return request({
    url: '/public/comments',
    method: 'post',
    data
  })
}

export function getPublicCommentPage(params) {
  return request({
    url: '/public/comments/page',
    method: 'get',
    params
  })
}