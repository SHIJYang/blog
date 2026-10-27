# SHIJYang/blog 升级调研报告

> 调研时间：2026-10-27（**v5 · 数据库结论回正，MySQL 默认线**）　｜　对象：GitHub `SHIJYang/blog`（本地 `D:/sjy/blog` 同源）
> 方式：只读。`git ls-tree` / `git show` / `git merge-tree` 逐分支读取 + `SHOW CREATE TABLE` 原文 + 生产域名只读探测。**未改任何分支、未提交。**
> 标注：`来源：xxx` 可验证；`推断` 无直接证据；`待确认` 需你补充。
> v4 变更：D1~D16 全部决策齐备，§7/§8/§9/§10 按最终决策重排。
> **v5 变更（重要）**：**撤回 v3 的"迁 PostgreSQL"结论**——复核后发现"迁移变便宜"不等于"必须迁移"，MySQL 8.0.40 具备除"向量 ANN / RLS"外的全部等价能力（§8.2 逐条对照）。默认路线恢复为**留 MySQL + BLOB 向量 + Repository 层隐私拦截**（§8.5），PG 降级为有触发条件的备选（§8.4 T1~T4）。另补 §2.4 回答"分分支行不行"。

---

## 0. 决策记录（2026-10-27 用户确认）

### 0.1 已定项

| # | 决策 | 对方案的影响 |
|---|---|---|
| D1 | **MySQL 8.0.40** | 8.0 系列**无 `VECTOR` 类型**（9.0 才引入）→ "MySQL 原生向量"路线彻底出局 |
| D2 | **封存 = 连本人也不能随时看到**，需二次验证解锁 | 比 DDL 注释的"仅禁止 AI 提及"严格得多。新增：PIN 哈希、失败锁定、解锁审计、列表默认排除 |
| D3 | **公开 = 进博客公开列表** | `privacy=0` 必须有真实出口：落成 `blog_article(status=1)`，不能只改一个字段 |
| D4 | **多用户注册**（保留 ADMIN/AUTHOR/READER） | 隔离、越权、配额、审计全部不能砍 |
| D5 | 历史图床图片**接受"已公开"既成事实** | 不做回滚迁移；只对**新写入**的私密照片启用受控访问 |
| D6 | **允许直接重建任何表、任何文件** | ⭐ 最关键的授权：schema 可以按目标语义重设计，迁移退化为"重建 + 回填 ~100 行" |
| D7 | `addtoblog`（Flutter 发布器）**保留** | 拾光是**另一个独立 Flutter App**，不改造发布器 |
| D8 | 念生状态机 **≈ 6 态** | 按 6 态设计：种子/发酵/验证/行动/结晶/休眠 |
| D9 | 旧 Android **没有真实用户** | 可直接下线，无需灰度期与迁移引导 |
| D10 | **不需要多端实时同步** | 不上 WS/SSE，REST + 轮询即可 |
| D11 | 封存解锁用**全局单一 PIN**（非每条独立） | `sys_user.privacy_pin_hash` 一份足够；解锁令牌为**用户级**，`pin_updated_at` 作吊销水位 |
| D12 | **设 `privacy=0` 即发博客**（无二次动作） | 省一步交互，但误发风险上升 → 靠 UI 二次确认 + 5 分钟撤回窗口兜（见 R2） |
| D13 | 改记忆正文**同步更新**已发布文章 | 确立"记忆是源、文章是投影"模型：需 `source_rev` 水位 + `manual_edited` 断点，避免覆盖人工润色 |
| D14 | 注册用户**可读自己的全部 + 他人的 `privacy=0`** | 新增公开读面：`/api/public/memories`；`memory_note` 需**跨用户索引**（不能带 `user_id` 左前缀） |
| D15 | 后端**确认继续 Spring Boot**；拾光与念生**均为新建 Flutter** | 116 个 Java 文件是资产不是负担；`addtoblog` 保持原样，拾光/念生另起工程 |
| D16 | **不做导出、不做推送、本期不考虑线上部署** | 砍掉 `export` 接口、FCM/APNs、compose 生产编排、双写/灰度/回滚 30 天；第 0 阶段只保证**本地开发库**可一键起 |

### 0.2 结论修正（诚实记录我改了主意的地方）

| 项 | v2 结论 | v3 结论 | 为什么改 |
|---|---|---|---|
| 数据库（v2→v3） | **不迁**，先补 DDL + 抽接口 | 曾改为"直接落 PostgreSQL 16 + pgvector" | 当时理由：D6 让"迁移变便宜"。**事后判定为推理错误——把成本门槛消失当成了必要性成立** |
| 数据库（v3→v5，最终） | 曾推荐迁 PG | **不迁。留 MySQL 8.0.40**；PG 降级为"触发 T1~T4 才执行"的备选 | 逐条复核 §8.2：`pg_trgm`/`text[]`/`WITH RECURSIVE`/`jsonb`/`CHECK` 在 MySQL 8.0 全有等价物，**PG 真正独有的只剩"向量 ANN"与"RLS"**；RLS 表达不了 D11 的"需 PIN 解锁"（仍靠应用层），向量在 38→5000 条规模用 BLOB+缓存足够 |
| 迁移成本 | 高（3~5 人周） | **低（1~1.5 人周）** | 授权重建表 → 不必反推 14 张表的怪癖、不必逐表 ALTER、不必双写过渡 |
| 封存语义 | "AI 不提及但本人可见"（按 DDL 注释） | **"本人也需 PIN 解锁"** | D2 明确 |
| 公开语义 | "AI 可随意召回" | **"进博客公开列表，且注册用户可读他人公开记忆"** | D3 + D14 明确，需新增两个数据出口（文章投影 + 公开读接口） |
| 记忆与文章的关系 | 未定义（原 `publish` 是单向一次性复制） | **投影模型**：记忆是源，文章是投影，改记忆则同步改文章 | D13 明确；原实现是"发布后各自独立"，会产生两份真相 |

---

## 前置摘要（v5 最终版）

1. **数据量极小**（4 用户 / 18 文章 / 38 记忆 / 50 消息 / 89 闪光点），全部数据一次 `COPY` 就能搬完。**"允许重建 + 数据极少"是本项目最大的自由度，应该现在用掉，而不是留着。**
2. **不需要 PostgreSQL**（v5 修正）：MySQL 8.0.40 已具备 ngram 全文、JSON + 多值索引、递归 CTE、强制 CHECK；PG 独有的只剩"向量 ANN"和"RLS"——RLS 表达不了"需 PIN 解锁"，向量在 5000 条规模内用 BLOB + 内存缓存足够。**留 MySQL，把 PG 写成有触发条件的备选。**
3. **schema 需要重设计而非照搬**：`D2` 要求"本人也看不到"、`D3` 要求"公开有真实出口"，这两条现有表结构根本表达不了（`privacy` 只是一个 tinyint，无 PIN、无 `sealed_at`、无审计、无 photo 实体）。**已产出草案 `backend/src/main/resources/db/migration/V1__baseline.sql`（25 表）。**
4. **代码资产几乎全部可留**：`MemoryVectorService` 的三通道打分、`AgnesAiClient`、`MemoryPublishService`、JWT/Security 都不依赖 MySQL 方言；要改的是**取数方式**（`selectByUser` 拉全量 JSON → SQL 预过滤 + BLOB + 缓存），不是业务逻辑。

---

## 1. 仓库访问结论

**仓库 public，全部分支可读，无需你补充权限。**

| 项 | 值 | 来源 |
|---|---|---|
| 仓库 / 默认分支 | `SHIJYang/blog` / `main` | GitHub API |
| 可见性 | `public`（`private: false`） | 同上 |
| 创建 / 最后推送 | 2026-08-13 / 2026-10-23T06:48:47Z | 同上 |
| main 提交数 / 体积 | 5 / 1587 KB | `git rev-list --count` |
| 主语言 | Vue 350 KB、JS 45 KB、SCSS、TS | GitHub API `/languages` |
| LICENSE / Tag | **无** / **0 个** | GitHub API |
| 分支总数 | **4**（main, backend, flutter, memory） | `git branch -r` |

> 本机 `git clone` 走 HTTPS 被代理拦（`CONNECT tunnel failed 502`），但 REST API + 本地 `.git` 已有全部远程对象，**分支内容是直读，非推断**。

### 1.1 生产环境实测（v2 新增，只读 GET，未做任何写操作）

