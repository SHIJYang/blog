import request from '@/utils/request'

// 后台管理接口
export function createArticle(data) {
  return request({
    url: '/admin/articles',
    method: 'post',
    data
  })
}

export function updateArticle(data) {
  return request({
    url: '/admin/articles',
    method: 'put',
    data
  })
}

export function getArticlePage(params) {
  return request({
    url: '/admin/articles/page',
    method: 'get',
    params
  })
}

export function getArticleById(id) {
  return request({
    url: `/admin/articles/${id}`,
    method: 'get'
  })
}

export function deleteArticle(id) {
  return request({
    url: `/admin/articles/${id}`,
    method: 'delete'
  })
}

// 前台公开接口
export function getPublicArticlePage(params) {
  return request({
    url: '/public/articles/page',
    method: 'get',
    params
  })
}

export function getPublicArticleDetail(id) {
  return request({
    url: `/public/articles/${id}`,
    method: 'get'
  })
}
