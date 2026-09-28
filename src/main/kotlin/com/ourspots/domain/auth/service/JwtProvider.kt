package com.ourspots.domain.auth.service

import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtProvider(
    @Value("\${app.jwt.secret:}") private val secret: String,
    @Value("\${app.jwt.expiration-hours:24}") private val expirationHours: Long
) {
    companion object {
        private const val MIN_SECRET_LENGTH = 32
    }

    init {
        require(secret.length >= MIN_SECRET_LENGTH) {
            "JWT secret must be at least $MIN_SECRET_LENGTH characters long"
        }
    }

    private val key: SecretKey by lazy {
        Keys.hmacShaKeyFor(secret.toByteArray())
    }

    fun generateToken(): String {
        val now = Date()
        val expiration = Date(now.time + expirationHours * 60 * 60 * 1000)

        return Jwts.builder()
            .subject("admin")
            .issuedAt(now)
            .expiration(expiration)
            .signWith(key)
            .compact()
    }

    fun validateToken(token: String): Boolean {
        return try {
            Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
            true
        } catch (e: JwtException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }

    fun isValidAuthHeader(authHeader: String?): Boolean {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) return false
        return validateToken(authHeader.substring(7))
    }

    // 서명은 정상인데 유효기간만 지난 경우만 true — 헤더 누락/위조된 토큰은 여기 해당 안 됨.
    // 정상적으로 로그인했던 세션이 그냥 만료된(21일 지남) 경우를 구분해서, 비정상 접근 알림을 거기까지
    // 울리지 않게 하려는 용도(GlobalExceptionHandler 참고)
    fun isExpiredToken(token: String): Boolean {
        return try {
            Jwts.parser().verifyWith(key).build().parseSignedClaims(token)
            false
        } catch (e: ExpiredJwtException) {
            true
        } catch (e: JwtException) {
            false
        } catch (e: IllegalArgumentException) {
            false
        }
    }
}
