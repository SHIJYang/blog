-- ============================================================
-- 记忆闪光点表（拾光碎念）
-- 凝聚时 AI 把日记/对话拆解成 N 条「闪光点」，
-- 每条独立向量化存储，AI 会话可精确召回某一句。
-- source: 1-AI自动提炼 2-用户手动添加
-- ============================================================
CREATE TABLE IF NOT EXISTS `memory_insight` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `memory_id` bigint NOT NULL COMMENT '关联记忆ID',
  `content` varchar(500) NOT NULL COMMENT '闪光点/念头内容',
  `emotion` varchar(32) DEFAULT NULL COMMENT '该条闪光点情绪标签',
  `vector_json` longtext NULL COMMENT '该闪光点独立向量(JSON,1024维)',
  `source` tinyint NOT NULL DEFAULT 1 COMMENT '来源: 1-AI提炼 2-用户手动',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_memory` (`user_id`, `memory_id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_memory` (`memory_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='记忆闪光点表';
