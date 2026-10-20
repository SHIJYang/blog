# 项目长期记忆（blog frontend）

## 项目概况
- Vue 3.5 + Vite 8 博客 SPA（前台 + Element Plus 管理后台），站点名「蒜泡水」（后端 /api/public/configs/info 的 blogname）
- 技术栈：vue-router 5（懒加载）、Pinia、axios（src/utils/request.js 统一封装，baseURL 走 VITE_API_BASE）、unplugin-icons/auto-import/components、sass

## 环境约定（2026-10-20 建立）
- 开发代理默认指向**远程后端** `http://118.190.133.231:32658`；切本地后端改 `.env.development` 的 `VITE_API_TARGET=http://localhost:8080` 后重启 dev；私有覆盖用 `.env.local`（git 忽略，优先级最高）
- 生产部署：Nginx 同域反代 `/api`（模板 `nginx.conf.example`），前端 baseURL 保持 `/api`；未来前后端分离只改 `.env.production` 的 `VITE_API_BASE`
- `.env.development` / `.env.production` 已在 .gitignore 白名单（不含密钥，可提交）
- 生产构建：terser 去 console、gzip 预压缩（vite-plugin-compression2，仅 build+production）、分包 element-plus/vue-vendor/aieditor
- vite.config.js 为函数形式 defineConfig + loadEnv，改配置时注意 command/mode 分支

## 注意事项
- unplugin 会在 dev/build 时重建 `src/auto-imports.d.ts`、`src/components.d.ts`，产生 LF/CRLF 行尾噪音，`git checkout --` 还原即可，内容无变化
- gzip 插件若构建报错：卸载 + 删 vite.config.js 中 compression 段，走 nginx 动态 gzip 降级
- Agnes AI 密钥硬编码待轮换（见 2026-10-20 日志）
