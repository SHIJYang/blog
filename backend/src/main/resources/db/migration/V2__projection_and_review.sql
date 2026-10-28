-- ============================================================
-- V2 · 记忆投影（D12/D13）所需的「待人工合并」标记
-- ------------------------------------------------------------
-- V1 已包含 source_memory_id / source_rev / manual_edited；
-- 这里只补 needs_review：记忆改动后文章被人工润色过时置 1，
-- 投影服务不覆盖正文，只打标记等人工合并。
-- ============================================================

ALTER TABLE blog_article
  ADD COLUMN IF NOT EXISTS needs_review smallint NOT NULL DEFAULT 0;

COMMENT ON COLUMN blog_article.needs_review IS
  '1=记忆又改了但文章被人工编辑过，待人工合并（投影服务不覆盖正文）';

CREATE INDEX IF NOT EXISTS idx_article_needs_review
  ON blog_article (needs_review)
  WHERE needs_review = 1;
