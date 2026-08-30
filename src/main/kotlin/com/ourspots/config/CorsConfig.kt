package com.ourspots.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.CorsFilter

@Configuration
@ConditionalOnWebApplication
class CorsConfig(
    @Value("\${app.cors.allowed-origins:}") private val allowedOriginsRaw: String
) {

    @Bean
    fun corsFilter(): CorsFilter {
        val origins = allowedOriginsRaw
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        val config = CorsConfiguration().apply {
            allowedOrigins = origins
            // PATCH: 사진 공개/비공개 전환(PATCH /api/photos/{id})에 필요 — 빠뜨리면 브라우저 preflight가
            // "Invalid CORS request"로 막혀서 요청 자체가 서버(컨트롤러)까지 도달하지 못함
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            allowCredentials = true
            // 백업 다운로드가 파일명을 이 헤더에서 읽어옴 — 기본적으로 브라우저가 크로스오리진 응답에서 숨기는 헤더라 명시적으로 노출 필요
            exposedHeaders = listOf("Content-Disposition")
        }

        val source = UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/**", config)
        }

        return CorsFilter(source)
    }
}
