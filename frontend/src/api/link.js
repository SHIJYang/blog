import request from '@/utils/request'

export function createLink(data) {
  return request({
    url: '/admin/links',
    method: 'post',
    data
  })
}

export function updateLink(data) {
  return request({
    url: '/admin/links',
    method: 'put',
    data
  })
}

export function getLinkPage(params) {
  return request({
    url: '/admin/links/page',
    method: 'get',
    params
  })
}

export function deleteLink(id) {
  return request({
    url: `/admin/links/${id}`,
    method: 'delete'
  })
}


export function getPublicLinks() {
  return request({
    url: '/public/links/list',
    method: 'get'
  })
}