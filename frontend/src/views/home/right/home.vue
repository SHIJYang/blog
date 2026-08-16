<template>
  <div class="home-page">
    <TextType :text="['你好呀', '欢迎来到我的网站', '祝你快乐']" :typing-speed="100" :pause-duration="1500" :show-cursor="true"
      cursor-character="|" class="art-text" :textColors="['#1e293b', '#334155', '#64748b']" />
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, nextTick, computed } from 'vue'
import { useRouter } from 'vue-router'
import { storeToRefs } from 'pinia'
import { useArticleStore } from '@/stores/article'
import { useConfigStore } from '@/stores/config'
import gsap from 'gsap'
import { ScrollTrigger } from 'gsap/ScrollTrigger'
gsap.registerPlugin(ScrollTrigger)

const configStore = useConfigStore()
</script>

<style scoped lang="scss">
@import url('https://fonts.googleapis.com/css2?family=Playfair+Display:ital,wght@1,600&display=swap');

.home-page {
  max-width: 800px;
  margin: 0 auto;
  padding: 40px 24px 80px;

  /* 容器居中与弹性布局 */
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 60vh;
}

.art-text {
  /* 艺术字体与大字样式 */
  font-family: 'Playfair Display', 'Georgia', serif;
  font-style: italic;
  font-size: 5rem;
  font-weight: 700;
  color: #1e293b;
  text-align: center;
  line-height: 1.2;
}
</style>