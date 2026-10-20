import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'
import { fileURLToPath, URL } from 'node:url'
import path from 'path'

import Icons from 'unplugin-icons/vite'
import IconsResolver from 'unplugin-icons/resolver'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'
import { compression } from 'vite-plugin-compression2'

const pathSrc = path.resolve(__dirname, 'src')

export default defineConfig(({ command, mode }) => {
  // 加载 .env / .env.[mode] 等环境变量；默认只返回 VITE_ 前缀的变量
  const env = loadEnv(mode, process.cwd())

  // /api 代理目标：默认远程后端，可在 .env.development 覆盖为 http://localhost:8080
  const apiTarget = env.VITE_API_TARGET || 'http://118.190.133.231:32658'

  const isDev = mode === 'development'
  const isBuild = command === 'build' && mode === 'production'

  return {
    plugins: [
      vue(),
      // 仅开发模式注册 Vue DevTools，不进入生产产物
      ...(isDev ? [vueDevTools()] : []),
      AutoImport({
        imports: ['vue'],
        resolvers: [
          ElementPlusResolver(),
          IconsResolver({
            prefix: 'Icon',
          }),
        ],
        dts: path.resolve(pathSrc, 'auto-imports.d.ts'),
      }),
      Components({
        resolvers: [
          IconsResolver({
            enabledCollections: ['ep'],
          }),
          ElementPlusResolver(),
        ],
        dts: path.resolve(pathSrc, 'components.d.ts'),
      }),
      Icons({
        autoInstall: true,
      }),
      // 仅生产构建启用 gzip 预压缩，产出 .gz 供 nginx gzip_static 直接返回
      ...(isBuild
        ? [
            compression({
              algorithm: 'gzip',
              include: /\.(html|txt|css|js|mjs|json|svg)$/i,
              threshold: 10240, // 小于 10KB 的文件不预压缩
            }),
          ]
        : []),
    ],
    resolve: {
      alias: {
        '@': fileURLToPath(new URL('./src', import.meta.url))
      }
    },
    server: {
      host: '0.0.0.0',
      port: 5173,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
        }
        // 本地后端备选（改 .env.development 的 VITE_API_TARGET 即可切换）：
        // target: 'http://localhost:8080',
      }
    },
    // npm run preview 本地验证生产构建时使用同款代理
    preview: {
      host: '0.0.0.0',
      port: 4173,
      proxy: {
        '/api': {
          target: apiTarget,
          changeOrigin: true,
        }
      }
    },
    build: {
      outDir: 'dist',  // 打包输出目录
      assetsDir: 'assets',  // 静态资源目录
      sourcemap: false,  // 生产环境不生成 sourcemap
      minify: 'terser',  // 代码压缩
      terserOptions: {
        compress: {
          drop_console: true,  // 打包时去掉 console.log
          drop_debugger: true  // 去掉 debugger
        }
      },
      // 分包后单 chunk 仍可能超过默认 500kB 警告线；aieditor 编辑器库约 1.8MB（仅写文章页按需加载），放宽到 2000
      chunkSizeWarningLimit: 2000,
      rollupOptions: {
        output: {
          // 函数形式分包：element-plus / vue 运行时 / aieditor 各自独立 chunk
          manualChunks(id) {
            if (!id.includes('node_modules')) return
            if (
              id.includes('element-plus') ||
              id.includes('@element-plus/icons-vue') ||
              id.includes('@popperjs') ||
              id.includes('async-validator')
            ) {
              return 'element-plus'
            }
            if (id.includes('aieditor')) {
              return 'aieditor'
            }
            if (
              id.includes('/vue/') ||
              id.includes('/vue-router/') ||
              id.includes('/pinia') ||
              id.includes('@vue/')
            ) {
              return 'vue-vendor'
            }
          },
        },
      },
    },
  }
})