| 探测 | 结果 | 说明 |
|---|---|---|
| `GET https://api.qisont.art/api/public/configs/info` | **200**，164ms | 返回 `blogname: 蒜泡水`、`blogavatar: https://free.picui.cn/...`，`message: 操作成功` |
| `GET /api/public/articles/page?size=2` | **200**，`pages: 6` | 按 size=2 推算约 **11–12 篇已发布文章**（与表 `AUTO_INCREMENT=19` 吻合） |
| `GET /api/memory/list`（无 token） | **403** | 鉴权边界有效，`anyRequest().authenticated()` 生效 |
| `GET /v3/api-docs` | **404** | nginx 仅反代 `/api/`，Swagger **未对外暴露**（正面） |

**结论**：`api.qisont.art` 是当前生产域名，`118.190.133.231:32658` 是同一后端的源站 IP（Vue/Android 代码里写的是 IP）。**两者不是两套环境，是同一套**——v1 报告里"两套后端并存"的风险项降级为"域名收口"的整洁性工作。

---

## 2. 分支清单与基线建议

### 2.1 分支清单

| 分支 | 默认 | 最近提交 | 提交数 | 文件数 | 实质内容 | 与 main 差异 |
|---|---|---|---|---|---|---|
| `main` | ✅ | `7084897` 2026-10-23（消息仅 `1`） | 5 | 87 | **仅 `frontend/`**（Vue3 SPA） | — |
| `backend` | | `544d0c4` 2026-08-16（`clean backend branch`） | 4 | 123 | **仅 `backend/`**（116 个 .java） | main 领先 4 / backend 领先 3 |
| `flutter` | | `979f5a1` 2026-10-19 | 3 | 162 | **`addtoblog` Markdown 发布器**（桌面优先） | 与 main **无共同提交**（独立历史） |
| `memory` | | `cb61f06` 2026-08-16 | 4 | 93 | **旧 Android 客户端**（Kotlin，Chat/Gallery/Dock/SphereMenu） | main 领先 3 / memory 领先 2 |

**分支差异的本质**：不是版本演进，是**按目录切片**。`main` 的提交 `3392638c chore: keep only frontend on main branch` 删掉了 backend 目录；`backend` 的 `544d0c4` 删掉了 frontend。两者同源 `e4b5400c`（`feat: init blog project (frontend Vue3 + backend Spring Boot 4.0)`），**互不包含，必须合并才有完整系统**。

另外两个反常识点：
- `backend` 最后一次提交停在 **2026-08-16**，比 main 落后 2 个月。但 main 的 3 次更新（环境变量、Nginx、vite 动态代理）**全部是前端**，所以后端没丢更新，只是**自 8 月起再没迭代过**。`来源：git log --date=short`
- `flutter` 分支**不是拾光客户端**，是一个拖 Markdown 发博客的桌面工具。`来源：origin/flutter:README.md`、`api_constants.dart` 仅含 `login` 与 `adminArticles` 两个端点

### 2.2 合并可行性（只读实测）

`git merge-tree --write-tree origin/main origin/backend`：**19 处冲突，100% 是 `modify/delete` 类型，零文本冲突**。每处都是"一侧删了整个目录、另一侧改了里面文件"，解决方式＝保留两侧现存版本。

### 2.3 基线建议

| 候选 | 结论 | 理由 |
|---|---|---|
| 直接用 `main` | ❌ | 无后端 |
| 直接用 `backend` | ❌ | 无前端，且其 `frontend/views/admin/memory/index.vue` 是旧版 |
| **新建 `upgrade/v2` = merge(main, backend)** | ✅ **推荐** | 一次拿到最新前端 + 全部后端；冲突可脚本化；保留双历史 |
| 并入 `flutter` | ⚠️ 暂不 | 与拾光无共同代码/历史，硬并污染基线。先决策"保留为发布器 / 废弃重写" |
| 并入 `memory`（Android） | ❌ 不并 | 你已定"逐步下线"；建议标记 `archived`，作 UI 参考（`SphereMenuView`、`ParticleView` 有产品价值） |

```bash
# 第 0 阶段第一条命令
git checkout -b upgrade/v2 origin/main
git merge origin/backend     # 19 处 modify/delete，全部保留两侧现存版本
```

### 2.4 「前后端放两个分支不行吗？」——行，但先看清代价

**技术上完全可行，没有任何阻塞。** 前后端分仓/分分支是主流做法，前提是：两个团队、独立发版节奏、契约稳定。你这个项目的画像恰好相反。

| 分分支的代价 | 这个项目里的具体表现 | 是"理论风险"还是"已经发生" |
|---|---|---|
| **跨端改动无法原子化** | 加"公开即发博客"要同时改 `MemoryController`（后端）与 `admin/memory/index.vue`（前端）。两个分支 = 两个永远不能一起 `revert` 的提交；出问题无法单次回滚 | 即将发生 |
| **契约漂移无人拦** | `docs/legacy/api-2026-08.openapi.json` 停在 **50 端点**，后端实际 **72 端点**——已经漂了 22 个，且漂了两个月没人发现 | **已经发生** |
| **配对靠人脑记** | 零 tag、零 release。部署时要凭记忆确认"今天的前端配哪个后端 commit"。错配的表现是线上 404 | 已具备条件 |
| **一侧停了另一侧不知道** | `backend` 自 2026-08-16 起零提交，而 `main` 又跑了 3 个提交、2 个月。没人被提醒 | **已经发生** |
| **无法整体 bisect** | 契约类回归无法用 `git bisect` 定位（前端一个分支、后端一个分支，二分要人肉跳） | 理论 |

**而合并的代价我实测过：19 处冲突、全是 `modify/delete`、零文本冲突，30 分钟能合完（§2.2）。**

> 结论：这里不存在"该不该分开"的架构问题，只是**成本不对称**——现在合 30 分钟，分开则每次跨端改动都要付一次"双份提交 + 人工配对"。**单人、单机、同一台服务器发版**的场景下，合并明显划算。

**如果你仍然想分分支，这不是错，但必须补三条**（否则不如合）：
1. **契约单一真源**：`contracts/openapi.json` 放中立位置（独立小仓或 `main` 的 `contracts/`），后端起服务生成、前端用它生成客户端，禁止手改；
2. **CI 双向校验**：任一侧改动后，跑"契约是否仍匹配"检查（`openapi-diff`），不匹配就红灯；
3. **版本配对表**：每个 release 打**同名 tag**（`v2.0.0` 同时打在前端与后端），`RELEASES.md` 记录配对，部署脚本读 tag 而不读分支 HEAD。

这三条加起来的工作量，已经超过合并本身。**所以我的建议仍是合并，但这是偏好而非对错**——你坚持分开，按上面三条做，方案其余部分（§8/§9）一个字都不用改。

> 合并后根 `README.md` 会说"main=前端/backend=后端"，该说法失效，必须重写。

---

## 3. 技术栈与依赖清单

### 3.1 前端（`main`，`frontend/`）

| 类别 | 选型 | 版本 | 来源 |
|---|---|---|---|
| 框架 | Vue | ^3.5.32（Composition API） | `frontend/package.json` |
| 构建 | Vite | ^8.0.8（函数式 `defineConfig` + `loadEnv`） | `package.json`、`vite.config.js` |
| UI | Element Plus | ^2.14.1 | `package.json` |
| 路由 | vue-router | **^5.0.4**，history 模式，全懒加载 | `src/router/index.js` |
| 状态 | Pinia ^3.0.4 + persistedstate ^4.7.1 | — | `package.json` |
| 请求 | axios ^1.15.2，统一封装 | `src/utils/request.js`：baseURL=`VITE_API_BASE\|\|'/api'`、10s 超时、Bearer 注入、`code!==200` 统一弹错+401/403 清 token 跳登录 | 同 |
| 富文本 | aieditor ^1.4.2（约 1.8MB，仅写作页） | — | `package.json`、`vite.config.js` 注释 |
| Markdown | markdown-it ^15.0.3 + 自研 `utils/markdown.js`(345) | — | `package.json` |
| 动效 | gsap ^3.15.0 / lucide-vue-next ^0.577.0 | — | `package.json` |
| 样式 | sass ^1.88.0 | — | `package.json` |
| 工程化 | unplugin-auto-import / components / icons、terser、compression2(gzip) | — | `vite.config.js` |
| Node | `^20.19.0 \|\| >=22.12.0` | — | `engines` |
| TS | **基本未用**（仅 1 个 `.ts`） | — | 目录扫描 |
| 测试 | **无** | — | `scripts` 仅 dev/build/preview |

### 3.2 后端（`backend`，`backend/`）

