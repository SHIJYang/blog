import request from '@/utils/request'

export function createTag(data) {
  console.log('createTag data:', data)
  return request({
    url: '/admin/tags',
    method: 'post',
    data
  })
}

export function updateTag(data) {
  return request({
    url: '/admin/tags',
    method: 'put',
    data
  })
}

export function getTagPage(params) {
  return request({
    url: '/admin/tags/page',
    method: 'get',
    params
  })
}

export function deleteTag(id) {
  return request({
    url: `/admin/tags/${id}`,
    method: 'delete'
  })
}



/**
 * 获取公开标签列表 (无需登录)
 * 用于前台标签云展示
 */
export function getPublicTags() {
  return request({
    url: '/public/tags/list',
    method: 'get'
  })
}