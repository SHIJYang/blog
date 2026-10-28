# backend · Spring Boot 统一底座

博客公开端、拾光碎念、念生共用同一个 Spring Boot 应用。**一个进程、一个库、一套鉴权。**

| 项 | 版本 |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.6 |
| ORM | MyBatis-Plus 3.5.15（**已移除 PageHelper**，改用 MP 分页插件） |
| 数据库 | **PostgreSQL 17 + pgvector** |
| 迁移 | Flyway（脚本在 `src/main/resources/db/migration/`） |
| 鉴权 | JWT 无状态（JJWT 0.13.0）+ BCrypt |

代码结构（保持原样，未做无谓重构）：

```
controller（18 个，72 个端点）
  ├── /api/auth/*       登录注册
  ├── /api/public/*     匿名可读：文章、分类、标签、评论、配置、友链、记忆（待加）
  ├── /api/admin/*      管理端
  ├── /api/memory/*     拾光（20 个端点）
  └── /api/upload/*     上传
service → service/impl → mapper（MyBatis-Plus）→ PostgreSQL
```

---

## 数据库：本地 docker PG / 生产 Supabase 托管 PG

同一套驱动、同一份 Flyway 脚本，只有连接串不同：

| 环境 | 连哪里 | 怎么配 |
|---|---|---|
| 当前（默认） | Supabase 托管 PostgreSQL 17 | 已写在 `application.yml` 的 `spring.datasource` |
| 本地备选 | `docker compose up -d`（PG17 + pgvector，库/用户/密码都是 `blog`） | 把 `url` 换成 `jdbc:postgresql://localhost:5432/blog`，用户/密码改 `blog` |

### Supabase 官方那三步文档，在本项目的落法

文档给的 `@supabase/server` 是 **Node / Deno / Cloudflare Workers 的包**（用 JWKS 验签用户 JWT、挂好 supabase-js 客户端）。
本项目后端是 Java 21 + Maven，仓里没有任何 node 服务端，**装它不会产生任何效果**。逐条对应：

| 文档步骤 | 本项目 |
|---|---|
| 1. `npm install @supabase/server` | **不适用**。Java 侧等价物就一个 JDBC 驱动 `org.postgresql:postgresql`，已加进 pom |
| 2. 环境变量 | 不使用。连接串、账号、JWT secret 统一写在 `application.yml` |
| 3. `npx skills add supabase/server` | **不装**。该 skill 教的是 Node 侧写 API 的姿势，与 Spring Boot 无关 |

`SUPABASE_PUBLISHABLE_KEY` / `SUPABASE_SECRET_KEY` / `SUPABASE_JWKS_URL` 目前**没有代码读取**——
本项目只把 Supabase 当托管 PG，鉴权仍是自签 JWT（JJWT + BCrypt）。留占位是为了将来切 Auth 时不用再翻文档。

### 生产连接串（实测结论，别照抄文档）

```
DB_URL=jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require
DB_USER=postgres.tvohmylewzftfpczikpn
DB_PASSWORD=<Database Password>
```

三个坑都踩过：

1. **直连（Dashboard 里那条 `db.<ref>.supabase.co:5432`）在本机不通**：该域名只解析出 AAAA
   （`2406:da12:...`），没有 A 记录，实测 `connection timeout expired`。
   只有两种情况能用直连：机器有可用 IPv6 出口，或买了 Supabase 的 **IPv4 add-on**（换一个 IPv4 直连地址）。
   直连模式下用户名是 `postgres`（**不带** ref），凭据别和 pooler 混着抄。
2. **必须用 5432 会话模式，不能用 6543 事务模式**。Flyway 迁移依赖会话级 advisory lock，
   事务模式下会报 `advisory locks are not supported`。应用日常连接也用 5432。
3. **密码 ≠ API Key**。`sb_secret_xxx` 是服务端 API Key，不是数据库密码，
   数据库密码在 Dashboard → Project Settings → Database。

> pooler 区域是探测出来的：对 `aws-0-<region>.pooler.supabase.com` 逐个用错误密码握手，
> 只有 `ap-northeast-2` 返回 `password authentication failed`（租户存在），其余区域都是
> `tenant/user not found`。以后换项目/换区域请以 Dashboard → Connect 显示为准。

### 首次接通后的自检

```bash
# 1) 能连上、扩展在不在
psql "postgresql://postgres.tvohmylewzftfpczikpn:<密码>@aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require" \
     -c "select extname, extversion from pg_extension order by 1;"

# 2) 启动应用，Flyway 会自动执行 V1__baseline.sql 建 25 张表
#    验证：select count(*) from information_schema.tables where table_schema='public';
```

若 `CREATE EXTENSION vector` 报权限错，Supabase 的惯例是把扩展装进 `extensions` schema，
在 SQL Editor 里手动跑一次即可（V1 里的语句本身可以保留）：

