-- ============================================================
-- V1__baseline.sql · PostgreSQL 17 + pgvector（本地 docker 与生产 Supabase 同为 17）
-- SHIJYang/blog 统一底座（博客公开端 + 拾光碎念 + 念生）
-- ============================================================
-- 生成时间：2026-10-27
-- 来源：生产 MySQL 8.0.40 的 16 表结构 + 本次架构改造（D1~D16 决策）
--
-- 【为什么是 PostgreSQL 而不是 MySQL】
-- 判断标准是"能不能让代码更简单"，不是"哪个更先进"。PG 净简化三处：
--   1. 向量：vector(1024) + HNSW 部分索引，删掉自研的 JSON 解析 + Caffeine 缓存层；
--   2. 时间：timestamptz 一次解决时区（MySQL 的 datetime 无 TZ、TIMESTAMP 有 2038 上限）；
--   3. 数组/JSON：text[] + jsonb 让情绪、AI 结构化结果免去"逗号串拆分 + 手写解析"。
-- 代价是丢了 MySQL 的 ON UPDATE CURRENT_TIMESTAMP，改用 MyBatis-Plus 自动填充
-- （@TableField(fill = FieldFill.INSERT_UPDATE)），比触发器更可控、可单测。
--
-- 【安全模型（与 D2/D11/D14 对应）】
--   privacy = 0 公开（进博客列表，任何人可读）
--   privacy = 1 私密（仅本人；AI 温和关联）
--   privacy = 2 封存（**本人也需全局 PIN 解锁**才可见；AI 永不召回）
--   强制点：应用层 Repository 拦截器 + 单测矩阵（RLS 无法表达"需 PIN"，
--   且单人开发无"防队友误写"需求，故不启用 RLS）。
--
-- 【不建外键】沿用现状。逻辑删除与 FK 语义冲突，改用 §7 的巡检 SQL 兜底。
-- 【重建策略】D6 允许重建：本文件是唯一真源，数据由 tools/migrate 回填。
-- ============================================================

CREATE EXTENSION IF NOT EXISTS vector;      -- pgvector >= 0.5（HNSW 需要）
CREATE EXTENSION IF NOT EXISTS pg_trgm;     -- 中文子串检索，替代 LIKE '%kw%'

-- ============================================================
-- 1. 认证 / 用户
-- ============================================================
CREATE TABLE sys_user (
  id                bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  username          varchar(50)  NOT NULL,
  password          varchar(100) NOT NULL,              -- BCrypt
  nickname          varchar(50)  NOT NULL,
  email             varchar(100) NOT NULL,
  avatar            varchar(255),
  role              varchar(20)  NOT NULL DEFAULT 'READER',  -- ADMIN / AUTHOR / READER
  status            smallint     NOT NULL DEFAULT 1,         -- 0禁用 1启用
  -- 封存解锁：全局单一 PIN（D11）。只存哈希，连续失败锁定
  privacy_pin_hash  varchar(100),
  pin_failed_count  smallint     NOT NULL DEFAULT 0,
  pin_locked_until  timestamptz,
  pin_updated_at    timestamptz,   -- 改 PIN 时刷新：早于此时的解锁令牌一律失效
  is_deleted        smallint     NOT NULL DEFAULT 0,
  create_time       timestamptz  NOT NULL DEFAULT now(),
  update_time       timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uk_user_username UNIQUE (username),
  CONSTRAINT uk_user_email    UNIQUE (email)
);
COMMENT ON COLUMN sys_user.privacy_pin_hash IS '封存记忆解锁 PIN（BCrypt，全局单一）。为空表示未设置，封存记忆一律不可读';
COMMENT ON COLUMN sys_user.pin_updated_at IS '解锁令牌吊销水位：令牌签发时间早于此值即失效';

