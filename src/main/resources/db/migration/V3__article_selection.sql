ALTER TABLE raw_article
    ADD COLUMN attempt_count   INT          NOT NULL DEFAULT 0,
    ADD COLUMN last_attempt_at DATETIME     NULL,
    ADD COLUMN skip_reason     VARCHAR(200) NULL;

CREATE INDEX idx_raw_article_status_published ON raw_article (status, published_at);
CREATE INDEX idx_raw_article_fetched ON raw_article (fetched_at);

UPDATE raw_article SET attempt_count = 1 WHERE status = 'FAILED';
