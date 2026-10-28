# 项目计划书 · 拾光 / 念生

> 更新：2026-10-28 ｜ 适用对象：接下来要写 Flutter 客户端的自己（含三个月后接手的人）
> 结论先说：**后端与 Web 管理端已进入可用状态，P0 隐私改造、记忆投影、公开读面、念生六态全部落地并实测；下一阶段主线是新建一个 Flutter App（拾光 + 念生两个 Tab），先跑桌面 / Web，再上移动端。**

---

## 1. 现在的真实状态（2026-10-28 实测，不是"应该能跑"）

| 项 | 状态 | 证据 |
|---|---|---|
| 后端编译 / 打包 | ✅ | `mvn -DskipTests package`，BUILD SUCCESS |
| 后端启动 | ✅ | `Started BlogApplication in 8~12s`（Windows 11 · JDK 21） |
| 数据库 | ✅ | PostgreSQL 17.11（Supabase 会话池 `aws-0-ap-northeast-2.pooler.supabase.com:5432`） |
| 迁移版本 | ✅ | Flyway 已执行 V1 基线 + V2（needs_review）+ V3（审计表允许无 memory_id） |
| 接口规模 | ✅ | springdoc 导出 **70 条路径**，含 P0 与念生全部端点 |
| 鉴权边界 | ✅ | 未登录访问 `/api/memory/list` → 403；公开接口 200 |
| 封存 PIN | ✅ | 5 次错误锁 30 分钟（返回 423）、5 分钟滑动令牌、改 PIN 吊销旧令牌 |
| 记忆投影 | ✅ | `privacy=0` 即建/更新文章；改回 1/2 立即下架；人工编辑后只标 `needs_review` |
| 公开读面 | ✅ | `/api/public/memories` 不暴露 `userId/username`；非公开详情 404 |
| 照片受控 | ✅ | 新照片落本地 + `/api/memory/photo/{id}`：匿名 403、本人 token 200 |
| 念生后端 | ✅ | 六态看板、非法迁移 409、迁移流水、AI 分析降级落库、Prompt 版本化 |
| Web 管理端 | ✅ 构建通过 | 记忆页 PIN/隐私确认/封存、文章页"待合并"、念生看板；浏览器人工验收待做 |
| 旧库数据迁移 | ✅ | MySQL 8.0.40 → Supabase PG，见 §4 |
| Flutter 拾光 / 念生 | ❌ **未开工** | 仓库仅有 `apps/addtoblog`（Markdown 发布器） |
| 自动化测试 | ❌ | 后端零 `@SpringBootTest`、前端零 vitest、Flutter 零 widget test |

---

## 2. 这一版做完了什么

### 2.1 隐私从"字段"变成"可执行的规则"（D2 / D11 / D14）
- 封存(2)：本人也需 PIN；列表/搜索默认排除，详情/对话/照片需解锁令牌；AI 永不召回。
- 私密(1)：仅本人；管理员可运维查看但不能越封存。
- 公开(0)：注册用户可读，同时进入博客公开列表。
- 统一收口在 `MemoryVisibilityService`，业务代码不再手写 `privacy` 条件。
- 审计：`memory_access_log` 记录解锁成功/失败、封存查看、隐私变更。

### 2.2 记忆与文章变成"源与投影"（D12 / D13）
- `MemoryProjectionService`：`privacy=0` 立即投影为 `status=1` 文章（幂等锚点 `source_memory_id`）；改回私密/封存立即下架（`status=0`）。
- 改记忆 → 比对 `source_rev` 重投影；文章被人工编辑过（`manual_edited=1`）→ 只置 `needs_review=1`，不覆盖。
- Web 写文章页保存即标记 `manual_edited`。

### 2.3 拾光补齐"公开出口"与"轻记录"
- `POST /api/memory/fragment`：纯文本碎念（kind=2），不再强制带图。
- `GET /api/memory/timeline`、`/on-this-day`：按 `finish_time` 年月聚合 / 那年今日。
- `GET /api/public/memories/page`、`/{id}`：公开记忆读面，作者只出昵称/头像。

