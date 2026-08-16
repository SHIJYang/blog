-- ============================================================
-- AI 对话供应商表（AI 对话供应商管理）
-- 每家供应商一行，独立存储 base_url / api_key / model。
-- is_active 标记当前激活供应商（仅一行 = 1）。
-- 向量固定走 SiliconFlow，与本表无关。
-- ============================================================
CREATE TABLE IF NOT EXISTS `ai_provider` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `name` varchar(64) NOT NULL COMMENT '供应商名称(展示名)',
  `base_url` varchar(255) NOT NULL COMMENT 'OpenAI兼容接口地址',
  `api_key` varchar(255) NOT NULL DEFAULT '' COMMENT 'API Key',
  `model` varchar(128) NOT NULL DEFAULT '' COMMENT '对话模型名',
  `is_active` tinyint NOT NULL DEFAULT 0 COMMENT '是否当前激活(0/1)',
  `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '是否启用(0/1)',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI对话供应商表';
