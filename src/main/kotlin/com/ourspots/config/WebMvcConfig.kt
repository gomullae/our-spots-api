package com.ourspots.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@ConditionalOnWebApplication
class WebMvcConfig(
    private val jwtInterceptor: JwtInterceptor,
    private val adminOnlyInterceptor: AdminOnlyInterceptor
) : WebMvcConfigurer {

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(jwtInterceptor)
            .addPathPatterns("/api/places/**", "/api/map/markers/refresh")
            .excludePathPatterns("/api/auth/**")

        registry.addInterceptor(adminOnlyInterceptor)
            .addPathPatterns("/api/weights/**", "/api/places/recent", "/api/expenses/**", "/api/schedules/**", "/api/admin/**")
    }
}
