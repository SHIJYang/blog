<template>
  <div class="app-container" :style="bgStyle">
    <router-view />
  </div>
</template>

<script setup>
import { onMounted, computed } from 'vue'
import { useConfigStore } from '@/stores/config'
import { useArticleStore } from '@/stores/article'

const configStore = useConfigStore()
const articleStore = useArticleStore()

const bgStyle = computed(() => {
  const url = configStore.configData?.bgurl
  // 后端可能把 null 序列化成字符串 "null"，这里一并过滤
  if (!url || url === 'null' || url === 'undefined') return {}
  return {
    backgroundImage: `url(${url})`,
    backgroundSize: 'cover',
    backgroundPosition: 'center',
    backgroundRepeat: 'no-repeat',
    backgroundAttachment: 'fixed'   // ← 关键：背景固定
  }
})

onMounted(async () => {
  configStore.fetchConfig()
  articleStore.initPublicMeta()
})
</script>

<style>
/* 基础 reset：项目未加载全局样式表，这里补上最小必要项
   （缺 box-sizing 会让带 padding 的容器宽度溢出） */
*,
*::before,
*::after {
  box-sizing: border-box;
}

html,
body {
  margin: 0;
  padding: 0;
}

body {
  overflow-x: hidden;
}
</style>

<style scoped>
.app-container {
  min-height: 100vh;
  /* 内容少时也撑满视口 */
  width: 100%;
  /* 不再使用 fixed 和 overflow */
}
</style>