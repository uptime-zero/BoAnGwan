package com.boangwan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "schedule")
public record ScheduleProperties(
        ScheduleItem collect,
        ScheduleItem digest,
        String timezone
) {
    public record ScheduleItem(String cron) {}
}
