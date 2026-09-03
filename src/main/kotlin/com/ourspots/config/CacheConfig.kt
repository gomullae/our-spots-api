package com.ourspots.config

import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.cache.CacheManager
import org.springframework.cache.caffeine.CaffeineCacheManager
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.TimeUnit

@Configuration
class CacheConfig {

    @Bean
    fun cacheManager(): CacheManager {
        val caffeineCacheManager = CaffeineCacheManager("markers")
        caffeineCacheManager.setCaffeine(
            Caffeine.newBuilder()
                .expireAfterWrite(12, TimeUnit.HOURS)
                .maximumSize(100)
                // recordStats() 없으면 CaffeineCacheMetrics가 cache.size만 찍고 히트율/미스율은 수집을
                // 아예 못 함(2026-09-02 발견 — 그동안 캐시 통계가 사실상 비어있었음)
                .recordStats()
        )
        caffeineCacheManager.registerCustomCache(
            "weightRecords",
            Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.DAYS)
                .maximumSize(10)
                .recordStats()
                .build()
        )
        return caffeineCacheManager
    }
}
