package com.clinicone.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableCaching
public class CacheConfig {
    private static final long MAX_ENTRIES = 512;
    private static final Duration EXPIRATION = Duration.ofMinutes(10);

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager(
                "addresses", "specialties", "diagnoses", "medications", "reasons");
        manager.setCaffeine(Caffeine.newBuilder()
                .maximumSize(MAX_ENTRIES)
                .expireAfterWrite(EXPIRATION)
                .recordStats());
        return manager;
    }
}
