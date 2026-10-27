# Blog · 三端单仓

个人内容系统，一个仓库、一条 `main`、三端共存：

| 目录 | 是什么 | 技术栈 | 状态 |
|---|---|---|---|
| `frontend/` | **公开创作端**（博客前台 + 管理后台） | Vue 3.5 · Vite 8 · Element Plus · Pinia | 在跑 |
| `backend/` | **统一底座**（博客 + 拾光 + 念生 同一个 Spring Boot） | Java 21 · Spring Boot 4.0.6 · MyBatis-Plus · PostgreSQL 16 | 在跑（库待切） |
| `apps/addtoblog/` | **Markdown 发布器**（桌面端，拖文件发博客） | Flutter | 可用，独立演进 |
| `archive/android-memory/` | 旧 Android 客户端（已下线，仅存档参考） | Kotlin + ViewBinding | **只读，不改** |

```
docs/                        架构与调研文档（先看这里）
├── upgrade-research-report.md        升级调研与实施方案（决策 D1~D16、风险、路线）
├── product/shiguang-product-spec.md  拾光碎念产品定位（原 frontend/kaifa.txt）
├── design/linear-style.md            视觉风格约定（原 frontend/DESIGN_LINEAR.md）
└── legacy/                           历史产物（旧 OpenAPI 快照、MySQL 16 表基线）
```

---

## 三个空间的关系

```
        公开创作                      私密生活
   ┌──────────────┐            ┌───────────────────────┐
   │  博客 article │◄───投影────│  拾光 memory_note      │  privacy 0/1/2
   │  （公开可读）  │            │  情绪 / 时光轴 / 照片   │
   └──────────────┘            └───────────────────────┘
                                        ▲ 升级为想法
                                 ┌───────────────────────┐
                                 │  念生 idea（六态状态机） │
                                 │  AI 分析 / 质疑         │
                                 └───────────────────────┘
```

**隐私模型（核心约束，读代码前必须知道）**

| privacy | 含义 | 谁能看 | AI 能否召回 |
|---|---|---|---|
| `0` 公开 | 立即可见，**同时投影为博客文章** | 任何人（含未登录） | 能 |
| `1` 私密 | 仅本人 | 本人 | 能（温和关联） |
| `2` 封存 | **连本人也要输全局 PIN 才可见** | 本人 + PIN 解锁令牌 | **永不** |

> 封存照片同样受控：新写入的私密图片必须走 `GET /api/memory/photo/{id}`，不允许直传公网图床。
> 历史图片已在图床公开，接受既成事实（决策 D5）。

---

## 本地启动

```bash
# 1) 起数据库（PostgreSQL 16 + pgvector）
docker compose up -d

# 2) 后端（需 JDK 21；Flyway 启动时建表）
cd backend
# 复制 application-local.yml 模板并填连接串（该文件已被 .gitignore 排除）
# IDEA 里运行 BlogApplication

# 3) 前端
cd frontend
npm install
npm run dev     # 默认代理远程后端；改 .env.development 的 VITE_API_TARGET 可指向本地 8080

# 4) 发布器（可选）
cd apps/addtoblog
flutter pub get && flutter run -d windows
```

数据库结构只有一处真源：`backend/src/main/resources/db/migration/V1__baseline.sql`（25 表）。
**不要手改库结构**——所有变更写成 `V2__xxx.sql`，由 Flyway 推进。

---

## 部署（仅前端）

服务器上的自动发布脚本在 `scripts/deploy-sjy.sh`，随仓库版本管理——改了脚本不用再手工 scp 到服务器，
下次部署会用它自己的新版（脚本会把自己复制到 `/var/lib/deploy-sjy/` 再执行，避免被 `git reset` 覆盖）。

```bash
# 服务器上首次放置（之后随仓库自动更新）
mkdir -p /www/git/blog/scripts
curl -fsSL https://raw.githubusercontent.com/SHIJYang/blog/main/scripts/deploy-sjy.sh \
     -o /www/git/blog/scripts/deploy-sjy.sh

# 计划任务里
bash /www/git/blog/scripts/deploy-sjy.sh
```

它按 `frontend/` 子树的 hash 判断是否需要重建，所以**改后端或文档不会触发前端重新构建**。
后端上线不归这个脚本管。细节见脚本头部注释（`--help` 可看参数）。

## 分支约定

- **`main` 是唯一开发分支**，前后端同树，一次提交可同时改接口和页面。
- 功能开发开短命分支（`feat/xxx`），合回 `main` 后删除。
- 历史分支 `backend` / `flutter` / `memory` **已并入 `main`，保留只作历史查询**，不再向其提交。

> 为什么合并：见 `docs/upgrade-research-report.md` §2.4。简版——分离时旧 OpenAPI 快照已漂了 22 个端点没人发现，而合并实测只需处理 19 处 `modify/delete` 冲突。

---

## 还没做完（按顺序）

1. **`pom.xml` 加依赖**（唯一需要你手工确认的一步，我在此环境无法编译验证）：
   `spring-boot-starter-jdbc`、`org.postgresql:postgresql`、`flyway-core`、`flyway-database-postgresql`、`com.pgvector:pgvector`，并把 `application.yml` 驱动换成 `org.postgresql.Driver`。
2. **数据回填**：MySQL 现有 4 用户 / 18 文章 / 38 记忆 / 50 消息 / 89 闪光点 → PG，向量重算（脚本放 `tools/migrate/`）。
3. **隐私拦截器**：MyBatis 层统一注入 `privacy <> 2`，业务代码禁止手写该条件。
4. 其余排期见报告 §9。

---

## 已知问题（先记着，别踩）

- 3 处前端硬编码 `sk-` 密钥（`AiEditor.vue`、`agnesImage.vue`、`agnesVideo.vue`）——**需轮换**，且已进 Git 历史。
- `memory_emotion.count` 每次归并会累加，情绪分布图数字虚高（V1 已删该列、改实时聚合，但**历史数据需清洗**）。
- 归档 Android 代码里 `build.gradle.kts` 有明文 keystore 口令，**随目录作废，勿再使用**。
