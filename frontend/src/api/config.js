import request from '@/utils/request'

export function createConfig(data) {
  return request({
    url: '/admin/configs',
    method: 'post',
    data
  })
}

export function updateConfig(data) {
  return request({
    url: '/admin/configs',
    method: 'put',
    data
  })
}

export function getConfigByKey(key) {
  return request({
    url: `/admin/configs/${key}`,
    method: 'get'
  })
}

export function getConfigPage(params) {
  return request({
    url: '/admin/configs/page',
    method: 'get',
    params
  })
}

export function getAllConfigs() {
  return request({
    url: '/admin/configs/all',
    method: 'get'
  })
}

export function deleteConfig(id) {
  return request({
    url: `/admin/configs/${id}`,
    method: 'delete'
  })
}


export function getPublicConfigInfo() {
  return request({
    url: '/public/configs/info',
    method: 'get'
  })
}