### 2.4 念生域（从 0 到可用后端 + 管理页）
- 六态字典 + `idea_transition_rule` 表驱动；非法迁移 409；`idea_transition` 可完整回放。
- AI 分析 / 质疑：严格 JSON 输出；解析失败或供应商不可用 → `degraded=1` 留痕，不覆盖原文。
- Prompt 治理：`ai_prompt` 按 scene 版本化，唯一激活；启动时幂等补默认。
- 闪光点一键升级为想法（留 `source_insight_id`）。
- Web 管理端：六列看板、拖拽迁移、历史时间线、分析抽屉、Prompt 版本管理。

### 2.5 工程收敛
- 后端/前端都不再依赖 `.env` / example 文件：配置只在 `application.yml`、`vite.config.js`、`request.js`。
- `docker-compose.yml` 仅作本地 PG 备选；默认连 Supabase 会话池（直连 `db.<ref>.supabase.co` 只有 AAAA，本机实测 TLS 反复 reset）。
- `application.yml` 明文连接串与 JWT secret：单机自用可以接受，**对外部署前必须换环境变量或加密**。

---

## 3. 架构与边界（写 Flutter 前先记住）

```
apps/shiguang (新)  ─┐
frontend (Vue 管理端) ─┼─→ backend (Spring Boot 4.0.6, /api/**) ─→ Supabase PostgreSQL 17 + pgvector
apps/addtoblog (旧)  ─┘                                        └─→ Agnes / DeepSeek（对话）、SiliconFlow bge-m3（向量）
```

- **认证**：自签 JWT（7 天），`Authorization: Bearer`；封存解锁令牌走 `X-Sealed-Token`（5 分钟滑动）。
- **接口契约**：`current` / `size` 分页、统一 `Result{code,message,data}`、业务错误也返回 HTTP 200（401 才是登录失效）。
- **照片**：新照片 URL 形如 `/api/memory/photo/{id}`，客户端必须带 `?token=<JWT>`（图片组件加不了 header）；历史 picui 公网 URL 原样返回。
- **不做**：实时同步（D10，不上 WS/SSE）、导出、推送、线上部署编排（D16）。

---

## 4. 数据现状（旧 MySQL 已迁入 Supabase）

旧库 `mysql7.sqlpub.com:3312/blog_sjy` 保持只读，未做任何写操作。迁入结果：

| 表 | 条数 | 处理说明 |
|---|---|---|
| sys_user | 2 | 旧 `admin` 与旧 `user`（施景阳）按邮箱命中并入现有 `admin`；`lizihan` 新建；已删除账号 `testxm` 跳过 |
| blog_article | 17 | 12 公开 + 5 软删；作者映射到新账号 |
| blog_category / blog_tag | 2 / 1 | id 保持 |
| blog_article_category / tag | 5 / 1 | 旧库孤儿关系（指向不存在的文章/标签）已跳过 |
| blog_comment | 3 | 归属文章均存在 |
| blog_config | 7 | 含 3 个密钥，已标 `is_secret=1` |
| ai_provider | 2 | Agnes Key 已恢复（模型 `agnes-2.5-flash` 沿用旧值）；DeepSeek 保持 |
| memory_note | 14 | 逗号串 → `text[]`；`kind` 按封面有无判定；id 保持 |
| memory_message | 32 | 旧库 49 条，17 条挂在早已删除的记忆上，跳过 |
| memory_embedding | 9 | `vector_json` → `vector(1024)`，全部 1024 维；按唯一键归一 |
| memory_insight | 32 | 向量 + 文本全迁，id 保持 |
| memory_emotion | 30 | `count` 列已废弃，只留 `aliases text[]` |
| memory_user_stat | 1 | 两条旧统计合并到同一账号，`total_memory` 按实际笔记重算 |
| memory_photo | 13 | 历史 picui 封面补为 `storage=2`，后续相册可用 |

