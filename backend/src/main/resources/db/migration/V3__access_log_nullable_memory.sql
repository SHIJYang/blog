-- ============================================================
-- V3 · 访问审计允许"不针对某条记忆"的事件
-- ------------------------------------------------------------
-- PIN 校验成功/失败（unlock_success / unlock_fail）发生在记忆之外，
-- V1 的 memory_access_log.memory_id NOT NULL 会把这类审计写失败。
-- 封存访问（view_sealed）与隐私变更（privacy_change）仍应带 memory_id。
-- ============================================================

ALTER TABLE memory_access_log
  ALTER COLUMN memory_id DROP NOT NULL;
