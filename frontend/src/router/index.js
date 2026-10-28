import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    redirect: '/home'
  },
  {
    path: '/home',
    name: 'Home',
    component: () => import('@/views/home/index.vue')
  },
{
  path: '/article/:id',
  name: 'ArticleBrowse',
  component: () => import('@/views/home/blog/index.vue'),
  meta: { title: '文章详情' }
},{
    path: '/admin/write',
    name: 'WriteArticle',
    component: () => import('@/views/admin/articles/write.vue'),
    meta: { title: '撰写文章', requiresAuth: true }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue')
  },

  {
    path: '/admin',
    name: 'AdminLayout',
    component: () => import('@/views/admin/Layout.vue'),
    redirect: '/admin/articles',
    children: [
      {
        path: 'users',
        name: 'AdminUsers',
        component: () => import('@/views/admin/users/index.vue')
      },
      {
        path: 'articles',
        name: 'AdminArticles',
        component: () => import('@/views/admin/articles/index.vue')
      },
      {
        path: 'categories',
        name: 'AdminCategories',
        component: () => import('@/views/admin/categories/index.vue')
      },
      {
        path: 'tags',
        name: 'AdminTags',
        component: () => import('@/views/admin/tags/index.vue')
      },
      {
        path: 'comments',
        name: 'AdminComments',
        component: () => import('@/views/admin/comments/index.vue')
      },
      {
        path: 'links',
        name: 'AdminLinks',
        component: () => import('@/views/admin/links/index.vue')
      },
      {
        path: 'picture',
        name: 'picture',
        component: () => import('@/views/admin/tools/picture.vue')
      },
      {
        path: 'agnes-video',
        name: 'AgnesVideo',
        component: () => import('@/views/admin/tools/agnesVideo.vue'),
        meta: { title: 'Agnes 视频生成' }
      },
      {
        path: 'agnes-image',
        name: 'AgnesImage',
        component: () => import('@/views/admin/tools/agnesImage.vue'),
        meta: { title: 'Agnes 图片生成' }
      },
    
      {
        path: 'configs',
        name: 'AdminConfigs',
        component: () => import('@/views/admin/configs/index.vue')
      },
      {
        path: 'ai-model',
        name: 'AdminAiModel',
        component: () => import('@/views/admin/configs/aiModel.vue'),
        meta: { title: 'AI 对话配置' }
      },
      {
        path: 'memory',
        name: 'AdminMemory',
        component: () => import('@/views/admin/memory/index.vue'),
        meta: { title: '记忆管理' }
      },
      {
        path: 'ideas',
        name: 'AdminIdeas',
        component: () => import('@/views/admin/ideas/index.vue'),
        meta: { title: '念生 · 想法' }
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from) => {
  const token = localStorage.getItem('token')
  const role = localStorage.getItem('role')

  // 未登录访问需要权限的页面
  if (to.path.startsWith('/admin') && !token) {
    return {
      path: '/login',
      query: { redirect: to.fullPath }
    }
  }

  // 已登录但权限不足访问后台
  if (to.path.startsWith('/admin') && token && role !== 'ADMIN') {
    return '/'
  }

  // 已登录访问登录/注册页 → 跳转首页
  if ((to.path === '/login' || to.path === '/register') && token) {
    return role === 'ADMIN' ? '/admin' : '/'
  }

  // 根路径分流（可选）
  if (to.path === '/' && token && role === 'ADMIN') {
    return '/admin'
  }

  // 放行
  return true
})
export default router