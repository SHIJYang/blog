-- ============================================================
-- SHIJYang/blog 数据库基线（MySQL 8.0+）
-- 生成时间：2026-10-27
-- 来源：用户提供的生产库 SHOW CREATE TABLE 原文
--
-- 本文件的定位：第 0 阶段 Flyway 的 V1__baseline.sql
--
-- 与原始 DDL 的差异（仅两处，结构零改动）：
--   1. memory_* 5 张表的 COMMENT 原文因编码问题呈 GBK 乱码（如「涓婚敭ID」），
--      此处按字段语义还原为正确中文；字段名/类型/默认值/索引完全保留。
--   2. 去掉了 AUTO_INCREMENT 起始值，避免新环境数据编号跳号。
--
-- 未包含但强烈建议（见报告 §4.5，不要直接照抄执行，需先评估生产影响）：
--   ALTER TABLE memory_note       ADD KEY idx_user_privacy (user_id, privacy);
--   ALTER TABLE memory_note       ADD KEY idx_user_finish  (user_id, finish_time);
--   ALTER TABLE memory_note       DROP KEY idx_user_id;              -- 与 idx_user_create 前缀重复
--   ALTER TABLE memory_embedding  ADD KEY idx_user_privacy (user_id, privacy);
--   ALTER TABLE blog_article      ADD KEY idx_status_create (status, create_time);
-- ============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ------------------------------------------------------------
-- 1. 认证
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `username` varchar(50) NOT NULL COMMENT '用户名/登录账号',
  `password` varchar(100) NOT NULL COMMENT '密码(BCrypt加密保存)',
  `nickname` varchar(50) NOT NULL COMMENT '昵称',
  `email` varchar(100) NOT NULL COMMENT '邮箱',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `role` varchar(20) NOT NULL DEFAULT 'READER' COMMENT '角色: ADMIN(管理员), AUTHOR(作者), READER(读者)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-禁用, 1-启用',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_username` (`username`) USING BTREE,
  UNIQUE KEY `uk_email` (`email`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. 博客域
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `blog_article`;
CREATE TABLE `blog_article` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `author_id` bigint NOT NULL COMMENT '作者ID(关联sys_user)',
  `title` varchar(150) NOT NULL COMMENT '文章标题',
  `content` longtext NOT NULL COMMENT '文章内容(Markdown或HTML)',
  `summary` varchar(500) DEFAULT NULL COMMENT '文章摘要',
  `cover_image` varchar(255) DEFAULT NULL COMMENT '封面图URL',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-草稿, 1-公开已发布, 2-私密/仅自己可见',
  `views` int NOT NULL DEFAULT '0' COMMENT '浏览量',
  `allow_comment` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许评论: 0-否, 1-是',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_author_id` (`author_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章表';

DROP TABLE IF EXISTS `blog_category`;
CREATE TABLE `blog_category` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(50) NOT NULL COMMENT '分类名称',
  `description` varchar(255) DEFAULT NULL COMMENT '描述',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_name` (`name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='分类表';

DROP TABLE IF EXISTS `blog_tag`;
CREATE TABLE `blog_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(50) NOT NULL COMMENT '标签名称',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_name` (`name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='标签表';

DROP TABLE IF EXISTS `blog_article_category`;
CREATE TABLE `blog_article_category` (
  `article_id` bigint NOT NULL COMMENT '文章ID',
  `category_id` bigint NOT NULL COMMENT '分类ID',
  PRIMARY KEY (`article_id`,`category_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章-分类关联表';

DROP TABLE IF EXISTS `blog_article_tag`;
CREATE TABLE `blog_article_tag` (
  `article_id` bigint NOT NULL COMMENT '文章ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  PRIMARY KEY (`article_id`,`tag_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章-标签关联表';

DROP TABLE IF EXISTS `blog_comment`;
CREATE TABLE `blog_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `article_id` bigint NOT NULL COMMENT '文章ID',
  `user_id` bigint DEFAULT NULL COMMENT '用户ID(已登录用户关联sys_user, 游客为NULL)',
  `nickname` varchar(50) NOT NULL COMMENT '评论者昵称(游客填写或登录用户昵称)',
  `email` varchar(100) NOT NULL COMMENT '评论者邮箱(游客填写或登录用户邮箱)',
  `website` varchar(255) DEFAULT NULL COMMENT '评论者网站/博客',
  `content` text NOT NULL COMMENT '评论内容(通过Jsoup过滤HTML)',
  `parent_id` bigint DEFAULT NULL COMMENT '父评论ID(回复哪条评论, 若为一级评论则为NULL)',
  `root_id` bigint DEFAULT NULL COMMENT '根评论ID(当前盖楼最顶层的一级评论ID, 方便平铺查询)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-待审核, 1-已通过, 2-垃圾评论',
  `ip_address` varchar(50) DEFAULT NULL COMMENT '评论者IP地址',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_article_id` (`article_id`) USING BTREE,
  KEY `idx_root_id` (`root_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文章评论表';

DROP TABLE IF EXISTS `blog_link`;
CREATE TABLE `blog_link` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `name` varchar(100) NOT NULL COMMENT '链接名称',
  `url` varchar(255) NOT NULL COMMENT '链接地址',
  `logo` varchar(255) DEFAULT NULL COMMENT 'Logo图片URL',
  `description` varchar(255) DEFAULT NULL COMMENT '简介',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序值(越小越靠前)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 0-隐藏, 1-显示',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='友情链接表';

DROP TABLE IF EXISTS `blog_config`;
CREATE TABLE `blog_config` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `config_key` varchar(50) NOT NULL COMMENT '配置键(如: blog_name, icp_beian, about_me)',
  `config_value` text COMMENT '配置值(使用Jsoup进行HTML清洗)',
  `config_name` varchar(100) NOT NULL COMMENT '配置中文展示名称(如: 博客名称)',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除: 0-未删除, 1-已删除',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_public` tinyint NOT NULL DEFAULT '1' COMMENT '是否公开: 0-私密, 1-公开',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_config_key` (`config_key`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='系统配置表';

-- ------------------------------------------------------------
-- 3. AI 供应商
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `ai_provider`;
CREATE TABLE `ai_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '供应商名称(展示名)',
  `base_url` varchar(255) NOT NULL COMMENT 'OpenAI兼容接口地址',
  `api_key` varchar(255) NOT NULL DEFAULT '' COMMENT 'API Key',
  `model` varchar(128) NOT NULL DEFAULT '' COMMENT '对话模型名',
  `is_active` tinyint NOT NULL DEFAULT '0' COMMENT '是否当前激活(0/1)',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用(0/1)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_active` (`is_active`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI对话供应商表';

-- ------------------------------------------------------------
-- 4. 拾光碎念域
-- ------------------------------------------------------------
DROP TABLE IF EXISTS `memory_note`;
CREATE TABLE `memory_note` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '关联博客用户ID',
  `cover_photo` varchar(500) DEFAULT '' COMMENT '记忆封面照片URL',
  `cover_thumb` varchar(500) DEFAULT '' COMMENT '封面缩略图URL',
  `auto_title` varchar(128) DEFAULT '' COMMENT 'AI图像识别自动生成标题',
  `final_diary` longtext COMMENT '凝聚后生成的完整日记正文',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态: 1-未开始 2-对话中 3-已成念 4-已废弃',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `finish_time` datetime DEFAULT NULL COMMENT '记忆凝聚完成时间',
  `published_article_id` bigint DEFAULT NULL COMMENT '发布到博客的文章ID（未发布为NULL）',
  `privacy` tinyint NOT NULL DEFAULT '1' COMMENT '记忆隐私等级：0-公开(可联动博客可召回) 1-私密(仅本人可见,AI温和关联) 2-封存(永久禁止AI主动提及,仅存档不召回)',
  `emotions` varchar(255) DEFAULT NULL COMMENT '本记忆聚合的情绪标签(逗号分隔)',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0-正常 1-删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_id` (`user_id`) USING BTREE,
  KEY `idx_status` (`status`) USING BTREE,
  KEY `idx_user_create` (`user_id`,`create_time`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI记忆日记主表';

DROP TABLE IF EXISTS `memory_message`;
CREATE TABLE `memory_message` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `memory_id` bigint NOT NULL COMMENT '关联记忆主表ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `msg_type` tinyint NOT NULL DEFAULT '1' COMMENT '消息类型：1-用户文字 2-AI文字 3-用户图片',
  `content` longtext COMMENT '消息文本内容',
  `img_url` varchar(500) DEFAULT '' COMMENT '图片地址（图片消息专用）',
  `emotion` varchar(32) DEFAULT NULL COMMENT 'AI 提取的情绪标签（自由生成，可归并）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_memory_id` (`memory_id`) USING BTREE,
  KEY `idx_user_memory` (`user_id`,`memory_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记忆对话消息表';

DROP TABLE IF EXISTS `memory_embedding`;
CREATE TABLE `memory_embedding` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `memory_id` bigint NOT NULL COMMENT '关联记忆ID',
  `vector_key` varchar(64) DEFAULT '' COMMENT '向量KEY（语义检索时使用）',
  `text_snippet` varchar(1024) DEFAULT '' COMMENT '向量化的原文摘要（用于溯源）',
  `privacy` tinyint NOT NULL DEFAULT '1' COMMENT '创建时记忆的隐私快照(0/1/2)',
  `emotion` varchar(32) DEFAULT NULL COMMENT '该记忆主情绪标签',
  `vector_json` longtext COMMENT '1024维向量JSON（MySQL存储，本地余弦相似度计算）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_memory` (`user_id`,`memory_id`) USING BTREE,
  KEY `idx_vector_key` (`vector_key`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记忆向量映射表';

DROP TABLE IF EXISTS `memory_emotion`;
CREATE TABLE `memory_emotion` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `emotion` varchar(32) NOT NULL COMMENT '情绪标签(归并后的规范名)',
  `aliases` varchar(500) DEFAULT '' COMMENT '归并进来的原始标签集合(逗号分隔)',
  `count` bigint NOT NULL DEFAULT '0' COMMENT '出现次数',
  `last_seen_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近出现时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_emotion` (`user_id`,`emotion`) USING BTREE,
  KEY `idx_user_count` (`user_id`,`count`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记忆情绪标签归并表';

DROP TABLE IF EXISTS `memory_insight`;
CREATE TABLE `memory_insight` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `memory_id` bigint NOT NULL,
  `content` varchar(500) NOT NULL,
  `emotion` varchar(32) DEFAULT NULL,
  `vector_json` longtext,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `source` tinyint NOT NULL DEFAULT '1' COMMENT '来源: 1-AI自动提炼 2-用户手动添加',
  PRIMARY KEY (`id`) USING BTREE,
  KEY `idx_user_memory` (`user_id`,`memory_id`) USING BTREE,
  KEY `idx_user` (`user_id`) USING BTREE,
  KEY `idx_memory` (`memory_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='记忆一句话知识点表';

DROP TABLE IF EXISTS `memory_user_stat`;
CREATE TABLE `memory_user_stat` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `continue_days` int NOT NULL DEFAULT '1' COMMENT '连续记录天数',
  `total_memory` int NOT NULL DEFAULT '0' COMMENT '累计记忆条数',
  `last_record_time` datetime DEFAULT NULL COMMENT '最后记录时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE KEY `uk_user_id` (`user_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户记忆统计表';

SET FOREIGN_KEY_CHECKS = 1;
