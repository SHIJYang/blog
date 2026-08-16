// stores/auth.js
import { defineStore } from 'pinia'
import { login as loginApi, register as registerApi } from '@/api/auth'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    role: localStorage.getItem('role') || '',
    username: localStorage.getItem('username') || '',
    userInfo: null
  }),

  getters: {
    isLoggedIn: (state) => !!state.token,
    isAdmin: (state) => state.role === 'ADMIN',
    displayName: (state) => state.userInfo?.nickname || state.username || ''
  },

  actions: {
    // 登录
    async login(loginForm) {
      const res = await loginApi(loginForm)
      return res
    },

    // 注册
    async register(regForm) {
      const res = await registerApi(regForm)
      return res
    },

    // 设置认证信息
    setAuth(token, role, username) {
      this.token = token
      this.role = role
      this.username = username
    },

    // 清除认证信息
    logout() {
      this.token = ''
      this.role = ''
      this.username = ''
      this.userInfo = null
      localStorage.removeItem('token')
      localStorage.removeItem('role')
      localStorage.removeItem('username')
    }
  }
})