一致性收尾：3 条曾发布过公开文章的记忆（28/31/38）已设为 `privacy=0`，文章回填 `source_memory_id` 且 `manual_edited=1`（不覆盖旧文）；对应向量隐私快照同步为 0。

---

## 5. 已知缺口（按"会不会真出事"排序）

### P0 · 上 Flutter 之前必须补

| 缺口 | 现状 | 验收 |
|---|---|---|
| **后端没有 CORS** | Flutter Web / `flutter run -d chrome` 跨源请求会被浏览器拦 | 开发期允许 `localhost:*` / `127.0.0.1:*`；生产只放行正式域名 |
| **登录失效判定** | 业务错误 HTTP 200、`code=401` 才算掉线，客户端容易误判 | Flutter 侧统一拦截器：401 清 token 跳登录，403 只提示 |
| **公开记忆无封面图鉴权例外** | privacy=0 照片允许匿名，但客户端拼 token 也要兼容 | 帖子封面在已登录/未登录两种状态都能显示 |
| **AI 供应商可用性未复验** | 旧 Key 已迁入，迁移后未登录实测 | 登录后 `/idea/{id}/analyze` 返回 `degraded=0` |

### P1 · 功能缺口

- **5 分钟撤回窗口未做**（R2）：现在只有二次确认，公开后靠手动改回私密。
- **`needs_review` 没有合并页**：Web 只显示"待合并"标签，没有"对比记忆版 / 文章版并合并"的界面。
- **相册能力空转**：`memory_photo` 有数据（13 条历史），前端没有相册/按时间归集页面。
- **年度报告未做**（第 4 阶段）。
- **关系图谱未做**（`idea_relation` 表在，算法后置）。
- **旧库下线未执行**：验证 30 天无回滚需求后再停。

### P2 · 小瑕疵

- `create_time` 用 DB `now()`、`update_time` 用 JVM 时钟，极端情况下会出现 `update_time < create_time`。
- `BlogArticleCategory` / `BlogArticleTag` 无 `@TableId`，启动有 warn，`xxById` 不可用。
- `AiEditor.vue` 里仍有一枚硬编码 `sk-`，前端产物可见；应改为走后端代理（计划见 §7 M3）。

---

## 6. Flutter 蓝图（本轮新建，一个 App 两个 Tab）

### 6.1 工程与依赖

```
apps/shiguang/
├── lib/
│   ├── core/
│   │   ├── config/app_config.dart        # baseUrl、超时、开关（默认 http://localhost:8080）
│   │   ├── network/api_client.dart       # JWT / X-Sealed-Token / Result 解包 / 错误映射
│   │   ├── theme/app_theme.dart          # 参照 docs/design/linear-style.md 的 token
│   │   └── storage/token_store.dart       # MVP: shared_preferences；移动端换 secure_storage
│   ├── data/
│   │   ├── services/                     # auth / memory / idea / public
│   │   ├── models/                       # 按 backend VO 一比一建
│   │   └── repositories/
│   ├── domain/entities/
│   └── presentation/
│       ├── shell/app_shell.dart          # 桌面: NavigationRail；移动: NavigationBar
│       ├── auth/                         # 登录 / 注册
│       ├── shiguang/                     # 时光轴、碎念、详情、对话、PIN、情绪
│       └── niansheng/                    # 六态看板、卡片、迁移、AI 抽屉
└── test/
```

- 状态管理：`provider` + `ChangeNotifier`（与 `addtoblog` 的分层习惯一致，不引入更重的方案）。
- 网络：`http`（不引 dio，够用）；统一拦截：`code==200` 取 `data`，`code==401` 清 token，其余弹错误。
- 存储：桌面/Web 先用 `shared_preferences`；Android 阶段换 `flutter_secure_storage`。
- 平台：**Windows 桌面 + Web 优先**，Android 次之，iOS 最后。

### 6.2 两个 Tab 的职责

