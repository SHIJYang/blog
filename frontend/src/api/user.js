import request from '@/utils/request'

export function createUser(data) {
  return request({
    url: '/admin/users',
    method: 'post',
    data
  })
}

export function updateUser(data) {
  return request({
    url: '/admin/users',
    method: 'put',
    data
  })
}

export function getUserPage(params) {
  return request({
    url: '/admin/users/page',
    method: 'get',
    params
  })
}

export function getUserById(id) {
  return request({
    url: `/admin/users/${id}`,
    method: 'get'
  })
}

export function deleteUser(id) {
  return request({
    url: `/admin/users/${id}`,
    method: 'delete'
  })
}