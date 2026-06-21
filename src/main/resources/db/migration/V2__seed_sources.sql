INSERT INTO source (name, rss_url, language, encoding, guid_type, content_source_type, priority, active, created_at, updated_at)
VALUES
    ('보안뉴스', 'https://www.boannews.com/media/news_rss.xml', 'ko', 'EUC-KR', 'LINK_IDX', 'RSS_ONLY', 5, TRUE, current_timestamp, current_timestamp),
    ('데일리시큐', 'https://www.dailysecu.com/rss/allArticle.xml', 'ko', 'UTF-8', 'GUID_TAG', 'RSS_ONLY', 5, TRUE, current_timestamp, current_timestamp),
    ('The Hacker News', 'https://feeds.feedburner.com/TheHackersNews', 'en', 'UTF-8', 'GUID_TAG', 'RSS_ONLY', 10, TRUE, current_timestamp, current_timestamp),
    ('Krebs on Security', 'https://krebsonsecurity.com/feed/', 'en', 'UTF-8', 'GUID_TAG', 'RSS_ONLY', 10, TRUE, current_timestamp, current_timestamp),
    ('Bleeping Computer', 'https://www.bleepingcomputer.com/feed/', 'en', 'UTF-8', 'GUID_TAG', 'RSS_ONLY', 10, TRUE, current_timestamp, current_timestamp);