| Tab | 页面 | 依赖接口 |
|---|---|---|
| 拾光 | 时光轴 / 那年今日 / 碎念快速输入 / 记忆详情 / 对话 / 凝聚 / 编辑 / 隐私与 PIN / 情绪统计 / 公开记忆浏览 | `/api/memory/**`、`/api/public/memories/**`、`/api/memory/photo/{id}` |
| 念生 | 六态看板 / 新建编辑 / 拖拽迁移 / 迁移历史 / AI 分析·质疑 / 闪光点升级 | `/api/idea/**` |
| 我的 | 账号信息 / 封存 PIN 设置 / 退出 | `/api/memory/pin/**`、`/api/auth/**` |

### 6.3 关键实现约定

1. **照片**：`Image.network(url + '?token=' + jwt)`；`storage=2` 的历史 URL 不强加 token。
2. **封存**：详情/对话前先查 `/pin/status`；未解锁弹 PIN；令牌只存内存 + `shared_preferences` 二选一（跨页保持），改 PIN 后立即清。
3. **公开二次确认**：设为 `privacy=0` 前必须弹"会立即发布到博客公开列表"，与 Web 一致。
4. **拖拽迁移**：先查 `allowedTargets` 再允许拖入；后端 409 时回弹并提示原因。
5. **降级展示**：AI 返回 `degraded=1` 时显示提示 + 原始文本，不当作失败。
6. **留白**：对话不自动追问；用户只发照片可只存档。

---

## 7. 分阶段计划（以 Flutter 为主线）

### M0 · 地基（先做，1 周内）
| 交付 | 验收 |
|---|---|
| 后端 CORS（`WebMvcConfigurer` 或 Security CORS） | `flutter run -d chrome` 登录成功，无跨源报错 |
| `flutter create apps/shiguang --platforms=windows,web,android,ios` | Windows / Web 均能启动 |
| App 壳 + 主题 + 双 Tab + 登录注册 + Token 存储 | 登录后进入拾光；重启仍保持登录 |
| `api_client` 统一解包与错误映射 | 401 自动回登录；403 常驻不退出 |

### M1 · 拾光 MVP（2 周）
| 交付 | 验收 |
|---|---|
| 时光轴（年月分组）+ 那年今日 | 数据与 Web 管理端一致 |
| 碎念快速输入（<3 秒落地） | 新建后列表/时光轴立即出现 |
| 记忆详情：对话（验证码）/ 凝聚 / 编辑 / 删除 | 对话有召回提示；凝聚出日记 |
| 隐私切换 + PIN 门 + 封存列表占位 | 未解锁看不到封存；解锁 5 分钟后自动失效 |
| 照片鉴权加载 + 情绪分布 | 私密照片未登录不可见 |
| 公开记忆浏览（只读） | 未登录可看 `privacy=0` 记忆 |

### M2 · 念生 MVP（1.5 周）
| 交付 | 验收 |
|---|---|
| 六列看板（桌面横向、移动纵向分页） | 6 列与 `idea_state` 一致 |
| 新建 / 编辑 / 删除 / 优先级 | 与 Web 管理端数据一致 |
| 拖拽迁移 + 历史时间线 | 非法迁移被拒并提示；历史可回放 |
| AI 分析 / 质疑抽屉 | 结构化分区展示；`degraded` 显示原始文本 |
| 闪光点升级为想法 | 迁移后 `sourceInsightId` 非空 |

### M3 · 体验与整合（2 周）
- 情绪呼吸粒子（CustomPainter，随情绪色带变化）、留白对话动效、三级沉淀视觉。
- 记忆 → 文章入口（`/memory/publish`）、公开文章跳转。
- 离线读缓存（D10：**只读不写**）；图片本地缓存。
- Web 管理端补"待合并"合并页；AiEditor 改走后端代理，清掉硬编码 Key。

### M4 · 移动端（2 周）
- Android：`flutter_secure_storage`、`FLAG_SECURE`、返回手势、相册权限。
- iOS：同等隐私门 + 生物识别降级到 PIN。
- 桌面 / Web 回归；移动端弱网与断线重试。

> 每个 M 结束都要有一次可回归的验收（见上表），不把"能点开"当完成。

---

