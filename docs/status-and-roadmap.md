# 阶段进展与后续开发

> 更新：2026-10-27 · 适用对象：接手这个仓库的人（含三个月后的自己）
> 结论先说：**后端已经真正跑起来了**——JDK 21 编译通过、Flyway 在 Supabase PG17 上建出 25 张表、
> 注册/登录/写记忆全链路实测通过。博客域可用，拾光域向量链路已对齐并验证，念生域仍是 0 代码。

---

## 1. 现在能跑什么（不是"应该能跑"，是实测过）

| 项 | 状态 | 证据 |
|---|---|---|
| 后端编译 | ✅ | `mvn compile`：121 个源文件，BUILD SUCCESS |
| 后端启动 | ✅ | `Started BlogApplication in 5.89 seconds` |
| 数据库迁移 | ✅ | 首启 `Successfully applied 1 migration to schema "public", now at version v1`（3.4s） |
| 库结构 | ✅ | 26 张表（25 业务表 + `flyway_schema_history`）、67 个索引、3 个 HNSW |
| pgvector | ✅ | 0.8.2，`vector` / `pg_trgm` 装在 `extensions` schema |
| 注册 / 登录 | ✅ | `POST /api/auth/register` 200、`POST /api/auth/login` 200（返回 JWT） |
| 鉴权 | ✅ | 无 token 访问 `/api/memory/list` → 403 |
| 公开接口 | ✅ | `configs/info`、`articles/page`、`categories/list`、`tags/list`、`links/list` 均 200 |
| 写记忆情绪 | ✅ | `POST /api/memory/update` → `memory_note.emotions` 落成 `text[]` 值 `{松弛,治愈}`，读回 `松弛,治愈` |
| 情绪统计 | ✅ | `GET /api/memory/emotion/stats` → `[{治愈:1},{松弛:1}]` |
| 前端构建 | ✅（早前） | `npm run build` 通过，3574 模块 |
| 部署脚本 | ✅（线上跑过） | `scripts/deploy-sjy.sh` v2，服务器已用它发布 |
| 自动化测试 | ❌ | **零测试**，全靠手工 curl |

环境实测：`PostgreSQL 17.11`（x86_64-pc-linux）· `pgvector 0.8.2` · `pg_trgm 1.6` · JDK 21 · Maven 3.9.16 · Spring Boot 4.0.6 · MyBatis-Plus 3.5.15。

---

## 2. 这一阶段做完了什么

### 2.1 仓库：从"三套分支各存一块"到单仓三端
`frontend/`（Vue3）、`backend/`（Spring Boot）、`apps/addtoblog/`（Flutter 发布器）合并在 `main`，
`archive/android-memory/` 只读留档。文档归位到 `docs/`，部署脚本入库并支持子树变更门禁。

### 2.2 数据库：MySQL 8.0 → PostgreSQL 17（Supabase 托管）

- 结构真源换成 `backend/src/main/resources/db/migration/V1__baseline.sql`（25 表 / 30 显式索引），
  变更一律写 `V2__*.sql`，禁止手改库。
- 生产走 Supabase：**会话池** `aws-0-ap-northeast-2.pooler.supabase.com:5432`，用户名 `postgres.<ref>`，`sslmode=require`。
- 配置全部走环境变量（`DB_URL` / `DB_USER` / `DB_PASSWORD`），`backend/.env` 由 `spring.config.import` 自动读取；
  **故意不给 localhost 兜底**——宁可启动即失败，也不允许生产误连本地库。

### 2.3 向量层：从"JSON 字符串 + 内存算余弦"到"真向量列 + DB 侧 ANN"

| 关注点 | 现在的做法 |
|---|---|
| 实体 | `MemoryEmbedding` / `MemoryInsight` **没有 embedding 字段**，MP 通用 insert 碰不到向量列 |
| 写入 | 专用 SQL + `#{embedding}::vector` 显式转型，参数由 `VectorUtil.toJson()` 产出 |
| 覆盖 | `ON CONFLICT (user_id, memory_id) DO UPDATE`，取代 delete+insert（不再烧自增、不拿间隙锁） |
| 召回 | `ORDER BY embedding <=> #{vec}::vector` 交给数据库，Java 只叠情绪加成 + 时序衰减 |
| 降级 | Embedding 挂掉 → 只存摘要（`embedding = NULL`），关键词召回仍可用 |

### 2.4 PG 兼容层（原来全是 MySQL 假设）

| 问题 | 处理 |
|---|---|
| 分页方言 `DbType.MYSQL` → 生成 `LIMIT ?, ?` | 改 `POSTGRE_SQL` |
| `text[]` 列 vs 逗号串 | `PgTextArrayTypeHandler` 字段级绑定 + `autoResultMap = true` |
| `text[]` 和 `""` 比较 | 全部改成 Java 侧 `isBlank` |
| `memory_emotion.count` 列已删 | 实体删字段，次数改为实时聚合（顺带根治"累加虚高"bug） |
| PG 没有 `ON UPDATE CURRENT_TIMESTAMP` | `UpdateTimeMetaObjectHandler` + 8 个实体 `@TableField(fill)` |
| 会话时区 UTC vs JVM +8 | `hikari.connection-init-sql: SET TIME ZONE 'Asia/Shanghai'` |