```sql
create extension if not exists vector with schema extensions;
create extension if not exists pg_trgm with schema extensions;
```

---

## 向量层：已与 V1 的 `vector(1024)` 对齐

原先代码是 MySQL 时代的写法（`vector_json` text 列 + Java 侧全量余弦），在 V1 的表上会直接报列不存在。
现在改成「向量列不进实体，全部走显式 SQL」：

| 关注点 | 做法 |
|---|---|
| 写入 | `MemoryVectorMapper#upsertEmbedding` / `MemoryInsightMapper#insertWithVector`，参数由 `VectorUtil.toJson()` 产出，SQL 里 `#{embedding}::vector` 转型 |
| 覆盖 | `ON CONFLICT (user_id, memory_id) DO UPDATE`，取代原 delete+insert（避免间隙锁，也不再烧自增序列——生产库自增涨到 2963 而记忆只有 38 条） |
| 召回 | `ORDER BY embedding <=> #{vec}::vector` 让 DB 先筛（走 `idx_emb_hnsw` 部分索引，`privacy <> 2`），Java 只叠加情绪加成与时序衰减 |
| 实体 | `MemoryEmbedding` / `MemoryInsight` 里没有 `embedding` 字段，MP 通用 insert 碰不到向量列 |
| 降级 | Embedding 服务不可用 → `upsertSnippetOnly`（`embedding = NULL`），关键词召回仍可用；HNSW 自动跳过 NULL |

因此 **pom 里不需要 `com.pgvector:pgvector`** —— 字符串直传 + 显式转型就够，少一个依赖、少一层误用空间。

> `V1` 里 `memory_embedding.embedding` 由此改为 **可空**（原为 NOT NULL，会让降级路径直接插不进去）。

### 实测记录（Supabase PG 17.11 + pgvector 0.8.2）

在临时 schema `v1_check` 里把 V1 的 64 条语句逐条执行（跑完 drop，`public` 未受影响）：

- **64/64 通过**，建出 25 张表、65 个索引（含主键/唯一约束索引）、3 个 HNSW（`idx_emb_hnsw` / `idx_insight_hnsw` / `idx_idea_hnsw`）。
- `upsertEmbedding` 连插两次同一 `(user_id, memory_id)` → 仍是 1 行，字段被覆盖，自增不烧。
- 召回：同向量的那条 `score = 1.0`，`privacy = 2` 的记忆不出现。
- **踩到一个真 bug 并修掉**：`WHERE privacy <> 2 ORDER BY <=> LIMIT n` 在**顺序扫描**下会把
  `embedding IS NULL` 的降级行也带出来（`score` 为 NULL）。我先前以为"HNSW 索引不含 NULL"就够，
  实测不成立 —— 小表根本不走索引。现在 SQL 里显式加了 `embedding IS NOT NULL`。
- `memory_note.emotions` 的 `text[]` 写入/读回/更新均正常（`{松弛,治愈}` ↔ `松弛,治愈`）。
- `vector` 扩展装在 `extensions` schema（Supabase 惯例），V1 的 `CREATE EXTENSION IF NOT EXISTS vector` 会 no-op，
  不会把类型塞进 `public` 触发它的 linter 告警。

**关于 HNSW 的规模提醒**：几百行时 planner 选 `idx_emb_user_privacy` + top-N 排序，结果**精确**；
强制关掉 `enable_sort/seqscan` 才走 `idx_emb_hnsw`（说明索引本身可用）。因为 HNSW 不支持按 `user_id` 过滤，
索引扫描返回近邻后还要回表筛 `user_id`，**多用户且数据量大时可能不足 TopN**。
真到那个规模再开 `SET hnsw.iterative_scan = relaxed_order`，现在不必操心。

---

## 配置

仓库里的 `application.yml` **只有占位符**，真实连接串与密钥放在本地 `backend/application-local.yml`（已被 `.gitignore` 排除）：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://aws-0-ap-northeast-2.pooler.supabase.com:5432/postgres?sslmode=require
    username: postgres.<project-ref>
    password: <Database Password>
jwt:
  secret: <至少 32 字节的随机串>
```

`application.yml` 通过 `spring.config.import` 自动读取它，从 `backend/` 或仓库根目录启动都能找到。
本地 docker PG 是备选方案：起容器后把 `url` 换成 `jdbc:postgresql://localhost:5432/blog`，用户/密码改 `blog`。

---

## 建库与迁移

```bash
# 当前：连接串已写在 application.yml（Supabase），启动应用即可
# 本地备选：仓库根目录 docker compose up -d（PG17 + pgvector，库名/用户/密码都是 blog）

# 两种情况都是启动时 Flyway 自动执行 V1__baseline.sql，建出 25 张表
```

schema 的**唯一真源**是 `src/main/resources/db/migration/V1__baseline.sql`。
任何结构变更都新增 `V2__xxx.sql`，**不要手改数据库**。

---

## 首次启动实录：四个只有真跑才会暴露的坑

