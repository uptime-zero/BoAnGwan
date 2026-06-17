package com.boangwan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "rss")
public record RssProperties(String userAgent, int timeoutSeconds) {}
