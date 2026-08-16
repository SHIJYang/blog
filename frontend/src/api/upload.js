import request from '@/utils/request'

/**
 * 上传图片到后端
 * @param {File} file 图片文件
 * @param {number} [isPrivate=0] 0-公开 1-私有
 * @returns {Promise<any>} 后端 ResultObject 的 data 层（上传结果/URL）
 */
export function uploadImage(file, isPrivate = 0) {
  const formData = new FormData()
  formData.append('file', file)
  return request({
    url: '/upload/image',
    method: 'post',
    data: formData,
    params: { isPrivate }
    // 不手动指定 Content-Type，让 axios 自动生成 multipart/form-data 含 boundary
  })
}