### 2.5 首次真跑修掉的三个 bug（空库启动必踩）

1. **Boot 4 把自动配置拆成独立模块**：只加 `flyway-core` 时迁移**静默不执行**，必须加 `spring-boot-flyway`。
2. **pgjdbc 拒绝 `timestamptz → LocalDateTime`**：空表看不出来（没有行不触发类型转换），一有数据全崩，
   表现为登录 401。已加 `config/mybatis/PgTimestamptzTypeHandler` 全局注册。
3. **`@PostConstruct` 里查库**抢在 Flyway 之前 → 挪到 `ApplicationRunner`。

---

## 3. 已知缺口（按"出事概率 × 后果"排序）

### P0 · 会真出事

| 缺口 | 现状 | 验收标准 |
|---|---|---|
| **隐私过滤没进拦截器层** | 可见性散在 Mapper SQL 与 HNSW 部分索引里，业务手写查询可能漏；`memory` 的 list/detail 目前只按 `user_id` 过滤，没有"他人 `privacy=0` 可读"这条 | 拦截器统一注入；越权矩阵全绿（见 §5 步骤 1） |
| **`privacy=0` 不投影成博客文章** | 决策 D12 定的是"设公开即发博客"，投影服务 `MemoryPublishService` 仍是旧的单条发布逻辑，没有 `source_memory_id` / `source_rev` / `manual_edited` 的幂等与保护 | 改记忆 → 文章跟着变；人工润色过的文章不被覆盖、标 `needs_review` |
| **照片仍走本地目录 + 公网图床** | 决策 D5 要求新私密照片走受控存储，`memory_photo` 表建好了但**零代码**；`isPrivate=false` 还是硬编码 | 私密照片直链 404，带 token 才能 `GET /api/memory/photo/{id}` |
| **没有创建管理员的途径** | 注册接口把 `role` 硬编码成 `READER`，全站第一个 ADMIN 只能手工 SQL 造 | 提供引导命令或首个用户自动 ADMIN |

### P1 · 功能缺口

- **`/api/public/memories` 不存在**（决策 D14 要的"跨用户读公开记忆"没接口）。
- **念生域 0 代码**：`idea` / `idea_state` / `idea_transition` / `idea_transition_rule` / `ai_prompt` 表都在，Java 侧一个类都没有。
- **前端契约落后**：源码真实端点 72 个，`docs/legacy/api-2026-08.openapi.json` 是 50 条的旧快照。
- **零自动化测试**：连一条 `@SpringBootTest` 都没有，改一次就要手工 curl 一遍。
- **死文件**：`backend/src/main/resources/sql/*.sql` 是 MySQL 时代的 DDL，已被 V1 取代，容易误导。

### P2 · 小瑕疵

- `create_time` 取 DB `now()`、`update_time` 取 JVM 时钟，实测两机差 ~6s 时会出现 `update_time < create_time`。
- 分页参数实测 `pageSize=5` 返回的是默认 `size:10`（前端契约与后端参数名不一致）。
- `BlogArticleCategory` / `BlogArticleTag` 没有 `@TableId`，MP 启动时会 warn，`xxById` 系列方法不可用。

---

## 4. 怎么跑（本机可复制的命令）

### 4.1 环境变量（已配好，换机器时需要）
```
JAVA_HOME = C:\Program Files\jdk-21        # 已写入用户环境变量
PATH      += C:\Program Files\jdk-21\bin
Maven     = C:\Users\程卓瀚\.workbuddy\binaries\maven\apache-maven-3.9.16   # 未加 PATH，用绝对路径
```

### 4.2 三种跑法

```bash
# A. 连生产库（Supabase）：backend/.env 已填好，直接起
cd backend
export JAVA_HOME="C:\Program Files\jdk-21"
"$MAVEN/bin/mvn.cmd" spring-boot:run          # 默认 8080

# B. 只编译
"$MAVEN/bin/mvn.cmd" -Dmaven.test.skip=true compile

# C. 连本地库：仓库根目录起 docker，再用 local profile
docker compose up -d                          # PG17 + pgvector，库/用户/密码都是 blog
cp backend/src/main/resources/application-local.yml.example backend/src/main/resources/application-local.yml

# 前端
cd frontend && npm install && npm run dev
```

### 4.3 报错对照表（都真踩过）