2026-10-27 用 Supabase PG17 真跑了一遍（`mvn spring-boot:run` → Flyway 建表 → 注册/登录/写记忆），
空库启动直接卡在前两个坑上。这些都不是「代码写得不对」，是**框架版本/驱动行为**，务必别退回去：

| # | 现象 | 真因 | 处理 |
|---|---|---|---|
| 1 | 启动报 `relation "ai_provider" does not exist`，日志里**完全看不到 Flyway** | Spring Boot 4 把自动配置拆成了独立模块（`spring-boot-jdbc` / `-sql` / `-webmvc` …），只加 `flyway-core` 拿到的是"库"不是"自动配置" | pom 里必须加 `org.springframework.boot:spring-boot-flyway` |
| 2 | 登录 401「用户名或密码错误」，但密码明明是对的 | pgjdbc 拒绝 `timestamptz` → `LocalDateTime`：`Cannot convert the column of type TIMESTAMPTZ to requested type java.time.LocalDateTime`。**空表不报错**（没有行就不触发类型转换），一有数据全崩 | `config/mybatis/PgTimestamptzTypeHandler` + `mybatis-plus.type-handlers-package` |
| 3 | 空库首启炸在某个 Bean 的初始化 | `AiProviderController#init` 用 `@PostConstruct` 查库，与 `FlywayMigrationInitializer` 没有顺序保证 | 挪到 `config/StartupTasksConfig` 的 `ApplicationRunner`（refresh 之后才跑） |
| 4 | `update_time` 可能早于 `create_time`（实测差 6 秒） | `create_time` 取 DB 的 `now()`，`update_time` 取 JVM 时钟，两边有 NTP 偏差 | 已知小瑕疵，暂不处理；要严格单调就把两边统一到同一个来源 |

> 第 2 条是最隐蔽的：`/api/public/articles/page` 之类的接口在空库上返回 200、`records: []`，
> 看着一切正常，直到库里出现第一行数据。**空库 smoke test 不能证明 ORM 映射是对的。**

验证通过的清单（同一台机器，同一套配置）：

```
Flyway  : Successfully applied 1 migration to schema "public", now at version v1
表/索引 : 26 张表（25 + flyway_schema_history）、67 索引、3 个 HNSW
种子数据 : ai_provider = Agnes(启用) + DeepSeek(停用)，由 ApplicationRunner 写入
接口    : /api/auth/register 200 · /api/auth/login 200(返回 JWT) · /api/memory/list 200
          /api/memory/update 200（emotions 数组写入落成 text[]：{松弛,治愈}）
          /api/memory/emotion/stats 200（数组读回并聚合）
          /api/memory/list 无 token → 403（鉴权生效）
```

---

## 需要注意的实现约定

1. **`update_time` 不再靠数据库**（PG 没有 `ON UPDATE CURRENT_TIMESTAMP`）。
   8 个实体的 `updateTime` 都已标 `@TableField(fill = FieldFill.INSERT_UPDATE)`，
   由 `config/UpdateTimeMetaObjectHandler` 统一回填。新增带 `update_time` 的表别忘加这行注解。
2. **向量列**是 `vector(1024)`，且**不进实体**。读写都写在 Mapper 注解里，
   参数用 `#{vec}::vector` 形式显式转型；旧的 `vector_json` 字段已删除。
   别再往实体里加 `embedding` 字段——JDBC 会把 String 当 varchar 传，PG 直接报类型不匹配。
3. **`text[]` 列在 Java 侧仍是逗号串**。`memory_note.emotions`、`memory_emotion.aliases`
   通过 `config/typehandler/PgTextArrayTypeHandler` 双向转换，实体上必须
   `@TableName(value = "xxx", autoResultMap = true)`，否则 MP 走自动映射、读出来是 `{a,b}` 原文。
   另外：**不要拿 `text[]` 列去和 `""` 比较**（`ne(x, "")`），PG 会报类型错，空值用 Java 侧 `isBlank` 过滤。
4. **分页方言必须是 POSTGRE_SQL**（`MyBatisPlusConfig`）。MySQL 方言生成 `LIMIT ?, ?`，PG 只认 `LIMIT ? OFFSET ?`。
5. **会话时区统一 `Asia/Shanghai`**（`spring.datasource.hikari.connection-init-sql`）。
   实体是 `LocalDateTime`、列是 `timestamptz`，两端时区不一致会整体偏 8 小时。
6. **隐私过滤不允许写在业务代码里**。封存（`privacy=2`）目前落在 Mapper 的 SQL 条件与 HNSW 部分索引上，
   下一步是抽到 MyBatis 拦截器统一注入；业务查询只表达"我要什么"，不表达"我要不要藏"。
7. **召回逻辑保留三通道**（余弦 + 情绪加成 + 时序衰减），但取数改为
   `ORDER BY embedding <=> ?` 让数据库先筛，再在内存做加权。
