import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
import path from 'path'

import Icons from 'unplugin-icons/vite'
import IconsResolver from 'unplugin-icons/resolver'
import AutoImport from 'unplugin-auto-import/vite'
import Components from 'unplugin-vue-components/vite'
import { ElementPlusResolver } from 'unplugin-vue-components/resolvers'

const pathSrc = path.resolve(__dirname, 'src')

export default defineConfig({
  plugins: [
    vue(),
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
       target: 'http://118.190.133.231:32658',
        changeOrigin: true,
      }
      // '/api': {
      //  target: 'http://localhost:8080',
      //   changeOrigin: true,
      // }
    }
  },build: {
    outDir: 'dist',  // 打包输出目录
    assetsDir: 'assets',  // 静态资源目录
    sourcemap: false,  // 生产环境不生成 sourcemap
    minify: 'terser',  // 代码压缩
    terserOptions: {
      compress: {
        drop_console: true,  // 打包时去掉 console.log
        drop_debugger: true  // 去掉 debugger
      }
    }
  }
})