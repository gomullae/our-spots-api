package com.ourspots.common.util

import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.web.client.RestTemplate
import java.time.Duration

// TelegramNotificationService/GooglePlaceSyncService가 공유하는 외부 HTTP 호출용 RestTemplate 생성 로직
object RestTemplateFactory {
    fun create(connectTimeoutSeconds: Long = 5, readTimeoutSeconds: Long = 5): RestTemplate =
        RestTemplate().apply {
            requestFactory = SimpleClientHttpRequestFactory().apply {
                setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds))
                setReadTimeout(Duration.ofSeconds(readTimeoutSeconds))
            }
        }
}
