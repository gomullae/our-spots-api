package com.ourspots.config

import com.ourspots.common.exception.UnauthorizedException
import com.ourspots.domain.auth.service.JwtProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

/** JwtInterceptor와 달리 GET도 예외 없이 인증을 요구한다 — 관리자 전용 데이터(체중 기록 등)에 사용 */
@Component
@ConditionalOnWebApplication
class AdminOnlyInterceptor(
    private val jwtProvider: JwtProvider
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        if (request.method == "OPTIONS") return true

        if (!jwtProvider.isValidAuthHeader(request.getHeader("Authorization"))) {
            throw UnauthorizedException("인증이 필요합니다. 로그인해주세요.")
        }
        return true
    }
}
