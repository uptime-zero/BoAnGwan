CREATE TABLE source (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    rss_url VARCHAR(500) NOT NULL,
    language VARCHAR(10) NOT NULL DEFAULT 'ko',
    encoding VARCHAR(20) NOT NULL DEFAULT 'UTF-8',
    guid_type VARCHAR(20) NOT NULL DEFAULT 'GUID_TAG',
    content_source_type VARCHAR(20) NOT NULL DEFAULT 'RSS_ONLY',
    priority INT NOT NULL DEFAULT 10,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE raw_article (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    source_id BIGINT NOT NULL,
    guid VARCHAR(500) NOT NULL,
    title VARCHAR(500) NOT NULL,
    link VARCHAR(1000) NOT NULL,
    description TEXT,
    published_at DATETIME,
    fetched_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(20) NOT NULL DEFAULT 'COLLECTED',
    CONSTRAINT fk_raw_article_source FOREIGN KEY (source_id) REFERENCES source(id),
    CONSTRAINT uq_raw_article_source_guid UNIQUE (source_id, guid)
);

CREATE TABLE daily_digest (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    raw_article_id BIGINT NOT NULL,
    digest_date DATE NOT NULL,
    domain VARCHAR(50),
    tags JSON,
    one_liner VARCHAR(200),
    problem TEXT,
    risk TEXT,
    impact_target VARCHAR(200),
    action JSON,
    model_used VARCHAR(100),
    input_tokens INT,
    output_tokens INT,
    generated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_daily_digest_article FOREIGN KEY (raw_article_id) REFERENCES raw_article(id)
);

CREATE TABLE delivery_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    digest_id BIGINT NOT NULL,
    channel VARCHAR(20) NOT NULL DEFAULT 'DISCORD',
    status VARCHAR(20) NOT NULL,
    error_message TEXT,
    retry_count INT NOT NULL DEFAULT 0,
    sent_at DATETIME,
    CONSTRAINT fk_delivery_log_digest FOREIGN KEY (digest_id) REFERENCES daily_digest(id)
);
