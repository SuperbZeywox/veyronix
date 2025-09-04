package com.zeywox.veyronixcore.config.cache;


import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "response-cache")
public record ResponseCacheProperties(long hardTtlSeconds, int cacheablePagesMax, int l1SoftTtlMillis, int revalidateBudgetMillis) {}


