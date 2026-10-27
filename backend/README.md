# backend · Spring Boot 统一底座

博客公开端、拾光碎念、念生共用同一个 Spring Boot 应用。**一个进程、一个库、一套鉴权。**

| 项 | 版本 |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.6 |
| ORM | MyBatis-Plus 3.5.15（**已移除 PageHelper**，改用 MP 分页插件） |
| 数据库 | **PostgreSQL 16 + pgvector** |
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

## 启动前必须做的一件事：补 pom 依赖

当前 `pom.xml` 仍是 MySQL 时代的样子，**驱动和 Flyway 都没加**（我无法在此环境编译验证，所以没有擅自改）。请在 `pom.xml` 的 `<dependencies>` 里加入：

```xml
<!-- PostgreSQL 驱动 -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- pgvector 的 JDBC 类型映射（写入/读取 vector 列） -->
<dependency>
    <groupId>com.pgvector</groupId>
    <artifactId>pgvector</artifactId>
    <version>0.1.6</version>
</dependency>

<!-- Flyway -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

同时把下面这条从 `pom.xml` 删掉（或注释）：

```xml
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>
```

> 若 Flyway 与 Spring Boot 4.0.6 版本不匹配，锁定 `flyway.version` 到 Flyway 正式支持 Boot 4 的版本即可（这是本方案剩下的唯一技术不确定点）。

---

## 配置

复制模板并填入本地值（`application-local.yml` 已被 `.gitignore` 排除，不会进仓库）：

```bash
cp src/main/resources/application-local.yml.example src/main/resources/application-local.yml
```

`application.yml` 里仍需把驱动改掉：

```yaml
spring:
  datasource:
    driver-class-name: org.postgresql.Driver   # 原来是 com.mysql.cj.jdbc.Driver
```

---

## 建库与迁移

```bash
# 仓库根目录
docker compose up -d          # PostgreSQL 16 + pgvector，库名/用户/密码都是 blog

# 启动应用时 Flyway 自动执行 V1__baseline.sql，建出 25 张表
```

schema 的**唯一真源**是 `src/main/resources/db/migration/V1__baseline.sql`。
任何结构变更都新增 `V2__xxx.sql`，**不要手改数据库**。

---

## 需要注意的实现约定

1. **`update_time` 不再靠数据库**（PG 没有 `ON UPDATE CURRENT_TIMESTAMP`）。
   实体上统一加：
   ```java
   @TableField(fill = FieldFill.INSERT_UPDATE)
   private LocalDateTime updateTime;
   ```
   并注册 `MetaObjectHandler`。
2. **向量列**是 `vector(1024)`，不是字符串。写库用 pgvector 的 `PGvector` 对象，
   禁止再序列化成 JSON 文本（旧 `vector_json` 字段已废弃）。
3. **隐私过滤不允许写在业务代码里**。封存（`privacy=2`）必须在 MyBatis 拦截器层统一拦截，
   业务查询只表达"我要什么"，不表达"我要不要藏"。
4. **召回逻辑保留三通道**（余弦 + 情绪加成 + 时序衰减），但取数改为
   `ORDER BY embedding <=> ?` 让数据库先筛，再在内存做加权。
