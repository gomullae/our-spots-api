package com.ourspots.domain.auth.service

import com.ourspots.common.exception.TooManyRequestsException
import com.ourspots.common.exception.UnauthorizedException
import com.ourspots.domain.auth.entity.LoginAttempt
import com.ourspots.domain.auth.repository.LoginAttemptRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

data class AttemptInfo(
    val count: Int,
    val blockedUntil: LocalDateTime?
)

@Service
class AuthService(
    private val loginAttemptRepository: LoginAttemptRepository,
    private val jwtProvider: JwtProvider,
    @Value("\${app.admin-password:}") private val adminPassword: String
) {
    // 메모리에서 실패 횟수 관리 (IP -> AttemptInfo)
    private val attemptCache = ConcurrentHashMap<String, AttemptInfo>()

    companion object {
        const val MAX_ATTEMPTS = 5
        const val BLOCK_DURATION_HOURS = 24L
    }

    fun login(password: String, ipAddress: String, userAgent: String?): String {
        checkIfBlocked(ipAddress)

        if (password != adminPassword) {
            handleFailedAttempt(ipAddress, userAgent, "POST /api/auth/login")
            throw UnauthorizedException("권한이 없습니다. 관리자 비밀번호를 확인해주세요")
        }

        attemptCache.remove(ipAddress)
        return jwtProvider.generateToken()
    }

    fun validateToken(token: String): Boolean {
        return jwtProvider.validateToken(token)
    }

    private fun checkIfBlocked(ipAddress: String) {
        val info = attemptCache[ipAddress] ?: return

        info.blockedUntil?.let { blockedUntil ->
            if (LocalDateTime.now().isBefore(blockedUntil)) {
                throw TooManyRequestsException(
                    "너무 많은 시도로 인해 차단되었습니다. ${blockedUntil.toLocalDate()} ${blockedUntil.toLocalTime().withNano(0)} 이후에 다시 시도해주세요."
                )
            } else {
                attemptCache.remove(ipAddress)
            }
        }
    }

    private fun handleFailedAttempt(ipAddress: String, userAgent: String?, endpoint: String) {
        val currentInfo = attemptCache[ipAddress]
        val newCount = (currentInfo?.count ?: 0) + 1

        val blockedUntil = if (newCount >= MAX_ATTEMPTS) {
            LocalDateTime.now().plusHours(BLOCK_DURATION_HOURS)
        } else {
            null
        }

        attemptCache[ipAddress] = AttemptInfo(newCount, blockedUntil)

        // DB에 기록 (공격자 정보 저장)
        val attempt = LoginAttempt(
            ipAddress = ipAddress,
            userAgent = userAgent?.take(500),
            endpoint = endpoint,
            attemptCount = newCount,
            blocked = blockedUntil != null
        )
        loginAttemptRepository.save(attempt)
    }
}
