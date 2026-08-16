import { defineAsyncComponent, markRaw, type Component, shallowRef, watch } from 'vue'

// 页面注册表 —— 只有被访问过才会 import
const pageRegistry: Record<string, () => Promise<Component>> = {
  home:   () => import('../views/home/right/home.vue'),
  blog:   () => import('../views/home/right/blog.vue'),
  resume: () => import('../views/home/right/resume.vue'),
  life:   () => import('../views/home/right/life.vue'),
}

export function usePageLoader(selectedKey: Ref<string>) {
  // 已加载的组件缓存
  const cache = shallowRef<Map<string, Component>>(new Map())
  // 当前要渲染的组件
  const currentComponent = shallowRef<Component | null>(null)
  // 加载状态
  const loading = ref(false)

  // 加载某个页面（如果已缓存则直接取）
  async function loadPage(key: string) {
    if (cache.value.has(key)) {
      currentComponent.value = cache.value.get(key)!
      return
    }

    const loader = pageRegistry[key]
    if (!loader) return

    loading.value = true
    try {
      const comp = markRaw(defineAsyncComponent({
        loader,
        // 加载中的占位
        loadingComponent: undefined,
        // 加载失败
        delay: 120,         // 120ms 内加载完就不显示 loading
        timeout: 10000,
      }))
      cache.value.set(key, comp)
      currentComponent.value = comp
    } finally {
      loading.value = false
    }
  }

  // 监听选中项变化
  watch(selectedKey, (key) => {
    loadPage(key)
  }, { immediate: true })

  // 预加载：鼠标悬停时提前加载
  function preload(key: string) {
    if (!cache.value.has(key) && pageRegistry[key]) {
      const comp = markRaw(defineAsyncComponent(pageRegistry[key]))
      cache.value.set(key, comp)
    }
  }

  return { currentComponent, loading, preload }
}