| 类别 | 选型 | 版本 | 来源 |
|---|---|---|---|
| 语言 | Java | **21** | `pom.xml` |
| 框架 | Spring Boot | **4.0.6**（非 3.x，生态较新） | `pom.xml` parent |
| Web | spring-boot-starter-web（Servlet MVC） | — | `pom.xml` |
| ORM | MyBatis-Plus | 3.5.15（`mybatis-plus-spring-boot4-starter` + `jsqlparser`） | `pom.xml` |
| 分页 | PageHelper 4.1.0（**`helper-dialect: mysql` 硬编码**） | — | `application.yml` |
| 安全 | Spring Security + JJWT 0.13.0 + BCrypt | — | `pom.xml`、`SecurityConfig` |
| 认证 | **JWT 无状态**，7 天（`jwt.expiration: 604800`），`Authorization: Bearer` | — | `SecurityConfig`、`application.yml` |
| API 风格 | RESTful + 统一 `Result{code,message,data}` + DTO/VO + `@Valid` + SpringDoc 2.8.9 | — | `common/Result.java`、`dto/`、`vo/` |
| AI 对话 | 自研 `AgnesAiClient`（OpenAI 兼容），多供应商热切换 | — | `service/ai/*` |
| AI 向量 | SiliconFlow `BAAI/bge-m3`，**1024 维** | — | `application.yml` |
| 其它 | Jsoup 1.18.3、ZXing 3.5.3、Commons-Lang3/IO、Lombok | — | `pom.xml` |
| 限流 | 自研 AOP `@AiRateLimit`（10 次/分） | — | `aspect/AiRateLimitAspect.java` |
| Redis | **未接入**（README 写"待接入"，pom 无依赖） | — | `backend/README.md` |

### 3.3 数据库

| 项 | 值 | 来源 |
|---|---|---|
| 类型 / 驱动 | MySQL / `com.mysql.cj.jdbc.Driver` | `application.yml` |
| **版本** | **MySQL 8.0.40**（用户确认，D1） | 与 DDL 证据一致：全库 `COLLATE=utf8mb4_0900_ai_ci` 是 MySQL 8.0 引入的默认排序规则 |
| 字符集 | 全库 `utf8mb4` | 16 张表 DDL 一致 |
| 存储引擎 | 全库 `InnoDB` | 同上 |
| **关键推论** | **MySQL 8.0.40 无原生 `VECTOR` 类型**（9.0 才引入），且 8.0 系列不会有。故"保留 MySQL 但用原生向量列"这条路**在本版本下不成立**，只能继续用 `longtext` 存 JSON | 见 §8 |
| 连接配置 | 不在仓库（`application-local.yml` 未提交） | `application.yml` 注释 |
| 迁移工具 | **无 Flyway / Liquibase**，`resources/sql/` 仅 2 个手工 SQL | `git ls-tree`、pom 扫描 |

### 3.4 部署

| 项 | 现状 | 来源 |
|---|---|---|
| 前端 | Nginx 静态托管 `dist/` + 同域反代 `/api` | `nginx.conf.example` |
| 后端 | 单机进程 `:8080`，公网经 `api.qisont.art`，源站 `118.190.133.231:32658` | `application.yml`、生产探测 |
| 容器 / CI | **均无**（4 分支 grep `docker\|compose\|.github` 零命中） | 全分支 |
| 图片 | `free.picui.cn` 公网图床（Token 存 `blog_config.picui_token`，相册 ID 写死 `68`）+ 本地 `uploads/memory` | `PicuiClient.java`、生产返回的 avatar URL |
| 缓存策略 | `index.html` no-cache；`/assets/*` 30 天 immutable；`gzip_static on` | `nginx.conf.example` |
| Swagger | 生产不可达（404），仅本地 dev 可用 | 生产探测 |

---

## 4. 数据库现状（16 表 / 字段 / 索引 / 迁移方式）

> **本节已由生产 `SHOW CREATE TABLE` 校正，不再有"未获取"项。** 干净可执行版本见 `docs/legacy/schema-baseline.mysql.sql`（16 表、结构零改动、修复了 `memory_*` 5 张表的中文注释乱码）。

### 4.1 表清单（16 张，全部 InnoDB / utf8mb4_0900_ai_ci）

| # | 表 | 域 | 行数量级（`AUTO_INCREMENT` 反推） | 索引 | 明显问题 |
|---|---|---|---|---|---|
| 1 | `sys_user` | 认证 | **4** | PK、`uk_username`、`uk_email` | — |
| 2 | `blog_article` | 博客 | **18** | PK、`idx_author_id` | 缺 `(status, create_time)`，公开列表排序无索引（数据小，暂无感） |
| 3 | `blog_category` | 博客 | 2 | PK、`uk_name` | — |
| 4 | `blog_tag` | 博客 | 1 | PK、`uk_name` | — |
| 5 | `blog_article_category` | 博客 | 少量 | 复合 PK | 无 FK |
| 6 | `blog_article_tag` | 博客 | 少量 | 复合 PK | 无 FK |
| 7 | `blog_comment` | 博客 | **3** | PK、`idx_article_id`、`idx_root_id` | — |
| 8 | `blog_link` | 博客 | 少量 | PK | — |
| 9 | `blog_config` | 系统 | 9 | PK、`uk_config_key` | 存 `picui_token`、`siliconflow_api_key` **明文** |
| 10 | `ai_provider` | AI | **3** | PK、`idx_active` | `api_key` **明文** |
| 11 | `memory_note` | 拾光 | **38** | PK、`idx_user_id`、`idx_status`、`idx_user_create(user_id,create_time)` | **无 `privacy` 索引**、**无 `finish_time` 索引**；`idx_user_id` 被 `idx_user_create` 前缀覆盖（冗余） |
| 12 | `memory_message` | 拾光 | **50** | PK、`idx_memory_id`、`idx_user_memory` | `idx_user_memory` 与 `idx_memory_id` 功能重叠 |
| 13 | `memory_embedding` | 拾光 | **~2962**（⚠ 见 4.4） | PK、`uk_user_memory(user_id,memory_id)`、`idx_vector_key` | **无 `privacy`/`emotion` 索引**；唯一键导致重算向量必须 delete+insert |
| 14 | `memory_emotion` | 拾光 | **~18700**（⚠ 见 4.4） | PK、`uk_user_emotion(user_id,emotion)`、`idx_user_count` | `count` 字段存在**累加 bug**（见 4.4） |
| 15 | `memory_insight` | 拾光 | **89** | PK、`idx_user_memory`、`idx_user`、`idx_memory` | **无 `is_deleted`**（硬删除，与全库软删风格不一致） |
| 16 | `memory_user_stat` | 拾光 | 3 | PK、`uk_user_id` | — |

### 4.2 关键字段（对产品语义有直接影响）

**`memory_note`（拾光主表）**
```sql
user_id bigint NOT NULL                 -- 隔离主键
cover_photo varchar(500) DEFAULT ''     -- 图床 URL
final_diary longtext                    -- 凝聚后的日记正文
status tinyint DEFAULT 1                -- 1未开始 2对话中 3已成念 4已废弃
privacy tinyint DEFAULT 1               -- 0公开 1私密 2封存
emotions varchar(255)                   -- 逗号分隔情绪标签
published_article_id bigint NULL        -- 发布幂等锚点
finish_time datetime NULL               -- 凝聚完成时间（时光轴应依赖它，但无索引）
```