| 报错 | 真因 | 怎么办 |
|---|---|---|
| `relation "xxx" does not exist` 且日志无 Flyway | 少了 `spring-boot-flyway` | 检查 pom 里那个依赖还在 |
| `Cannot convert the column of type TIMESTAMPTZ to requested type java.time.LocalDateTime` | 类型处理器没生效 | 检查 `mybatis-plus.type-handlers-package` 与 `PgTimestamptzTypeHandler` |
| `password authentication failed for user "postgres"` | 用 `sb_secret_*` 当密码，或池主/直连用户名混用 | 池主用 `postgres.<ref>`，直连用 `postgres`；密码是 Database Password |
| `no tenant identifier provided` | 池主用户名少了 `.<ref>` | 补上项目 ref |
| 连接 `db.<ref>.supabase.co` 超时 | 该域名**只有 AAAA**，本机无 IPv6 出口 | 改用会话池，或买 Supabase IPv4 add-on |
| `Port xxxxx was already in use` | 环境里有 `SERVER__PORT` 之类变量 | `--server.port=18080` 显式指定 |

---

## 5. 建议的继续开发顺序

每一步都写清"改哪里 / 怎么验收"，做完能独立上线，不互相阻塞。

### 步骤 1 · 隐私与投影（P0，最该先做）
改：`backend/.../config/`（新增 MyBatis 拦截器）· `MemoryQueryService` / `MemoryCoreService` · 新增 `MemoryProjectionService` · `PublicMemoryController`。
验收（curl 矩阵，缺一条不算完）：
- 未登录：只看到 `privacy=0`；`privacy=1/2` 一律 404
- 他人 token：能看到你的 `privacy=0`，看不到 `privacy=1`
- 本人：全部可见；`privacy=2` 必须带 PIN 解锁令牌
- 改一条 `privacy=0` 的记忆正文 → 对应 `blog_article` 同步更新；`manual_edited=1` 时不覆盖、标 `needs_review`

### 步骤 2 · 照片受控存储
改：`FileUploadController` · 新增 `MemoryPhotoService` · `GET /api/memory/photo/{id}` 鉴权流式输出。
验收：私密照片直链 404；本人带 token 200；`storage=2` 的历史图床图仍能显示（D5 既成事实）。

### 步骤 3 · 前端对齐 + 公开记忆
改：`frontend/src/api/*` 对齐 72 端点（用 `/v3/api-docs` 生成）· 修正分页参数名 · 接 `/api/public/memories`。
验收：登录 → 发记忆 → 设为公开 → 博客列表能看到；后台改文章正常。

### 步骤 4 · 念生域
改：新增 `entity/Idea*`、`IdeaStateMachine`（表驱动 `idea_transition_rule`）、`ai_prompt` 版本化。
验收：六态（seed/incubating/validating/acting/crystallized/dormant）迁移全部由配置驱动，
非法迁移被拒；闪光点可一键升级为 idea 且留 `source_insight_id`。

### 横切 · 工程债（可与上面并行）
- 补第一批测试：`@SpringBootTest` 起上下文 + Flyway 在 Testcontainers PG17 上跑一遍 V1（**这条能挡住 §2.5 那三个坑**）。
- 清理 `backend/src/main/resources/sql/` 两个 MySQL 死 DDL。
- CI：`mvn -B verify` + `npm run build` 两条腿。

---

## 6. 红线约定（改之前先读）

1. **schema 只认 V1**：任何结构变更写 `V2__xxx.sql`，不要手改库、不要改已执行过的迁移文件。
2. **向量列不进实体**：读写走 Mapper 显式 SQL + `::vector` 转型；别把 `embedding` 加回实体。
3. **`type-handlers-package` 里的 handler 全局生效**：新增时确认没抢注 `String` 这类高频类型
   （`PgTextArrayTypeHandler` 就是因此放在包外的）。
4. **启动期不要查库**：`@PostConstruct` 与 Flyway 无序，用 `ApplicationRunner`。
5. **密钥与密码只进 `backend/.env`**（已 gitignore），永不入库、永不进示例文件。
6. **召回 SQL 必须显式 `AND embedding IS NOT NULL`**：小表走顺序扫描，"HNSW 不含 NULL"这条保证不成立。

---

## 附 · 关键文件地图

```
backend/src/main/resources/db/migration/V1__baseline.sql   结构唯一真源（25 表）
backend/.env                                               实机连接（gitignored）
backend/src/main/java/com/blog/config/
  ├── MyBatisPlusConfig.java           分页方言 PG
  ├── UpdateTimeMetaObjectHandler.java update_time 自动回填
  ├── StartupTasksConfig.java          启动期种子（ApplicationRunner）
  ├── mybatis/PgTimestamptzTypeHandler.java   timestamptz ↔ LocalDateTime（全局）
  └── typehandler/PgTextArrayTypeHandler.java text[] ↔ 逗号串（字段级）
backend/src/main/java/com/blog/mapper/MemoryVectorMapper.java   向量读写 + ANN 召回
backend/src/main/java/com/blog/service/vector/MemoryVectorService.java  三通道加权
docs/upgrade-research-report.md     决策 D1~D16（为什么这么选）
docs/product/shiguang-product-spec.md 拾光产品定位
docs/design/linear-style.md          视觉约定
scripts/deploy-sjy.sh                前端自动发布（服务器在跑）
```
