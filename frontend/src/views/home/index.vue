<template>
  <div class="app-wrapper">
    <Transition name="fade-out">
      <div v-if="showIntro" class="intro-overlay">
        <SplitText text="Hello, sps" class-name="intro-text" :delay="100" :duration="0.6" ease="power3.out"
          split-type="chars" :from="{ opacity: 0, y: 40 }" :to="{ opacity: 1, y: 0 }" :threshold="0.1"
          root-margin="-100px" text-align="center" @animation-complete="handleAnimationComplete" />
      </div>
    </Transition>

    <Transition name="slide-up-main">
      <div v-if="showMain" class="main-container">
        <el-container style="height: 100vh; overflow: hidden; position: relative;">

          <el-header class="app-header" :class="{ 'header-hidden': !showHeader }">
            <RadioGroup v-model="selectedValue" :options="numberOptions" name="my-radio" />
          </el-header>

          <el-main class="app-main">
            <div class="main-scroll" @scroll="handleScroll">
              <Transition :name="transitionName" mode="out-in">
                <div :key="selectedValue" class="page-wrapper">
                  <KeepAlive>
                    <component :is="currentPage" />
                  </KeepAlive>
                </div>
              </Transition>
            </div>
          </el-main>
        </el-container>
      </div>
    </Transition>
  </div>
</template>

<script setup lang="ts">
import { onMounted, ref, watch, computed, defineAsyncComponent } from 'vue'
import { useConfigStore } from '@/stores/config'

const configStore = useConfigStore()
configStore.fetchConfig().catch(() => { })

const INTRO_PLAYED_KEY = 'sps-bl…ayed'
const ACTIVE_TAB_KEY = 'sps-bl…-tab'

const hasPlayedIntro = sessionStorage.getItem(INTRO_PLAYED_KEY) === 'true'

const showIntro = ref(!hasPlayedIntro)
const showMain = ref(hasPlayedIntro)

const handleAnimationComplete = () => {
  console.log('All letters have animated!')
  setTimeout(() => {
    showIntro.value = false
  }, 800)
}

watch(showIntro, (val) => {
  if (!val) {
    sessionStorage.setItem(INTRO_PLAYED_KEY, 'true')
    setTimeout(() => {
      showMain.value = true
    }, 400)
  }
})

const savedTab = sessionStorage.getItem(ACTIVE_TAB_KEY)
const selectedValue = ref(savedTab || 'one')

const numberOptions = [
  { value: 'one', label: '首页' },
  { value: 'two', label: '博客' },
  { value: 'three', label: '生活' },
  { value: 'four', label: '简历' },
]

const pages: Record<string, ReturnType<typeof defineAsyncComponent>> = {
  one: defineAsyncComponent(() => import('./right/home.vue')),
  two: defineAsyncComponent(() => import('./right/blog.vue')),
  three: defineAsyncComponent(() => import('./right/life.vue')),
  four: defineAsyncComponent(() => import('./right/resume.vue')),
}

const currentPage = computed(() => pages[selectedValue.value])

const pageOrder = ['one', 'two', 'three', 'four']
const transitionName = ref('slide-up')

watch(selectedValue, (n, o) => {
  sessionStorage.setItem(ACTIVE_TAB_KEY, n)
  transitionName.value =
    pageOrder.indexOf(n) > pageOrder.indexOf(o) ? 'slide-up' : 'slide-down'
})

// === 滚动显隐控制逻辑 ===
const showHeader = ref(true)
let lastScrollTop = 0

const handleScroll = (e: Event) => {
  const target = e.target as HTMLElement
  const currentScrollTop = target.scrollTop

  // 增加 10px 缓冲阈值，防止微小抖动触发显隐
  if (Math.abs(currentScrollTop - lastScrollTop) < 10) return

  // currentScrollTop > 80 防止在最顶部时误触发隐藏
  if (currentScrollTop > lastScrollTop && currentScrollTop > 80) {
    showHeader.value = false // 向下滚动，隐藏
  } else {
    showHeader.value = true  // 向上滚动，显示
  }

  lastScrollTop = currentScrollTop <= 0 ? 0 : currentScrollTop
}
</script>

<style scoped lang="scss">
.app-wrapper {
  position: relative;
  width: 100%;
  height: 100vh;
  overflow: hidden;
}

.intro-overlay {
  position: fixed;
  inset: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  justify-content: center;
}

.intro-text {
  font-size: 5.5rem;
  font-weight: 600;
  color: #3f3f3f;
  letter-spacing: 0.02em;
}

.fade-out-leave-active {
  transition: opacity 0.4s cubic-bezier(0.55, 0, 1, 0.45);
}

.fade-out-leave-to {
  opacity: 0;
}

.slide-up-main-enter-active {
  transition: all 0.8s cubic-bezier(0.22, 1, 0.36, 1);
}

.slide-up-main-enter-from {
  opacity: 0;
  transform: translateY(60px);
}

.slide-up-main-enter-to {
  opacity: 1;
  transform: translateY(0);
}

/* Header 绝对定位及平滑显隐过渡 */
.app-header {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  z-index: 50;
  transition: transform 0.4s cubic-bezier(0.22, 1, 0.36, 1);
  display: flex;
  justify-content: center;
  align-items: center;
}

.header-hidden {
  /* 修改：使用固定像素值 -150px，确保连同 margin 和外溢的 SVG 描边一起彻底移出屏幕 */
  transform: translateY(-150px);
}

.app-main {
  height: 100vh;
  padding: 0;
}

.page-wrapper {
  height: 100%;
  min-height: 100%;
}

.main-scroll {
  height: 100%;
  box-sizing: border-box;
  /* 顶部 padding: 100px 确保内容不被 Header 遮挡 */
  padding: 100px 50px 50px 50px;
  overflow-y: auto;
  overflow-x: hidden;
  scroll-behavior: smooth;

  &::-webkit-scrollbar {
    width: 4px;
  }

  &::-webkit-scrollbar-track {
    background: transparent;
  }

  &::-webkit-scrollbar-thumb {
    background: #2a2a2a;
    border-radius: 4px;

    &:hover {
      background: #3a3a3a;
    }
  }
}

.slide-up-enter-active {
  transition: all 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}

.slide-up-leave-active {
  transition: all 0.3s cubic-bezier(0.55, 0, 1, 0.45);
}

.slide-up-enter-from {
  opacity: 0;
  transform: translateY(40px);
}

.slide-up-leave-to {
  opacity: 0;
  transform: translateY(-20px);
}

.slide-down-enter-active {
  transition: all 0.5s cubic-bezier(0.22, 1, 0.36, 1);
}

.slide-down-leave-active {
  transition: all 0.3s cubic-bezier(0.55, 0, 1, 0.45);
}

.slide-down-enter-from {
  opacity: 0;
  transform: translateY(-40px);
}

.slide-down-leave-to {
  opacity: 0;
  transform: translateY(20px);
}
</style>