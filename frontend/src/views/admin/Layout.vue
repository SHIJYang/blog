<template>
  <el-container class="admin-layout-container">
    <!-- 侧边栏导航 -->
    <el-aside width="200px" class="layout-aside">
      <div class="sidebar-logo">
        <span>博客管理系统</span>
      </div>

      <el-menu :default-active="activeMenu" class="sidebar-menu" router>
        <el-menu-item index="/admin/articles">
          <el-icon>
            <Document />
          </el-icon>
          <span>文章管理</span>
        </el-menu-item>

        <el-menu-item index="/admin/categories">
          <el-icon>
            <Collection />
          </el-icon>
          <span>分类管理</span>
        </el-menu-item>

        <el-menu-item index="/admin/tags">
          <el-icon>
            <PriceTag />
          </el-icon>
          <span>标签管理</span>
        </el-menu-item>

        <el-menu-item index="/admin/comments">
          <el-icon>
            <ChatDotRound />
          </el-icon>
          <span>评论管理</span>
        </el-menu-item>

        <el-menu-item index="/admin/links">
          <el-icon>
            <Link />
          </el-icon>
          <span>友链管理</span>
        </el-menu-item>

        <el-menu-item index="/admin/users">
          <el-icon>
            <User />
          </el-icon>
          <span>用户管理</span>
        </el-menu-item>

        <el-sub-menu index="tools" title="工具">
          <template #title>
            <el-icon>
              <Tools />
            </el-icon>
            <span>工具</span>
          </template>

          <el-menu-item index="picture">在线图片链接</el-menu-item>

          <el-menu-item index="agnes-video">Agnes 视频生成</el-menu-item>

          <el-menu-item index="agnes-image">Agnes 图片生成</el-menu-item>


        </el-sub-menu>

        <el-menu-item index="/admin/configs">
          <el-icon>
            <Setting />
          </el-icon>
          <span>系统配置</span>
        </el-menu-item>

        <el-menu-item index="/admin/ai-model">
          <el-icon>
            <Platform />
          </el-icon>
          <span>AI 对话配置</span>
        </el-menu-item>

        <el-menu-item index="/admin/memory">
          <el-icon>
            <Memo />
          </el-icon>
          <span>记忆管理</span>
        </el-menu-item>

        <el-menu-item index="/admin/ideas">
          <el-icon>
            <MagicStick />
          </el-icon>
          <span>念生 · 想法</span>
        </el-menu-item>


      </el-menu>
      <el-button @click="handleLogout">退出登录</el-button>
    </el-aside>



    <!-- 主要内容渲染区 -->
    <el-main class="layout-main">
      <router-view />
    </el-main>

  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import {
  User,
  Document,
  Collection,
  PriceTag,
  ChatDotRound,
  Link,
  Tools,
  Setting,
  Memo,
  Platform,
  MagicStick
} from '@element-plus/icons-vue'

const router = useRouter()
const route = useRoute()
const authStore = useAuthStore()

// 侧边栏高亮菜单计算
const activeMenu = computed(() => {
  return route.path
})

// 下拉菜单操作
const handleCommand = (command) => {
  if (command === 'logout') {
    handleLogout()
  }
}

// 登出逻辑
const handleLogout = () => {
  ElMessageBox.confirm('确定要退出当前账号并返回登录页吗？', '安全登出提示', {
    confirmButtonText: '确定',
    cancelButtonText: '取消',
    type: 'warning'
  }).then(() => {
    authStore.logout()
    ElMessage.success('登出成功')
    router.push('/login')
  }).catch(() => { })
}
</script>

<style lang="scss" scoped>
.admin-layout-container {
  height: 100vh;
  width: 100vw;
  overflow: hidden;

  .layout-aside {

    display: flex;
    flex-direction: column;

    .sidebar-logo {
      height: 60px;
      line-height: 60px;
      text-align: center;
      font-size: 1.125rem;
      font-weight: bold;

    }

    .sidebar-menu {
      border-right: none;
      flex: 0.8;
    }
  }

  .main-container {
    display: flex;
    flex-direction: column;
    height: 100%;
  }

  .layout-header {
    height: 60px;
    background-color: #fff;
    border-bottom: 1px solid var(--el-border-color-light);
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding: 0 20px;

    .header-left {
      font-size: 0.875rem;
      color: var(--el-text-color-secondary);
    }

    .user-dropdown-link {
      cursor: pointer;
      color: var(--el-color-primary);
      display: flex;
      align-items: center;
      font-size: 0.938rem;
    }
  }

  .layout-main {
    background-color: var(--el-fill-color-blank);
    padding: 20px;
    overflow-y: auto;
    flex: 1;
  }
}
</style>