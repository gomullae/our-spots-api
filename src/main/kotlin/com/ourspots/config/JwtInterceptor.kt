package com.ourspots.config

import com.ourspots.common.exception.UnauthorizedException
import com.ourspots.domain.auth.service.JwtProvider
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
@ConditionalOnWebApplication
class JwtInterceptor(
    private val jwtProvider: JwtProvider
) : HandlerInterceptor {

    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        val method = request.method
        if (method == "OPTIONS" || method == "GET") {
            return true
        }

        val authHeader = request.getHeader("Authorization")
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw UnauthorizedException("인증이 필요합니다. 로그인해주세요.")
        }

        val token = authHeader.substring(7)
        if (!jwtProvider.validateToken(token)) {
            throw UnauthorizedException("인증이 만료되었습니다. 다시 로그인해주세요.", isTokenExpired = jwtProvider.isExpiredToken(token))
        }

        return true
    }
}
