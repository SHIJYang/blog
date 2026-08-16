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
  if (configStore.configData?.bgurl) {
    return {
      backgroundImage: `url(${configStore.configData.bgurl})`,
      backgroundSize: 'cover',
      backgroundPosition: 'center',
      backgroundRepeat: 'no-repeat',
      backgroundAttachment: 'fixed'   // ← 关键：背景固定
    }
  }
  return {}
})

onMounted(async () => {
  configStore.fetchConfig()
  articleStore.initPublicMeta()
})
</script>

<style scoped>
.app-container {
  min-height: 100vh;
  /* 内容少时也撑满视口 */
  width: 100%;
  /* 不再使用 fixed 和 overflow */
}
</style>