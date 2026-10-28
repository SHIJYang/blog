import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  // 固定使用相对路径：dev 由 vite proxy 转发到本机 8080，生产由 Nginx 同域反代
  baseURL: '/api',
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
    // 封存解锁令牌：5 分钟无操作失效，由后端滑动续期
    const sealedToken = localStorage.getItem('sealedToken')
    if (sealedToken) {
      config.headers['X-Sealed-Token'] = sealedToken
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
      // 仅 401 视为登录失效；403 是业务拒绝（如封存未解锁、无权访问），不能把用户踢下线
      if (res.code === 401) {
        localStorage.removeItem('token')
        localStorage.removeItem('sealedToken')
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