import { defineStore } from 'pinia'
import { getPublicConfigInfo } from '@/api/config'

export const useConfigStore = defineStore('config', {
  state: () => ({
    isLoaded: false,
    configData: null  // 添加 state 来存储配置数据
  }),
  actions: {
    
    async fetchConfig() {
      if (this.isLoaded) return 
      
      try {
        const res = await getPublicConfigInfo()
        this.isLoaded = true
        this.configData = res  // 拦截器已解包到 data 层
        return res
      } catch (error) {
        console.error('全局获取系统配置失败:', error)
        throw error  // 可以选择抛出错误，让调用方处理
      }
    }
  }
})