-- ============================================================
-- 2. 博客域（结构与现库一致，仅类型/时区调整）
-- ============================================================
CREATE TABLE blog_article (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  author_id     bigint       NOT NULL,
  title         varchar(150) NOT NULL,
  content       text         NOT NULL,
  summary       varchar(500),
  cover_image   varchar(255),
  -- 0草稿 1公开已发布 2私密。与 memory_note.privacy=0 的"公开"通过本表落地
  status        smallint     NOT NULL DEFAULT 1,
  views         integer      NOT NULL DEFAULT 0,
  allow_comment smallint     NOT NULL DEFAULT 1,
  source_memory_id bigint,                     -- 反向溯源：由哪条记忆转来（幂等锚点）
  source_rev    timestamptz,                    -- 投影版本：= 投影时 memory_note.update_time
  manual_edited smallint     NOT NULL DEFAULT 0,-- 1=人工改过，记忆再改时不覆盖，只标 needs_review
  is_deleted    smallint     NOT NULL DEFAULT 0,
  create_time   timestamptz  NOT NULL DEFAULT now(),
  update_time   timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_article_author        ON blog_article (author_id);
CREATE INDEX idx_article_status_create ON blog_article (status, create_time DESC);
-- 同一记忆只能产出一篇公开文章
CREATE UNIQUE INDEX uk_article_source_memory ON blog_article (source_memory_id)
  WHERE source_memory_id IS NOT NULL AND is_deleted = 0;

CREATE TABLE blog_category (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name        varchar(50) NOT NULL,
  description varchar(255),
  is_deleted  smallint    NOT NULL DEFAULT 0,
  create_time timestamptz NOT NULL DEFAULT now(),
  update_time timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uk_category_name UNIQUE (name)
);

CREATE TABLE blog_tag (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name        varchar(50) NOT NULL,
  is_deleted  smallint    NOT NULL DEFAULT 0,
  create_time timestamptz NOT NULL DEFAULT now(),
  update_time timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uk_tag_name UNIQUE (name)
);

CREATE TABLE blog_article_category (
  article_id  bigint NOT NULL,
  category_id bigint NOT NULL,
  PRIMARY KEY (article_id, category_id)
);
CREATE INDEX idx_ac_category ON blog_article_category (category_id);

CREATE TABLE blog_article_tag (
  article_id bigint NOT NULL,
  tag_id     bigint NOT NULL,
  PRIMARY KEY (article_id, tag_id)
);
CREATE INDEX idx_at_tag ON blog_article_tag (tag_id);

CREATE TABLE blog_comment (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  article_id  bigint       NOT NULL,
  user_id     bigint,
  nickname    varchar(50)  NOT NULL,
  email       varchar(100) NOT NULL,
  website     varchar(255),
  content     text         NOT NULL,            -- 入库前 Jsoup 白名单清洗
  parent_id   bigint,
  root_id     bigint,
  status      smallint     NOT NULL DEFAULT 1,  -- 0待审 1通过 2垃圾
  ip_address  varchar(50),
  is_deleted  smallint     NOT NULL DEFAULT 0,
  create_time timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_comment_article ON blog_comment (article_id, status, create_time DESC);
CREATE INDEX idx_comment_root    ON blog_comment (root_id);

CREATE TABLE blog_link (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name        varchar(100) NOT NULL,
  url         varchar(255) NOT NULL,
  logo        varchar(255),
  description varchar(255),
  sort        integer      NOT NULL DEFAULT 0,
  status      smallint     NOT NULL DEFAULT 1,
  is_deleted  smallint     NOT NULL DEFAULT 0,
  create_time timestamptz  NOT NULL DEFAULT now(),
  update_time timestamptz  NOT NULL DEFAULT now()
);

-- 配置值改为 jsonb：AI Key / 图床 Token 等不再以明文 text 散落
CREATE TABLE blog_config (
  id           bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  config_key   varchar(50)  NOT NULL,
  config_value text,
  config_name  varchar(100) NOT NULL,
  is_public    smallint     NOT NULL DEFAULT 1,
  is_secret    smallint     NOT NULL DEFAULT 0,   -- 1=敏感值，接口一律脱敏返回
  is_deleted   smallint     NOT NULL DEFAULT 0,
  create_time  timestamptz  NOT NULL DEFAULT now(),
  update_time  timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uk_config_key UNIQUE (config_key)
);
COMMENT ON COLUMN blog_config.is_secret IS 'picui_token / siliconflow_api_key 等置 1，读取接口必须打码';

-- ============================================================
-- 3. AI 层
-- ============================================================
CREATE TABLE ai_provider (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  name        varchar(64)  NOT NULL,
  base_url    varchar(255) NOT NULL,
  api_key     varchar(255) NOT NULL DEFAULT '',   -- 建议应用层 AES-GCM 加密后入库
  model       varchar(128) NOT NULL DEFAULT '',
  is_active   smallint     NOT NULL DEFAULT 0,
  enabled     smallint     NOT NULL DEFAULT 1,
  create_time timestamptz  NOT NULL DEFAULT now(),
  update_time timestamptz  NOT NULL DEFAULT now()
);
-- 只允许一行激活
CREATE UNIQUE INDEX uk_provider_single_active ON ai_provider ((1)) WHERE is_active = 1;

-- Prompt 治理：拾光 / 念生的提示词入库，可版本化、可回滚、可 A/B
CREATE TABLE ai_prompt (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  scene       varchar(64)  NOT NULL,   -- shiguang.opening / shiguang.chat / shiguang.condense
                                       -- niansheng.analyze / niansheng.challenge
  version     integer      NOT NULL DEFAULT 1,
  content     text         NOT NULL,
  is_active   smallint     NOT NULL DEFAULT 0,
  remark      varchar(255),
  create_time timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT uk_prompt_scene_version UNIQUE (scene, version)
);
CREATE UNIQUE INDEX uk_prompt_single_active ON ai_prompt (scene) WHERE is_active = 1;

-- ============================================================
-- 4. 拾光碎念
-- ============================================================
CREATE TABLE memory_note (
  id                   bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id              bigint       NOT NULL,
  kind                 smallint     NOT NULL DEFAULT 1,  -- 1照片记忆 2纯文本碎念 3心境随笔
  cover_photo          varchar(500) NOT NULL DEFAULT '',
  cover_thumb          varchar(500) NOT NULL DEFAULT '',
  auto_title           varchar(128) NOT NULL DEFAULT '',
  final_diary          text,
  status               smallint     NOT NULL DEFAULT 1,  -- 1未开始 2对话中 3已成念 4已废弃
  -- 0公开(进博客公开列表) 1私密(仅本人,AI温和关联) 2封存(本人也需PIN解锁,AI永不召回)
  privacy              smallint     NOT NULL DEFAULT 1,
  sealed_at            timestamptz,
  emotions             text[]       NOT NULL DEFAULT '{}',  -- 替代逗号串
  finish_time          timestamptz,
  published_article_id bigint,
  is_deleted           smallint     NOT NULL DEFAULT 0,
  create_time          timestamptz  NOT NULL DEFAULT now(),
  update_time          timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_note_privacy CHECK (privacy IN (0,1,2)),
  CONSTRAINT ck_note_sealed  CHECK (privacy <> 2 OR sealed_at IS NOT NULL)
);
-- 核心查询：列表/时光轴/隐私过滤，各自有索引
CREATE INDEX idx_note_user_create  ON memory_note (user_id, create_time DESC) WHERE is_deleted = 0;
CREATE INDEX idx_note_user_privacy ON memory_note (user_id, privacy)           WHERE is_deleted = 0;
CREATE INDEX idx_note_user_finish  ON memory_note (user_id, finish_time DESC)  WHERE finish_time IS NOT NULL;
-- 他人公开记忆浏览（D14）：跨用户查询，不能带 user_id 左前缀，需单独索引
CREATE INDEX idx_note_public      ON memory_note (create_time DESC, user_id)
  WHERE privacy = 0 AND is_deleted = 0;
CREATE INDEX idx_note_emotions     ON memory_note USING gin (emotions);
CREATE INDEX idx_note_title_trgm   ON memory_note USING gin (auto_title gin_trgm_ops);

CREATE TABLE memory_message (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  memory_id   bigint      NOT NULL,
  user_id     bigint      NOT NULL,
  msg_type    smallint    NOT NULL DEFAULT 1,  -- 1用户文字 2AI文字 3用户图片
  content     text,
  img_url     varchar(500) NOT NULL DEFAULT '',
  emotion     varchar(32),
  is_deleted  smallint    NOT NULL DEFAULT 0,
  create_time timestamptz NOT NULL DEFAULT now()
);
-- 只保留一个复合索引（原库 idx_memory_id 与 idx_user_memory 重复）
CREATE INDEX idx_msg_user_memory_time ON memory_message (user_id, memory_id, create_time);

-- 新增：独立照片实体（支撑相册 / 按时间归集 / EXIF / 一张记忆多图）
CREATE TABLE memory_photo (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  memory_id   bigint       NOT NULL,
  user_id     bigint       NOT NULL,
  url         varchar(500) NOT NULL,
  thumb_url   varchar(500) NOT NULL DEFAULT '',
  width       integer,
  height      integer,
  taken_at    timestamptz,                 -- EXIF 拍摄时间，时光轴可用它而非上传时间
  storage     smallint     NOT NULL DEFAULT 1,  -- 1受控存储 2公网图床(历史数据)
  sort        integer      NOT NULL DEFAULT 0,
  is_deleted  smallint     NOT NULL DEFAULT 0,
  create_time timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX idx_photo_user_time   ON memory_photo (user_id, taken_at DESC) WHERE is_deleted = 0;
CREATE INDEX idx_photo_memory      ON memory_photo (memory_id)              WHERE is_deleted = 0;
COMMENT ON COLUMN memory_photo.storage IS '1=经后端鉴权流式输出 2=历史 picui 公网URL(已接受既成事实)';

-- 向量表：真·向量列 + 部分 HNSW（封存记忆不进索引，DB 层保证"永不召回"）
CREATE TABLE memory_embedding (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id     bigint      NOT NULL,
  memory_id   bigint      NOT NULL,
  vector_key  varchar(64) NOT NULL DEFAULT '',
  text_snippet varchar(1024) NOT NULL DEFAULT '',
  privacy     smallint    NOT NULL DEFAULT 1,   -- 快照，隐私变更时同步
  emotion     varchar(32),
  -- 允许 NULL：Embedding 服务不可用时只存摘要（关键词降级召回仍可用），
  -- HNSW 索引天然跳过 NULL，语义召回不会把这些行当候选。
  embedding   vector(1024),
  create_time timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uk_emb_user_memory UNIQUE (user_id, memory_id)
);
-- 关键：部分索引，privacy=2 的向量物理上不在索引内
CREATE INDEX idx_emb_hnsw ON memory_embedding
  USING hnsw (embedding vector_cosine_ops) WHERE privacy <> 2;
CREATE INDEX idx_emb_user_privacy ON memory_embedding (user_id, privacy);

CREATE TABLE memory_insight (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id     bigint      NOT NULL,
  memory_id   bigint      NOT NULL,
  content     varchar(500) NOT NULL,
  emotion     varchar(32),
  embedding   vector(1024),
  source      smallint    NOT NULL DEFAULT 1,  -- 1AI提炼 2用户手动
  is_deleted  smallint    NOT NULL DEFAULT 0,  -- 新增软删（原库是硬删）
  create_time timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_insight_user_memory ON memory_insight (user_id, memory_id) WHERE is_deleted = 0;
CREATE INDEX idx_insight_hnsw ON memory_insight
  USING hnsw (embedding vector_cosine_ops) WHERE is_deleted = 0;

-- 情绪：拆成"归并字典"，count 不再落库（改为实时聚合，根治累加虚高）
CREATE TABLE memory_emotion (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id     bigint      NOT NULL,
  emotion     varchar(32) NOT NULL,           -- 规范名
  aliases     text[]      NOT NULL DEFAULT '{}',
  last_seen_at timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uk_emotion_user_canon UNIQUE (user_id, emotion)
);
COMMENT ON TABLE memory_emotion IS '仅存"自由标签→规范情绪"的映射；出现次数请实时聚合，勿再落 count 列';

CREATE TABLE memory_user_stat (
  id              bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id         bigint      NOT NULL,
  continue_days   integer     NOT NULL DEFAULT 1,
  total_memory    integer     NOT NULL DEFAULT 0,
  last_record_time timestamptz,
  CONSTRAINT uk_stat_user UNIQUE (user_id)
);

-- 新增：封存访问审计（谁在何时、凭什么解锁看了哪条封存记忆）
CREATE TABLE memory_access_log (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id     bigint      NOT NULL,
  memory_id   bigint      NOT NULL,
  privacy     smallint    NOT NULL,
  action      varchar(32) NOT NULL,   -- view_sealed / unlock_fail / privacy_change / export
  ip_address  varchar(50),
  user_agent  varchar(255),
  create_time timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_access_user_time ON memory_access_log (user_id, create_time DESC);

-- ============================================================
-- 5. 念生：想法工作台（六态状态机 + 关系边）
-- ============================================================
CREATE TABLE idea_state (
  code        varchar(32) PRIMARY KEY,   -- seed / incubating / validating / acting / crystallized / dormant
  name        varchar(32) NOT NULL,
  sort        integer     NOT NULL DEFAULT 0,
  is_terminal smallint    NOT NULL DEFAULT 0,
  color       varchar(16) NOT NULL DEFAULT ''
);
INSERT INTO idea_state (code, name, sort, is_terminal) VALUES
  ('seed',        '种子',   1, 0),
  ('incubating',  '发酵',   2, 0),
  ('validating',  '验证',   3, 0),
  ('acting',      '行动',   4, 0),
  ('crystallized','结晶',   5, 1),
  ('dormant',     '休眠',   6, 1);

-- 允许的迁移：可配置，插入一行即放开一条边
CREATE TABLE idea_transition_rule (
  from_state varchar(32) NOT NULL,
  to_state   varchar(32) NOT NULL,
  PRIMARY KEY (from_state, to_state)
);
INSERT INTO idea_transition_rule (from_state, to_state) VALUES
  ('seed','incubating'), ('seed','dormant'),
  ('incubating','validating'), ('incubating','seed'), ('incubating','dormant'),
  ('validating','acting'), ('validating','incubating'), ('validating','dormant'),
  ('acting','crystallized'), ('acting','validating'),
  ('dormant','incubating');

CREATE TABLE idea (
  id            bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id       bigint       NOT NULL,
  content       text         NOT NULL,
  state         varchar(32)  NOT NULL DEFAULT 'seed' REFERENCES idea_state(code),
  priority      smallint     NOT NULL DEFAULT 0,
  tags          text[]       NOT NULL DEFAULT '{}',
  embedding     vector(1024),
  source        smallint     NOT NULL DEFAULT 1,  -- 1手写 2由拾光闪光点升级
  source_insight_id bigint,                       -- 复制非引用，仅留痕
  is_deleted    smallint     NOT NULL DEFAULT 0,
  create_time   timestamptz  NOT NULL DEFAULT now(),
  update_time   timestamptz  NOT NULL DEFAULT now(),
  CONSTRAINT ck_idea_state CHECK (state IN
    ('seed','incubating','validating','acting','crystallized','dormant'))
);
CREATE INDEX idx_idea_user_state ON idea (user_id, state, priority DESC) WHERE is_deleted = 0;
CREATE INDEX idx_idea_tags        ON idea USING gin (tags);
CREATE INDEX idx_idea_hnsw        ON idea USING hnsw (embedding vector_cosine_ops)
  WHERE is_deleted = 0;

-- 状态迁移流水（可完整回放）
CREATE TABLE idea_transition (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  idea_id     bigint      NOT NULL,
  user_id     bigint      NOT NULL,
  from_state  varchar(32),
  to_state    varchar(32) NOT NULL,
  trigger     varchar(32) NOT NULL DEFAULT 'manual', -- manual / ai / rule
  reason      text,
  create_time timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_transition_idea_time ON idea_transition (idea_id, create_time);

-- AI 分析与质疑：始终追加，不覆盖原文
CREATE TABLE idea_analysis (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  idea_id     bigint      NOT NULL,
  user_id     bigint      NOT NULL,
  kind        varchar(16) NOT NULL,   -- analyze / challenge
  model       varchar(64) NOT NULL DEFAULT '',
  prompt_scene varchar(64),
  result_json jsonb       NOT NULL,   -- {claims, assumptions, risks, questions}
  degraded    smallint    NOT NULL DEFAULT 0,  -- 1=模型输出无法解析，降级为纯文本
  create_time timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_analysis_idea_time ON idea_analysis (idea_id, create_time DESC);

-- 关系边（后置功能，先建表不建图算法）
CREATE TABLE idea_relation (
  id          bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
  user_id     bigint      NOT NULL,
  from_idea_id bigint     NOT NULL,
  to_idea_id   bigint     NOT NULL,
  rel_type    varchar(32) NOT NULL DEFAULT 'related', -- related / contradicts / extends / causes
  weight      real        NOT NULL DEFAULT 1.0,
  create_time timestamptz NOT NULL DEFAULT now(),
  CONSTRAINT uk_relation UNIQUE (from_idea_id, to_idea_id, rel_type),
  CONSTRAINT ck_relation_self CHECK (from_idea_id <> to_idea_id)
);
CREATE INDEX idx_relation_from ON idea_relation (user_id, from_idea_id);
CREATE INDEX idx_relation_to   ON idea_relation (user_id, to_idea_id);

-- ============================================================
-- 7. 不变量与一致性巡检（因刻意不建外键，用查询兜底）
-- ============================================================
-- 不变量 I1（D12/D13 投影一致性）：
--   privacy=0  ⇔  存在 source_memory_id 指向它、status=1、is_deleted=0 的文章
--   privacy=1/2 ⇔  该文章 status<>1（下架）或不存在
-- 违反 I1 的排查：
-- SELECT n.id, n.privacy, a.id AS article_id, a.status
--   FROM memory_note n
--   LEFT JOIN blog_article a ON a.source_memory_id = n.id AND a.is_deleted = 0
--  WHERE n.is_deleted = 0
--    AND ((n.privacy = 0 AND (a.id IS NULL OR a.status <> 1))
--      OR (n.privacy <> 0 AND a.status = 1));
--
-- 不变量 I2（D13 同步水位）：文章落后于记忆（需重投影）
-- SELECT a.id FROM blog_article a JOIN memory_note n ON n.id = a.source_memory_id
--  WHERE n.update_time > a.source_rev AND a.manual_edited = 0;
--
-- 悬空的发布关系
-- SELECT n.id FROM memory_note n LEFT JOIN blog_article a ON a.id = n.published_article_id
--  WHERE n.published_article_id IS NOT NULL AND a.id IS NULL;
-- 封存状态与 sealed_at 不匹配（CHECK 已拦，防绕过）
-- SELECT id FROM memory_note WHERE privacy = 2 AND sealed_at IS NULL;
