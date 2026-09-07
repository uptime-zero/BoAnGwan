package com.boangwan.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "selection")
public record SelectionProperties(
        int candidateWindowHours,
        int maxFailedAttempts,
        int maxCandidatesForTriage,
        int minContentLength
) {}
