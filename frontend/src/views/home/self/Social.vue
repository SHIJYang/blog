<template>
  <div class="social">
    <SocialGrid :value="socialList" text="GAME" @click-item="handleItemClick" />
  </div>
</template>

<script setup lang="ts">
import { shallowRef } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { Watermelon, Pear, Mug, GobletSquareFull, KnifeFork, Sugar, Bowl, MilkTea, House } from '@element-plus/icons-vue'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const authStore = useAuthStore()

const socialList = shallowRef([
  {
    icon: Watermelon,
    color: '#ff4d6d',        // 西瓜红
    hoverBgColor: '#2ecc71', // 西瓜皮绿
    link: ''
  },
  {
    icon: Pear,
    color: '#85bc3c',        // 青梨绿
    hoverBgColor: '#cddc39', // 柠檬黄绿
    link: ''
  },
  {
    icon: Mug,
    color: '#b5835a',        // 咖啡可可棕
    hoverBgColor: '#d4a373', // 温暖驼色
    link: ''
  },
  {
    icon: GobletSquareFull,
    color: '#9e2a2b',        // 红酒红
    hoverBgColor: '#9c27b0', // 优雅香槟紫
    link: ''
  },
  {
    icon: House,             // 👈 索引 4 (安全登出)
    color: '#f56c6c',        // 醒目警告红
    hoverBgColor: '#ef4444', // 深登出红
    link: ''
  },
  {
    icon: KnifeFork,
    color: '#909399',        // 不锈钢金属灰
    hoverBgColor: '#606266', // 深钢灰
    link: ''
  },
  {
    icon: Sugar,
    color: '#ff85a1',        // 甜糖果粉
    hoverBgColor: '#f472b6', // 樱花粉
    link: ''
  },
  {
    icon: Bowl,
    color: '#409eff',        // 优雅青花瓷蓝
    hoverBgColor: '#3a8ee6', // 经典深蓝
    link: ''
  },
  {
    icon: MilkTea,
    color: '#c68b59',        // 焦糖奶茶金
    hoverBgColor: '#d2b48c', // 丝滑麦芽沙色
    link: ''
  }
])

// 传入 TS 显式参数类型定义
const handleItemClick = ({ item, index }: { item: any; index: number }) => {
  // 🟢 严格按照您的设计需求，使用硬编码 4
  if (index === 4) {

    authStore.logout()
    ElMessage.success('已安全退出登录')
    setTimeout(() => {
      router.push('/login')

    }, 300)


    return
  }

  // 其他普通社交链接跳转
  if (item.link) {
    window.open(item.link, '_blank')
  }

  console.log('点击了卡片: ', item, '索引: ', index)
}
</script>

<style scoped>
.social {
  display: flex;
  justify-content: center;
  align-items: center;
  width: 100%;
  height: 100%;
}
</style>