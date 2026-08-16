import request from '@/utils/request'

export function createCategory(data) {
  return request({
    url: '/admin/categories',
    method: 'post',
    data
  })
}

export function updateCategory(data) {
  return request({
    url: '/admin/categories',
    method: 'put',
    data
  })
}

export function getCategoryPage(params) {
  return request({
    url: '/admin/categories/page',
    method: 'get',
    params
  })
}

export function deleteCategory(id) {
  return request({
    url: `/admin/categories/${id}`,
    method: 'delete'
  })
}


/**
 * 获取公开分类列表 (无需登录)
 * 用于前台侧边栏或头部导航
 */
export function getPublicCategories() {
  return request({
    url: '/public/categories/list',
    method: 'get'
  })
}