## 8. 怎么跑

```bash
# 后端（默认连 Supabase 会话池，8080）
cd backend
mvn spring-boot:run

# Web 管理端（vite 代理固定指向 localhost:8080）
cd frontend
npm install && npm run dev

# Flutter（M0 之后）
cd apps/shiguang
flutter pub get
flutter run -d windows     # 桌面优先
flutter run -d chrome      # 需后端 CORS
```

数据库结构只有三处真源：`V1__baseline.sql`、`V2__projection_and_review.sql`、`V3__access_log_nullable_memory.sql`。**不要手改库**，后续变更一律 `V4__*.sql`。

---

## 9. 红线约定（动代码前先读）

1. **schema 只认 Flyway**：V1~V3 已执行，新增写 V4；不要改已执行文件。
2. **向量列不进实体**：一律 `Mapper` 显式 SQL + `#{vec}::vector`。
3. **隐私只走 `MemoryVisibilityService`**：业务代码禁止手写 `privacy` 条件。
4. **投影只走 `MemoryProjectionService`**：不要再在别处直接 `insert blog_article` 做发布。
5. **念生状态只走 `IdeaStateMachineService`**：迁移合法性由表决定，不在代码里写死边。
6. **启动期不查库**：用 `ApplicationRunner`，别用 `@PostConstruct`（会抢在 Flyway 之前）。
7. **密钥不进前端**：前端不得出现 `sk-`；后端密钥只放 `application.yml` 或后台配置表。
8. **照片新数据必须受控**：不得再把新私密照片传公网图床；历史 `storage=2` 是既定事实，不回滚。

---

## 10. 风险与开放问题

| # | 风险 | 影响 | 缓解 |
|---|---|---|---|
| 1 | Flutter Web 的 CORS / 图片跨源 | M0 卡住 | 先做后端 CORS，再开 Web |
| 2 | 一个 App 两个域容易耦合 | 后期难拆 | `presentation/shiguang` 与 `niansheng` 不互引，共享层只放 core / data |
| 3 | 照片 URL 带 JWT | token 可能进日志 | 短期接受；后续改短时签名 URL |
| 4 | 零自动化测试 | 每次改动靠手点 | 先补后端越权矩阵 + Flutter widget 冒烟 |
| 5 | AI 输出不稳定 | 分析页空白 | `degraded=1` 必须可视，不能让用户以为坏了 |
| 6 | 桌面 Web 先行、移动端后补 | 安全能力（安全存储/截屏保护）延后 | M4 前不在移动端存任何敏感 token 明文 |

**待确认（不阻塞 M0）**：
- 单 App 的产品名与图标（暂用「拾光」）。
- 是否保留"公开记忆"在客户端浏览（当前接口已就绪，默认做只读页）。
- 是否要把 `addtoblog` 合并进新 App（暂不合并，保持独立）。

---

## 附 · 关键文件地图

```
backend/src/main/resources/db/migration/V1__baseline.sql     结构基线（25 表）
                                       V2__projection_and_review.sql
                                       V3__access_log_nullable_memory.sql
backend/src/main/java/com/blog/
  service/MemoryProjectionService.java        记忆→文章投影（D12/D13）
  service/impl/MemoryVisibilityService.java   隐私唯一收口（D2/D14）
  service/impl/MemoryPinService.java          封存 PIN / 令牌 / 锁定
  service/impl/MemoryPhotoService.java        照片受控存储
  controller/PublicMemoryController.java      公开记忆读面
  service/impl/IdeaService.java               念生看板 / AI / 流水
  service/impl/IdeaStateMachineService.java   六态迁移校验
  service/impl/IdeaPromptService.java         Prompt 版本化
frontend/src/views/admin/memory/index.vue     记忆管理（PIN/封存/隐私）
frontend/src/views/admin/articles/index.vue   文章管理（待合并）
frontend/src/views/admin/ideas/index.vue      念生看板
docs/product/shiguang-product-spec.md         拾光产品定位（保留）
docs/design/linear-style.md                   视觉约定
```