**`blog_article.status`（本次最重要的"意外收获"）**
```sql
`status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-草稿, 1-公开已发布, 2-私密/仅自己可见'
```
> 表结构**已经**支持草稿与私密文章。`MemoryPublishService.publish()` 却直接建 `status=1` 的公开文章。**"私念转公文草稿"不需要改表，只需要改服务层 + 前端加一个草稿列表页。**

**`memory_embedding`**
- `vector_json longtext`：1024 维 float 的 JSON 字符串（不是二进制，体积约为 float32 的 3~4 倍）；
- `privacy tinyint`：**隐私快照**，改隐私时 `MemoryQueryService.setPrivacy()` 会同步回写（第 300 行）；
- `uk_user_memory(user_id, memory_id)`：一条记忆只能有一份向量。

**`memory_emotion`**：`uk_user_emotion(user_id, emotion)` + `aliases varchar(500)`（原始标签集合），`count bigint`。

**`memory_insight`**：`source tinyint`（1=AI 提炼 / 2=用户手动），无软删字段。

### 4.3 全库共性（迁移与治理的直接依据）

| 事实 | 影响 |
|---|---|
| **零外键约束**（16 张表无一 `FOREIGN KEY`） | ① 迁 PG 时无 FK 可迁，迁移简单；② 但也没有任何 DB 层引用完整性，`memory_note.published_article_id` 悬空无法被发现；③ 删文章不会级联 |
| **统一软删 `is_deleted`**（除 `memory_insight`、`memory_user_stat`、两张关联表） | MyBatis-Plus `logic-delete-field` 全局生效；**`memory_insight` 是例外，会被物理删除**——"用户手动加/删闪光点"是真删，与拾光"只收纳不评判"的调性相反，建议补软删 |
| 所有时间列用 `datetime` + `DEFAULT CURRENT_TIMESTAMP` | `datetime` 不带时区语义。留 MySQL 时需**应用层统一按 UTC 写入**（或改 `TIMESTAMP`，但有 2038 上限），否则多端显示会漂 8 小时 |
| `ON UPDATE CURRENT_TIMESTAMP` 大量使用 | SQLite/部分方言不支持，迁移时需改触发器或应用层赋值 |
| 金额/浮点：无 | — |
| 加密：无（`api_key`、`picui_token` 全明文） | 见 §10 R5 |

### 4.4 两处数据异常（v2 新发现，**建议优先排查**）

**异常 A：`memory_emotion` 的 `AUTO_INCREMENT=18701`，但只有 38 条记忆。**
`uk_user_emotion(user_id, emotion)` 限定每个用户每种情绪最多一行，理论上全表行数 ≤ 用户数 × 情绪桶数（≈ 4 × 8 = 32）。**18701 意味着历史上插入了约 18700 行**，只可能是：早期表结构是按消息/按记忆逐条插入（后改成归并表但没重建），或存在反复 delete+insert。

**异常 B：`MemoryEmotionService.mergeEmotions()` 的 `count` 会无限累加。**
```java
upd.setCount(existing.getCount() == null ? e.getValue() : existing.getCount() + e.getValue());
```
每次归并都**重新从全部 note 统计一遍 tagCount，再叠加到已有 count 上**。归并 3 次，count 就是真实值的 3 倍。而 `GET /api/memory/emotion/stats` 与前端"情绪锚点分布"图直接读这个数——**你看到的情绪分布图数字很可能是虚高的**。
`来源：service/impl/MemoryEmotionService.java 第 172 行附近`

验证 SQL（只读）：
```sql
SELECT COUNT(*) AS rows_now FROM memory_emotion;
SELECT user_id, emotion, count, last_seen_at FROM memory_emotion ORDER BY count DESC LIMIT 20;
-- 与真实值对账
SELECT COUNT(*) FROM memory_note WHERE user_id=1 AND emotions LIKE '%疲惫%';
```

**异常 C：`memory_embedding` 约 2962 次插入 vs 38 条记忆。**
`storeEmbedding()` 每次都是 `delete ... then insert`（`MemoryVectorService`），配合唯一键会产生大量**间隙锁**与自增膨胀。建议改为 `INSERT ... ON DUPLICATE KEY UPDATE`（UPSERT），一举解决膨胀与锁竞争。

### 4.5 索引治理建议（第 0/1 阶段）

| 建议 | 理由 | 风险 |
|---|---|---|
| `memory_note` 加 `(user_id, privacy)` | 3 条隐私过滤查询全表扫的根因；也是"隐私过滤下沉到 SQL"的前提 | 无（低写入量表） |
| `memory_note` 加 `(user_id, finish_time)` | 时光轴按 `finish_time` 聚合，目前无可用索引 | 无 |
| `memory_note` 删 `idx_user_id` | 被 `idx_user_create(user_id, create_time)` 左前缀完全覆盖，纯冗余 | 需确认无 `FORCE INDEX` 依赖 |
| `memory_embedding` 加 `(user_id, privacy)` | 召回的候选集过滤 | 无 |
| `blog_article` 加 `(status, create_time)` | 公开列表 `WHERE status=1 ORDER BY create_time` | 无 |
| `memory_emotion.count` 改为**幂等重算** | 修 bug，不是加索引 | 需一次性清洗历史虚高值 |

> ⚠️ **v3 说明**：既然第 0 阶段直接换 PG（§8.3），**以上 MySQL ALTER 不必在生产执行**——这些索引已在 `backend/src/main/resources/db/migration/V1__baseline.sql` 里按 PG 写法一次性建好（含 `idx_note_user_privacy`、`idx_note_user_finish`、`idx_article_status_create`，并移除了冗余的 `idx_user_id`；`count` 列直接删除改实时聚合）。保留本表是为了记录"现库缺什么"，以及万一回滚到 MySQL 时的补丁清单。

### 4.6 迁移方式（现状）

- **无迁移工具**。变更流程＝"手写 SQL 贴到库里 + 同步改实体类"。
- `resources/sql/` 只有 `ai_provider.sql`、`memory_insight.sql` 两个 `CREATE TABLE IF NOT EXISTS`，**无 ALTER、无版本号、无回滚**。
- 本次已用你提供的 DDL 生成两份基线：
  - `docs/legacy/schema-baseline.mysql.sql`（**16 表，现库原样**）→ 用途改为：**回填脚本的结构参照 + 回滚时的 MySQL 复原脚本**，不再作为 `V1__baseline.sql`；
  - `backend/src/main/resources/db/migration/V1__baseline.sql`（**25 表，目标架构草案**）→ 这才是第 0 阶段的 `V1__baseline.sql`（见 §8.4）。
- 两库并行期建议：MySQL 转只读保留 30 天，Flyway 版本号只在 PG 侧推进，避免双份迁移脚本失同步。

---

## 5. 前端模块地图

**规模 14,216 行**；最大单文件 `views/admin/memory/index.vue` **970 行**。

```
frontend/src/
├── api/        11 模块 650 行（memory.js 占 244 行，最大）
│   └── memory.js  已封装 privacy / insights / emotion/stats / emotion/merge（比旧 OpenAPI 快照新）
├── components/
│   ├── article/  ArticleContent(518) / ArticleHeader / TableOfContents
│   ├── comment/  CommentSection(305) / CommentForm(305) / CommentItem(237)
│   ├── memory/   ChatConversation(252) / MemoryListPanel(309) / MemoryEditDialog
│   │             / MemoryPrivacyDialog / MemoryUploader      ← 拾光 UI 已有一半
│   ├── uiverse/  Neomorphic* / RadioGroup / SocialGrid
│   ├── vuebits/  GlassSurface(362) / SplitText / TextType     ← 视觉特效层
│   └── AiEditor.vue(220) / bloglistcard.vue(367)
├── views/
│   ├── home/index.vue(252)      4 面板：home / blog / life / resume（异步组件）
│   ├── home/blog/index.vue(763) 文章详情　　home/right/blog.vue(441) 列表
│   ├── home/right/resume.vue(763) 简历　　home/self/(info, Social)
│   ├── login/index.vue(394)
│   └── admin/  Layout + articles(index/write) + categories + tags + comments
│               + links + users + configs(aiModel) + memory/index(970)
│               + tools(picture / agnesVideo 705 / agnesImage 444)
├── stores/  auth / article(292) / comment / config
├── utils/   request.js / markdown.js(345) / format.js
└── styles/  global.scss
```

**复用判断**

| 能力 | 落点 | 结论 |
|---|---|---|
| 文章渲染 | `ArticleContent.vue` + `utils/markdown.js` | ✅ 高价值复用（目录/高亮/markdown 双通道） |
| 评论 | `components/comment/*` + `stores/comment.js` | ✅ 可复用 |
| 视觉体系 | `views/home/index.vue` + `vuebits/*` + `global.scss` + `DESIGN_LINEAR.md` | ✅ 可复用（移动端需另做 token 体系） |
| 后台壳 + 守卫 | `router/index.js`（`requiresAuth` + `role==='ADMIN'`） | ⚠️ 部分：守卫是 localStorage 判断，真边界在后端；新增念生菜单需扩 |
| 记忆管理 | `views/admin/memory/index.vue`(970) + `api/memory.js`(244) | ✅ **拾光 Web 端已有约 70%**，但形态是管理端 |
| 编辑器 | `AiEditor.vue` + `admin/articles/write.vue` | ⚠️ 需加"从日记生成草稿"入口 |
| 登录 | `views/login/index.vue` | ⚠️ 需加隐私门（PIN/生物识别），现状只有账号密码 |

**关键缺口**：**没有任何面向普通用户的拾光页面**——无 `/memory` 路由、无时光轴页、无相册页。`/admin/memory` 是唯一入口且限 ADMIN。`来源：src/router/index.js`

---

## 6. 后端 / API 现状

```
controller(18) → service → service/impl → mapper(17) → MySQL
        ↑ dto(12) / vo(7) / entity(16) / common(Result/异常) / aspect(限流) / security(JWT)
```
**116 个 .java，72 个源码端点。**

| Controller | base | 端点数 |
|---|---|---|
| `MemoryController` | `/api/memory` | **20** |
| `AdminConfigController` | `/api/admin/configs` | 6 |
| `AiProviderController` | `/api/admin/ai-provider` | 6 |
| `UserController` / `AdminArticleController` | `/api/admin/users`、`/articles` | 各 5 |
| `AdminCategory/Tag/Link/AgnesVideo` | — | 各 4 |
| `AdminCommentController` | `/api/admin/comments` | 3 |
| `AuthController` / `PublicArticle` / `PublicComment` | — | 各 2 |
| `PublicCategory/Tag/Link/Config` / `FileUpload` | — | 各 1 |

> ⚠️ `docs/legacy/api-2026-08.openapi.json` 是 **50 条端点的旧快照**，缺 `privacy / update / emotion/stats / emotion/merge / insights / insight/add / insight/delete / admin/ai-provider/*` 等 22 条。生产 `/v3/api-docs` 不可达（nginx 未反代），**只能本地起服务重新导出**。

### 6.1 `/api/memory/*` 20 个端点

| 组 | 端点 |
|---|---|
| 创建 | `POST /create`（**必须带图**）、`POST /captcha` |
| 对话 | `GET /ai/opening`、`POST /chat/send`（需验证码）、`POST /chat/img` |
| 凝聚 | `POST /condense` |
| 查询 | `GET /list` `/search` `/detail` `/stat` |
| 生命周期 | `POST /abandon` |
| 隐私 | `POST /privacy` |
| 编辑 | `POST /update` |
| 情绪 | `GET /emotion/stats`、`POST /emotion/merge` |
| 闪光点 | `GET /insights`、`POST /insight/add`、`POST /insight/delete` |
| 博客联动 | `POST /publish`、`GET /publish-status` |

### 6.2 记忆机制（最值得保留的资产）

`来源：service/vector/MemoryVectorService.java`

- **三通道打分**：`cosine + emotionBoost(0.25) + recencyFactor(0~0.4)`；
- 时序因子：≤7 天 +0.4 / ≤30 天 +0.25 / ≤90 天 +0.1 / 更早 0；
- **封存拦截**：`privacy == 2` 的 embedding 永不进候选池；
- **双层召回**：记忆级 + 一句话级（insight，额外 +0.05）；
- **降级**：embedding 服务不可用时退回关键词召回。

**但当前数据量下这不构成瓶颈**：`selectByUser(userId, 500)` 实际最多返回 38 行（唯一键限制），1024 维 ×38 ≈ 4 万 float/次，**微秒级**。性能优化到"记忆上千条"时再做，不必现在动手。

### 6.3 安全边界（含生产验证）

| 项 | 现状 | 评价 |
|---|---|---|
| 白名单 | `/api/auth/login,register`、`/api/public/**`、**`/uploads/memory/**`**、swagger | ⚠️ 静态上传目录匿名开放（若启用本地存储则照片裸奔） |
| 默认策略 | `anyRequest().authenticated()` | ✅ 生产实测 `/api/memory/list` 无 token → **403** |
| 数据隔离 | 全部 memory 查询强制 `SecurityUtils.getUserId()`；管理员越权需显式传 `userId` | ✅ 设计清晰 |
| Swagger | 生产 404（nginx 只反代 `/api/`） | ✅ 意外的好 |
| 测试 | **零**（无 `src/test`） | ❌ 高风险 |

---

## 7. 目标功能映射表

图例：**已有** / **部分具备** / **缺失** / **需重构**

### 7.1 拾光碎念

| 目标能力 | 判定 | 证据与差距 |
|---|---|---|
| 记忆 CRUD | **部分具备** | `create/list/detail/update/abandon` 齐备；但 `create` 强制 `MultipartFile`，**无法创建纯文字碎念** |
| 三级隐私 | **需重构（改造点已全部明确）** | 现状只有字段+接口+召回的 if。缺：①`list/search/detail` 无 privacy 条件 ②**无 privacy 索引** ③封存"本人也看不到"无法表达（无全局 PIN/`sealed_at`/解锁令牌/审计）④公开无出口 ⑤封存照片仍公网 URL。**D11 定 PIN 粒度、D12 定公开动作、D14 定公开可见面，已无设计悬念** |
| 情绪标签 | **已有** | `emotions` + `memory_emotion` 归并 + `emotion/stats` + 召回加成；**但 `count` 累加 bug 使统计失真**（§4.4） |
| 时光轴 | **缺失** | 只有 `list` 的 `create_desc/finish_desc` 排序；无年/月聚合 API、无 `finish_time` 索引、无前端页 |
| 照片 | **部分具备** | 封面+缩略图+对话图；**无独立 photo 实体**（不能做相册/按时间归集/EXIF/一记多图）；历史图床 `free.picui.cn` 公网（D5 已接受既成事实，不迁） |
| 私密日记 | **部分具备** | 只能由"照片+对话"凝聚产生，**不能手写**；无草稿态；无版本历史 |
| 博客投影（D12/D13） | **部分具备，方向正好对** | 现状 `publish()` 直接写 `status=1`——**恰好就是 D12 要的"设公开即发布"**；缺的是**投影模型**：`source_rev` 水位、`manual_edited` 断点、改记忆时重新投影。原实现是"一次性复制后两份真相"，这才是要改的 |
| 碎念（无图短句） | **缺失** | 产品核心之一，数据模型强制以照片为起点 |
| **公开记忆被他人浏览（D14）** | **缺失** | `/api/public/*` 只有 `articles/categories/tags/comments/configs/links`，**没有 memory 公开读接口**；`memory_note` 也缺跨用户公开索引 |

### 7.2 念生

| 目标能力 | 判定 | 说明 |
|---|---|---|
| 想法 CRUD | **缺失** | 全仓 `念生/niansheng` 无业务命中 |
| 状态机 | **缺失** | 唯一"状态机"是 `memory_note.status` 的 4 个 int 常量 + switch，**不可复用** |
| AI 分析 / 质疑 | **缺失（基础设施可复用）** | `AgnesAiClient.chat(systemPrompt, messages, maxTokens, temperature)` + `AiProviderService` + `@AiRateLimit` 全部现成，**只需写 Prompt 与域服务** |
| 看板 | **缺失** | 无 status 分组/排序 API，无拖拽组件 |
| 关系边 | **缺失** | 无 graph/edge 表；你也已标"后置" |

### 7.3 复用价值排序（结论：不重写）

| P | 资产 | 理由 |
|---|---|---|
| P0 | `MemoryVectorService` 三通道召回 | 原创机制＝产品差异化 |
| P0 | `AgnesAiClient` + `AiProviderService` | 多供应商热切换已解决 AI 运维痛点 |
| P0 | `SecurityConfig` + JWT + `SecurityUtils` | 可直接承载三端 |
| P1 | `MemoryPublishService` | 产品王牌；改造成**投影服务**即可（D12/D13），逻辑框架可留 |
| P1 | `MemoryEmotionService` | 逻辑完整（除 count bug） |
| P1 | 博客域 9 表 + 全 CRUD | 零改动可留 |
| P2 | `views/admin/memory/index.vue`(970) | 功能全，需从管理端拆出用户端 |
| P3 | Flutter `addtoblog` | 与拾光无关键，见第 2 阶段 |

---

## 8. MySQL vs PostgreSQL + pgvector 评估（**v5：结论为"不需要 PG"，已撤回 v3 的迁移结论**）

### 8.1 先把两个问题分开

上一轮我在这节里犯了一个推理错误：把"迁移变便宜了"当成了"应该迁移"。

v2 判定"不迁"的三个前提确实被你推翻了：

| v2 前提 | 现状 | 是否成立 |
|---|---|---|
| ① 16 表只有 2 张有 DDL，迁移要先反向工程 | 你给了全量 DDL，且 **D6 允许重建任何表** | ❌ 失效 |
| ② 迁移要动生产数据、要双写、有停机风险 | 全库约 **100 行**，一次 `COPY` 搬完；向量重 embed 成本≈0 | ❌ 失效 |
| ③ 单用户，不需要 RLS | **D4 多用户注册** + **D2 封存本人也不可见** | ❌ 失效（但见 §8.4 对 RLS 的复核） |

**但"前提失效"只推出一个结论：迁移的成本门槛没了。它不推出"必须迁移"。** 是否迁移取决于另一件事——**MySQL 8.0.40 能不能满足你的功能需求**。这才是本轮要回答的问题。

回答方式不是比较"哪个库更先进"，而是逐条检查：我给 PG 列的那些优势，MySQL 是不是真的没有。

### 8.2 先纠正我 v3 的夸大（逐条复核）

v3 那张对比表把"PG 有、MySQL 没有"写成了"MySQL 做不了"。这是错的。逐条复核如下：

| 我在 v3 说的 PG 优势 | MySQL 8.0.40 的真实能力 | 复核结论 |
|---|---|---|
| `pg_trgm` 替代 `LIKE '%kw%'` | **InnoDB FULLTEXT + `WITH PARSER ngram`**（8.0 内置，专为中日韩分词） | **打平** |
| `text[]` + GIN 存情绪标签 | **JSON 列 + 多值索引**（8.0.17+，可直接对数组元素建索引） | **打平** |
| `WITH RECURSIVE` 做关系图谱 | **MySQL 8.0 支持 `WITH RECURSIVE`** | **打平** |
| `jsonb` 存 AI 分析结果 | JSON 列 + `JSON_TABLE`（8.0.4+） | **打平** |
| `timestamptz` 解决时区 | `TIMESTAMP` 内部即存 UTC（有 2038 上限）；或应用层统一 UTC | **基本打平** |
| `CHECK` 约束表达封存不变量 | **MySQL 8.0.16+ 已强制 `CHECK`** | **打平** |
| `vector(1024)` + HNSW ANN | **没有**（8.0 系列无 `VECTOR`，9.0 才有） | **PG 独有** |
| RLS 行级安全 | **没有**（只能应用层 + 视图） | **PG 独有** |
| 部分索引（`WHERE privacy<>2` 的 HNSW） | **没有部分索引**（可用生成列 + 索引近似，但写起来别扭） | **小差距** |

**结论：真正只有 PG 能给的，只剩"向量 ANN"和"RLS"两项。其它我列的优势，MySQL 8.0.40 都有等价物。**

### 8.3 那"向量 ANN"到底是不是你的需求？（算一下）

- 现在 **38 条**记忆、89 条闪光点。应用层算余弦：3.8 万次乘加，**微秒级**，HNSW 毫无意义。
- 假设涨到 **5,000 条**：5,000 × 1,024 ≈ 512 万次乘加 ≈ **5~10ms**——依然不是瓶颈。
- **真正的瓶颈是 JSON 反序列化**：5,000 条 × 每条约 8KB 的 `vector_json` ≈ **40MB 文本/次查询**，解析开销比计算高两个数量级。
- 而这个瓶颈在 MySQL 侧有廉价解法：**向量改存 `BLOB`（float32 小端）+ Caffeine 缓存解析后的 `float[]`**。一次冷启动后全部命中内存，后续查询只剩"过滤 + 余弦"。

**所以：换 PG 换来的不是"能不能 vector"，而是"省掉自己维护缓存这一层"。到几万条之前，MySQL 顶得住。**

### 8.4 结论（v5 修正）

**不需要 PostgreSQL。默认留 MySQL 8.0.40，把"迁 PG"降级为一条有明确触发条件的备选路线。**

我 v3 的操作错在哪，说明白：

| v3 的理由 | 复核 | 判决 |
|---|---|---|
| ① D6 允许重建 → 迁移变便宜 | 事实成立 | 但这是**成本**理由，不是**必要性**理由 |
| ② D2 封存要"数据层就不给" | **夸大**。RLS 无法表达"需 PIN 解锁"——最终仍由应用层判定；RLS 只能做 `user_id` + `privacy` 的基础隔离，而这用**统一 Repository 拦截器 + 54 用例矩阵**同样能做到 | **不成立** |
| ③ D3/D12 顺带解决 | 我自己在原文里就写了"与库无关" | **不算理由** |
| ④ D14 公开读索引 | 两种库都得单独建这个索引 | **不算理由** |
| ⑤ 时间窗口（越晚越贵） | 只有在"确定将来会用上 PG 独有能力"时才成立 | **条件不成立则不适用** |

**把"便宜"当成"该做"，是我 v3 的判断失误。** 你要为用不上的能力提前付一周 + 一套新运维，这不是架构，是焦虑。

**升级触发条件（任一满足才迁，不预先迁）**

| # | 条件 | 为什么这条才成立 |
|---|---|---|
| T1 | 单用户记忆 > 5,000 **且** BLOB+缓存方案下 `recall` P95 仍 > 800ms | 说明真的到了 ANN 的边界 |
| T2 | 需要**跨用户语义推荐**（"和你有相似记忆的人还写了…"） | 全局 ANN，MySQL 无解 |
| T3 | 需要**DB 层强制隔离**，且开发人数 > 1 | RLS 的价值在"防队友误写"，单人靠纪律 + 测试就够 |
| T4 | 关系图谱需要**深度 > 3 的多跳**查询 + 动态边权 | 递归 + 加权在 MySQL 会很难写 |

**`backend/src/main/resources/db/migration/V1__baseline.sql` 不会浪费**：它作为"未来形态"存档，字段与 MySQL 侧一一对应（`text[]`↔JSON、`vector`↔BLOB、`timestamptz`↔UTC `datetime`）。触发 T1~T4 时按 §8.6 执行，数据量仍小，一天能搬完。

### 8.5 若留 MySQL：必须照做的 6 件事（这才是默认路线）

| # | 动作 | 具体做法 | 不做的后果 |
|---|---|---|---|
| M1 | **补迁移工具** | Flyway 支持 MySQL：`docs/legacy/schema-baseline.mysql.sql` 作 `V1__baseline.sql`，后续每次变更写 `V2__…` | 继续手贴 SQL，schema 无审计 |
| M2 | **补索引** | §4.5 全部；另加 `(privacy, create_time)`（D14 跨用户公开读）、`(user_id, finish_time)`（时光轴） | 公开列表与时光轴全表扫 |
| M3 | **向量改 BLOB + 缓存** | `vector_json longtext` → `vector_blob BLOB`（float32 小端）；Caffeine 缓存 `userId → float[][]`，TTL 10min 或写操作失效；召回先 SQL 过滤 `user_id + privacy<>2 + status` 再算 | 每次召回 parse 几十 MB JSON |
| M4 | **情绪改 JSON 数组** | `emotions` 逗号串 → JSON 列 + 多值索引；统计用 `JSON_TABLE` 或应用层 `unnest` 等价 | 统计只能全表 LIKE |
| M5 | **封存拦截下沉到 Repository** | 自定义 MyBatis 拦截器，对 `memory_note/memory_message/memory_photo/memory_embedding` 自动追加 `privacy <> 2`（除非显式声明"已解锁"上下文）；业务代码**禁止手写** privacy 条件 | 漏一处就是隐私事故（现状正是这样） |
| M6 | **中文搜索上 ngram** | `ALTER TABLE memory_note ADD FULLTEXT INDEX ft_note(auto_title, final_diary) WITH PARSER ngram;` | `LIKE '%kw%'` 永远全表扫 |

> M5 是最关键的一条：**"数据层兜底"不必换数据库，一个拦截器就能做到**。这也是我 v3 该给却给错的答案。

### 8.6 若将来走 PG：迁移步骤（备档，触发 T1~T4 时执行）

| 步 | 动作 | 产出 / 验收 |
|---|---|---|
| 1 | 起 PG 16 + `vector`/`pg_trgm` | 可连 |
| 2 | 跑 `backend/src/main/resources/db/migration/V1__baseline.sql`（25 表草案，需 review） | 表数 25 |
| 3 | 回填脚本 `tools/migrate/`（幂等，只读源库） | 行数对账 100% |
| 4 | 向量重建：38 记忆 + 89 闪光点重调 `bge-m3` | 维度 1024、条数一致 |
| 5 | 方言切换：MP `DbType.POSTGRE_SQL`、移除 PageHelper、`BLOB→vector`、`JSON→text[]` | 72 端点全绿 |
| 6 | 新旧对比（同批请求双跑） | 差异率 0，之后才允许删旧表 |



---

## 9. 分阶段升级路线（v5 · MySQL 默认线）

### 第 0 阶段：合并 + 库基线 + 契约（1~1.5 周）

**目标**：把"四个切片 + 一张无版本管理的库"变成"一个能跑、能测、契约冻结的 monorepo"。**库仍是 MySQL（§8.4）。**

| 交付物 | 内容 |
|---|---|
| `upgrade/v2` 分支 | merge(main, backend)，19 处 modify/delete（零文本冲突）；重写根 README |
| **库基线（MySQL）** | `docs/legacy/schema-baseline.mysql.sql` 作 `V1__baseline.sql`；接入 **Flyway（MySQL 版）**；`V2__privacy_and_index.sql` 承载 §8.5 的 M2/M4/M6 变更 |
| **schema 演进（不重建表）** | 加列：`memory_note.kind / sealed_at`、`sys_user.privacy_pin_hash / pin_failed_count / pin_locked_until / pin_updated_at`、`blog_article.source_memory_id / source_rev / manual_edited`、`memory_photo` / `memory_access_log` / `ai_prompt` 三张新表 |
| **向量改造（M3）** | `vector_json longtext` → `vector_blob BLOB`（float32 小端）+ Caffeine 缓存；一次性转换脚本把现有 38+89 条 JSON 解码成 BLOB |
| 契约冻结 | 本地起服务用 springdoc 重导 `contracts/openapi.json`（**72 端点**，替换 50 条旧快照）；错误码枚举化 |
| 测试地基 | JUnit5 + Testcontainers(**MySQL 8.0.40**)，把越权矩阵写成可执行用例；前端 vitest 骨架 |
| 安全清理 | 轮换 3 处硬编码 `sk-`（`AiEditor.vue:33`、`agnesImage.vue:174`、`agnesVideo.vue:223`）；`ai_provider.api_key` / `blog_config.picui_token` 改加密存储；`uploads/memory/**` 移出白名单 |

**验收**
- 本地起 MySQL 8.0.40（Docker 或本机）：`flyway migrate` 建出 16 表 + 新增 3 表 = **19 表**；
- 存量数据零丢失（38 记忆 / 18 文章 / 50 消息 / 89 闪光点 / 4 用户），向量 BLOB 长度 = 1024×4 = 4096 字节；
- 72 端点全部可用，行为与改造前一致（**这一阶段不改业务行为**）；
- `mvn test` + `npm run build` 绿；
- 全仓 `sk-` 命中 = 0。

**开工顺序（串行，可直接派活）**
1. `git checkout -b upgrade/v2 origin/main && git merge origin/backend` → 19 处冲突全部 `git add` 保留 → 重写根 README（半天）
2. 起 MySQL 8.0.40 → `flyway migrate` 跑 `V1__baseline` + `V2__privacy_and_index` → 19 表（半天）
3. 最小闭环 spike：Spring Boot 4.0.6 + MP 3.5.15 + Flyway 在 MySQL 上跑通 `sys_user` CRUD（**确认 Boot4 与 MP 3.5.15 的兼容性，这是唯一技术不确定性**）
4. 加列 + 三张新表 + 实体对齐（`kind` / `sealed_at` / `source_rev` / `manual_edited` / PIN 四件套）
5. 向量 BLOB 迁移脚本（幂等）+ Caffeine 缓存接入 + 召回改"先 SQL 过滤后算"
6. 72 端点回归（**应零行为差异**）
7. springdoc 重导 openapi + Testcontainers 越权用例骨架 + 密钥轮换

**风险**
- 🟠 `Spring Boot 4.0.6 + MyBatis-Plus 3.5.15 + Flyway` 组合需半天 spike（Boot 4 较新，Flyway 版本要选匹配的）；
- 🟠 向量 JSON→BLOB 转换要**先备份再跑**，脚本幂等可重跑；
- 🟡 加列用 `ALTER` 而非重建 → 需确认 `memory_note` 现网数据在加 `NOT NULL` 列时给默认值（38 行，可直接带默认值）。


### 第 1 阶段：后端 MVP（隐私为真 + 拾光硬化，3~4 周）

| 交付物 | 内容 |
|---|---|
| **封存 = 本人也看不到（D2，P0）** | ① `sys_user.privacy_pin_hash` + 失败锁定（DDL 已含）② `memory_note.sealed_at` + `CHECK(privacy<>2 OR sealed_at IS NOT NULL)` ③ 解锁签发**短时令牌**（5 分钟，内存态）④ `list/detail/search` 默认排除 `privacy=2`，带令牌才返回 ⑤ `memory_access_log` 记录每次解锁/失败（DDL 已含） |
| **即发博客（D12，P0）** | `privacy` 改 0 **立即**投影为 `blog_article(status=1, source_memory_id=..., source_rev=n.update_time)`；改回 1/2 立即下架（`status=0`）；`uk_article_source_memory` 保幂等。UI 加二次确认 + **5 分钟撤回窗口**（R2 兜底） |
| **同步更新（D13，P0）** | 投影服务：记忆更新后比对 `n.update_time > a.source_rev` → 重投影并升 `source_rev`；若 `a.manual_edited=1` 则**不覆盖**，标 `needs_review` 待人工合并（避免冲掉润色） |
| **公开读面（D14，P0）** | 新增 `/api/public/memories`（分页、按年/月、按情绪）+ `/api/public/memories/{id}`（仅 `privacy=0`）；与博客同口径：未登录可读，**不暴露 user 私有字段**（只出昵称/头像）；依赖 `idx_note_public` |
| **照片受控（D5）** | 新写入的私密照片走 `GET /api/memory/photo/{id}`（JWT + privacy 校验 + 令牌）；历史 picui 图片标 `memory_photo.storage=2`，**不做回滚**，仅在产品文案里提示"历史图片为公网" |
| **纯文本碎念** | `POST /api/memory/fragment`，`kind=2`，不再强制带图 |
| **召回重写（M3 落地）** | `MemoryVectorService` 改为：SQL 先过滤 `user_id + privacy<>2 + status` → 从 Caffeine 取 `float[][]` → 内存算余弦 + 三通道加权（emotion boost / recency **原样保留**）；取消 `LIMIT 500` 全量拉取 |
| **两个 bug 从根上消失** | ① `memory_emotion` **删掉 `count` 列**，改实时聚合 ② 向量写入改 `INSERT ... ON DUPLICATE KEY UPDATE`（不再 delete+insert），顺带止住自增膨胀与间隙锁 |
| **时光轴** | `GET /memory/timeline?year=&month=`（按 `finish_time` 聚合）+ `GET /memory/on-this-day`；索引已就位 |
| **念生只建表** | `idea` / `idea_state` / `idea_transition_rule` / `idea_transition` / `idea_analysis` / `idea_relation`（DDL 已含 6 态与迁移规则，**不写业务**） |
| 可观测 | 统一 `traceId`；AI 调用耗时/成本埋点 |

**验收**
- 越权矩阵（3 角色 × 3 隐私 × 6 端点 = 54 用例）自动化全绿；
- **封存专项**：无令牌时 `list/detail/search/召回` 四处**全部**看不到该条（这是 D2 的验收核心）；令牌过期后立刻失效；失败 5 次锁定；
- `privacy=0` 的记忆出现在 `/api/public/articles/page` **且**出现在 `/api/public/memories`；改回 1 则两处同时消失（D12/D14 验收）；
- 改记忆正文后文章内容同步变化（D13）；手工改过文章（`manual_edited=1`）时记忆再改**不覆盖**，只标 `needs_review`；
- 新写入的私密照片无 token 访问返回 401/403；
- `emotion/stats` 与手工统计一致（验证 count 修复）；
- 召回：38 条基准下 P95 < 50ms。

**风险**
- 🔴 隐私改造触碰所有 memory 查询，**必须先有第 0 阶段的测试地基**；
- 🟠 "公开=即时发博客"是 D12 的既定行为，误发风险由**二次确认 + 5 分钟撤回**兜底；仍需在 UI 上把"这会把内容推上公网"写显眼；
- 🟡 历史 picui 图片仍公网，产品文案需诚实说明。

### 第 2 阶段：Flutter 拾光（新建工程，4~6 周）

**D7 + D15 已定：`addtoblog` 保留不动；拾光与念生均为新建 Flutter 开发**，不改造发布器。参考其分层约定与 Windows 拖放踩坑记录即可。

| 交付物 | 内容 |
|---|---|
| 新工程 `apps/shiguang/` | 新建；沿用 `addtoblog` 验证过的分层（`core/theme → data → domain → presentation`）与 token 体系思路；**引入 `flutter_secure_storage`**（token/PIN 不落明文） |
| 隐私门 | 启动 PIN/生物识别；切后台模糊遮罩；Android `FLAG_SECURE` |
| 封存交互 | 封存列表默认**只显示占位**（"3 条已封存"），点击需 PIN；解锁后才解密标题与封面 |
| 主界面 | 时光轴（年月聚合 + 情绪色带）/ 碎念快速输入（<3s 落地）/ 照片记忆流 / 记忆详情（对话回放 + 闪光点 + 隐私切换） |
| 情绪可视化 | 复用 `emotion/stats`（v3 起数字已可信） |
| 离线 | **v1 只做离线读缓存，不做离线写**（D10 无实时同步，离线写会引入冲突合并复杂度，不值当） |
| 边界 | 只读自己的公开文章（"公文映私念"），**不写公开内容** |
| 明确不做（D16） | 不做导出、不做推送（无 FCM/APNs）、不做离线写 → 无后台常驻服务，纯前台 App |

**验收**：冷启动到可记录 ≤2s；后台 30s 后截图空白；封存条目无 PIN 时列表只显示数量；Android+iOS 双端可跑；隐私切换后列表/详情/召回三方一致。

**风险**：🔴 Flutter 移动端交付经验未知（现有分支是桌面工具，不代表移动能力）；🟡 生物识别在部分国产 ROM 上行为不一致，需降级到 PIN。

### 第 3 阶段：念生域（六态状态机，4~5 周）

**D8 已定：6 态。DDL 已含 `idea_state` 与 `idea_transition_rule`。**

```
seed 种子 → incubating 发酵 → validating 验证 → acting 行动 → crystallized 结晶
                    ↑              ↑                │
                    └──────────────┴────────────────┘   （可回退）
     任意非终态 → dormant 休眠 → incubating
```

| 交付物 | 内容 |
|---|---|
| 状态机服务 | 迁移前查 `idea_transition_rule`，非法迁移返回 **409**；每次迁移写 `idea_transition` 流水（含 reason）；支持按 idea 回放 |
| AI 分析 / 质疑 | 复用 `AgnesAiClient`：`POST /idea/{id}/analyze`、`/challenge`；输出**结构化 `jsonb`**（`claims/assumptions/risks/questions`），落 `idea_analysis`，**永不覆盖原文**；解析失败标 `degraded=1` 降级纯文本 |
| Prompt 治理 | 用 `ai_prompt` 表（scene + version + is_active 唯一索引），支持 A/B 与回滚；**念生 Prompt 全自研**（守"不复制无 LICENSE 第三方 Prompt"约束） |
| 看板 | `GET /idea/board`（按 state 分组）、`PATCH /idea/{id}/move`（校验 + 流水）、`GET /idea/{id}/history` |
| 与拾光的关系 | 拾光闪光点可**复制**升级为想法（`source_insight_id` 留痕，不引用）；念生想法**不回流**拾光 |
| 客户端 | Flutter 端"卡片 + 看板列"，Web 暂不做 |

**验收**：非法迁移 100% 被拒（全 from/to 组合参数化测试）；状态流水可完整回放；AI 输出 schema 校验通过；看板拖拽端到端一致。

**风险**：🔴 **状态机过度设计**是此类需求最常见死法——6 态已定，别再扩；🟡 AI"质疑"对私密想法有情绪风险，Prompt 必须写明"质疑的是假设不是人"，并给关闭开关。

### 第 4 阶段：博客联动与收敛（2~3 周）

| 交付物 | 内容 |
|---|---|
| 双向闭环 | ① 私念→公文：D12 即发已覆盖主链路，此阶段只补 Vue 端"灵感箱"页（列出已投影文章 + `needs_review` 待合并项）② 公文→私念：发文时可选"记录此刻心境"，写 `memory_note(kind=3, privacy=1)` |
| 不做项（D16） | 不做导出接口、不做推送、不做线上部署编排；年度报告仅"页内生成"，不发通知 |
| 年度报告 | `GET /report/annual?year=`：公开文章数 + 私密记忆数 + 情绪分布 + 高频词（JSON 数组聚合）；前端一页可分享 |
| **旧 Android 下线** | D9：无真实用户 → **直接 archive `memory` 分支 + 停服务端兼容端点**，不做灰度、不做迁移引导 |
| 契约收口 | OpenAPI 唯一真源；`openapi-generator` 生成 Vue/Flutter 客户端；旧快照仅存档于 `docs/legacy/` |
| 决策点 | 此时评估关系图谱是否启动（`idea_relation` 表已在，算法后置） |

**验收**：同一记忆不重复出文章（`uk_article_source_memory`）；年度报告数字与 DB 对账一致；三处客户端（Vue / 拾光 / 发布器）无契约漂移（CI 校验）。

---

## 10. 风险与待确认问题

### 10.1 风险清单（v3）

| # | 风险 | 级别 | 依据 | 缓解 |
|---|---|---|---|---|
| R1 | **隐私承诺与实现不符**（封存仅"AI 不提"，本人可随便看；公开无出口；照片公网） | 🔴 极高 | `MemoryQueryService` 无 privacy 条件；`memory_note` DDL 无 privacy 索引；`MemoryCreateService:132` 传 `false` | 第 1 阶段 P0；以 54 用例矩阵验收 |
| R2 | **`privacy=0` 即时发博客 → 误把私密内容推上公网** | 🟠 中（D12 已定行为，风险转为可控） | 下拉框一选即生效，误操作代价高 | 二次确认弹窗 + **5 分钟撤回窗口**（下架并软删文章）+ 列表页把"公开"标红；不再用"仅从草稿升级"限制（与 D12 冲突） |
| R3 | **零自动化测试，而隐私与库都要大改** | 🔴 高 | 无 `src/test`、无前端 test | 第 0 阶段先建 Testcontainers 地基，禁止跳过 |
| R4 | **一次性同时改库 + 改隐私模型** | 🟠 中高 | 两项都是高危变更，叠加会放大排查难度 | 严格串行：第 0 阶段只换库（行为不变），第 1 阶段才改隐私 |
| R5 | **向量 JSON→BLOB 一次性转换 + 缓存一致性** | 🟠 中 | 现库 `vector_json` 是 1024 维 JSON 文本 | 脚本幂等 + 先备份；缓存以"写操作即失效"为准，不用 TTL 兜底 |
| R6 | **敏感信息明文**：`ai_provider.api_key`、`blog_config.picui_token`；3 处前端硬编码 `sk-`；Android keystore 口令 `memory123` | 🟠 高 | DDL + `AiEditor.vue:33` 等 | 立即轮换；库内加密（v2 DDL 已加 `is_secret`）；Git 历史清理需 `filter-repo`（改 SHA，需协调） |
| R7 | **`Spring Boot 4.0.6 + MyBatis-Plus 3.5.15 + PG` 组合较新** | 🟡 中低 | `pom.xml` | 1 天 spike |
| R8 | **历史图床图片不可回收** | 🟡 中低 | D5 已接受 | 产品文案诚实说明 + 新数据走受控访问 |
| R9 | **AI 质疑对私密想法造成情绪伤害** | 🟡 中低 | 念生核心交互 | Prompt 约束"质疑假设非质疑人" + 可关闭 |
| R10 | **`api.txt` 与代码脱节 22 个端点** | 🟡 低 | 源码 72 vs 快照 50 | 第 0 阶段重导 |
| R11 | **多用户注册 + 无配额** → 恶意注册耗尽 AI 额度 | 🟡 中低 | D4 | 注册验证码/邮箱验证 + AI 按用户限流（`@AiRateLimit` 已有，需按用户维度） |
| R12 | **公开记忆被他人浏览时的身份泄露**（D14 新增） | 🟡 中低 | `/api/public/memories` 会暴露作者信息 | 只出 `nickname`+`avatar`（A2）；公开详情不收 `user_id`；封存/私密永不进公开查询 |

### 10.2 剩余待确认问题 → **已全部关闭**

D1~D16 覆盖了 v1/v2 提出的全部 10 条问题，**当前无阻塞项，可以开工**。以下 3 项不是"要不要做"，而是"做到什么程度"，可在执行中拍板，不阻塞第 0 阶段：

| # | 执行期细节 | 我的默认取值（不反对即按此实现） | 何时需要定 |
|---|---|---|---|
| A1 | 封存解锁令牌的**有效期与续期** | 5 分钟无操作失效；同一会话内切换页面不重复要 PIN；连续 5 次错锁 30 分钟 | 第 1 阶段写解锁服务时 |
| A2 | 他人公开记忆的**作者信息暴露面** | 只出 `nickname` + `avatar`，**不出** `username`/`email`/`user_id` | 第 1 阶段做 `/api/public/memories` 时 |
| A3 | 六态的中英文命名 | 代码/DB 用英文码（`seed/incubating/validating/acting/crystallized/dormant`），UI 用中文（种子/发酵/验证/行动/结晶/休眠），**DB 存码不存中文** | 第 3 阶段开工前确认一次即可 |

> 若 A1~A3 有不同意见，现在说最省事——都还没写代码。
---

## 附：取证边界

| 项 | 状态 |
|---|---|
| 16 张表结构 | ✅ 已获取（生产 `SHOW CREATE TABLE`，见 `docs/legacy/schema-baseline.mysql.sql`） |
| 生产域名可用性 | ✅ 已实测（`api.qisont.art` 200 / 鉴权 403 / swagger 404） |
| 各分支代码 | ✅ 全部分支直读 |
| MySQL 版本 | ✅ 8.0.40（用户确认 D1） |
| **目标 schema v2（PG）** | ✅ 已产出草案（`backend/src/main/resources/db/migration/V1__baseline.sql`，25 表），**未执行**；v5 起降级为"触发 T1~T4 时的备档"，不作为默认路线 |
| **现库 schema（MySQL）** | ✅ 已产出基线（`docs/legacy/schema-baseline.mysql.sql`，16 表）= 默认路线的 `V1__baseline.sql` |
| 生产真实行数 | ❌ 未获取（仅由 `AUTO_INCREMENT` 反推量级；D6 允许重建，故不影响方案） |
| `application-local.yml`（连接串/账号） | ❌ 未获取（未提交，也不应提供） |
| 线上部署拓扑（机器数、备份策略、CDN） | ➖ **本期不涉及**（D16）——方案只保证本地开发库可起，线上切换与运维另立计划 |
