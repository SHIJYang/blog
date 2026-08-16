# Blog 项目

个人博客系统，前后端分离。

## 仓库结构

```
blog/
├── frontend/   # Vue3 前端（Vite + Element Plus + Tiptap + GSAP）
└── backend/    # Spring Boot 后端（MyBatis-Plus + Spring Security + JWT）
```

## 分支说明

- `main` — 前端代码（Vue3）
- `backend` — 后端代码（Spring Boot 4.0）

## 技术栈

- **前端**：Vue3 + Vite + Pinia + Element Plus + Tiptap + GSAP
- **后端**：Spring Boot 4.0 + MyBatis-Plus + Spring Security + JWT + MySQL

## 运行

### 后端
```bash
cd backend
# 配置 application-local.yml 中的数据库密码和 JWT secret
# 然后直接 IDEA 运行 BlogApplication.java
```

### 前端
```bash
cd frontend
npm install
npm run dev
```
