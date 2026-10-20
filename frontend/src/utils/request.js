import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  // 开发/生产均由环境变量提供，未配置时回退 /api（与旧行为一致）
  baseURL: import.meta.env.VITE_API_BASE || '/api',
  timeout: 10000
})

// 请求拦截器
request.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    if (token) {
      // 按照后端 JwtAuthenticationTokenFilter 的要求配置请求头
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器
request.interceptors.response.use(
  (response) => {
    const res = response.data

    // 如果是二进制等非 JSON 响应，直接返回
    if (response.config.responseType === 'blob') {
      return response
    }

    // 根据后端定义的 Result 类，200 表示操作成功
    if (res.code === 200) {
      return res.data
    } else {
      // 处理特定错误，例如：401/403 Token 失效或无权限
      if (res.code === 401 || res.code === 403) {
        localStorage.removeItem('token')
        router.push('/login')
      }
      ElMessage.error(res.message || '操作失败')
      return Promise.reject(new Error(res.message || 'Error'))
    }
  },
  (error) => {
    // 捕获网络异常等系统级错误
    ElMessage.error(error.message || '系统网络异常')
    return Promise.reject(error)
  }
)

export default request