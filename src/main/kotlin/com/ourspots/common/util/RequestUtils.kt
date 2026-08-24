package com.ourspots.common.util

import jakarta.servlet.http.HttpServletRequest

object RequestUtils {

    // nginx가 항상 X-Real-IP를 $remote_addr로 설정하고 8080 포트는 외부에 노출되지 않으므로(docs/deployment.md),
    // 스푸핑 가능한 X-Forwarded-For는 신뢰하지 않음 — X-Real-IP 부재 시 바로 remoteAddr로 폴백
    fun getClientIp(request: HttpServletRequest): String {
        val xRealIp = request.getHeader("X-Real-IP")
        if (!xRealIp.isNullOrBlank()) return xRealIp.trim()

        return request.remoteAddr ?: "unknown"
    }
}
