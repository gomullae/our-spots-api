package com.ourspots.domain.auth.service

import com.ourspots.common.exception.TooManyRequestsException
import com.ourspots.common.exception.UnauthorizedException
import com.ourspots.domain.auth.entity.LoginAttempt
import com.ourspots.domain.auth.repository.LoginAttemptRepository
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AuthServiceTest {

    private lateinit var loginAttemptRepository: LoginAttemptRepository
    private lateinit var jwtProvider: JwtProvider
    private lateinit var authService: AuthService

    private val adminPassword = "test-admin-password"
    private val testIp = "192.168.1.1"
    private val testUserAgent = "Mozilla/5.0 Test Browser"

    @BeforeEach
    fun setUp() {
        loginAttemptRepository = mockk()
        jwtProvider = mockk()

        // 기본 save 동작 설정 - 입력받은 객체를 그대로 반환
        every { loginAttemptRepository.save(any<LoginAttempt>()) } answers { firstArg() }

        authService = AuthService(
            loginAttemptRepository = loginAttemptRepository,
            jwtProvider = jwtProvider,
            adminPassword = adminPassword
        )
    }

    @Nested
    @DisplayName("login")
    inner class Login {

        @Test
        fun login_whenCorrectPassword_shouldReturnToken() {
            // given
            val expectedToken = "jwt-token-123"
            every { jwtProvider.generateToken() } returns expectedToken

            // when
            val result = authService.login(adminPassword, testIp, testUserAgent)

            // then
            assertEquals(expectedToken, result)
            verify { jwtProvider.generateToken() }
        }

        @Test
        fun login_whenWrongPassword_shouldThrowUnauthorizedException() {
            // given
            val wrongPassword = "wrong-password"

            // when & then
            val exception = assertThrows<UnauthorizedException> {
                authService.login(wrongPassword, testIp, testUserAgent)
            }

            assertNotNull(exception)
            verify { loginAttemptRepository.save(any<LoginAttempt>()) }
        }

        @Test
        fun login_whenSuccessAfterFailures_shouldResetFailCount() {
            // given
            val expectedToken = "jwt-token"
            every { jwtProvider.generateToken() } returns expectedToken

            repeat(3) {
                runCatching { authService.login("wrong", testIp, testUserAgent) }
            }

            // when
            val result = authService.login(adminPassword, testIp, testUserAgent)

            // then
            assertNotNull(result)
        }
    }

    @Nested
    @DisplayName("Rate Limiting")
    inner class RateLimiting {

        @Test
        fun login_whenMaxAttemptsExceeded_shouldThrowTooManyRequestsException() {
            // given & when
            repeat(AuthService.MAX_ATTEMPTS) {
                runCatching { authService.login("wrong", testIp, testUserAgent) }
            }

            // then
            val exception = assertThrows<TooManyRequestsException> {
                authService.login("wrong", testIp, testUserAgent)
            }
            assertTrue(exception.message!!.contains("차단"))
        }

        @Test
        fun login_whenBlockedIpWithCorrectPassword_shouldStillBeBlocked() {
            // given
            repeat(AuthService.MAX_ATTEMPTS) {
                runCatching { authService.login("wrong", testIp, testUserAgent) }
            }

            // when & then
            assertThrows<TooManyRequestsException> {
                authService.login(adminPassword, testIp, testUserAgent)
            }
        }

        @Test
        fun login_whenDifferentIp_shouldNotBeAffected() {
            // given
            val otherIp = "192.168.1.2"
            val expectedToken = "jwt-token"
            every { jwtProvider.generateToken() } returns expectedToken

            repeat(AuthService.MAX_ATTEMPTS) {
                runCatching { authService.login("wrong", testIp, testUserAgent) }
            }

            // when
            val result = authService.login(adminPassword, otherIp, testUserAgent)

            // then
            assertNotNull(result)
        }

        @Test
        fun login_whenFailed_shouldSaveAttemptToDatabase() {
            // given
            val savedAttempts = mutableListOf<LoginAttempt>()
            every { loginAttemptRepository.save(any<LoginAttempt>()) } answers {
                val attempt = firstArg<LoginAttempt>()
                savedAttempts.add(attempt)
                attempt
            }

            // when
            repeat(3) {
                runCatching { authService.login("wrong", testIp, testUserAgent) }
            }

            // then
            assertEquals(3, savedAttempts.size)
            assertEquals(1, savedAttempts[0].attemptCount)
            assertEquals(2, savedAttempts[1].attemptCount)
            assertEquals(3, savedAttempts[2].attemptCount)
        }

        @Test
        fun login_whenMaxAttemptsReached_shouldSaveBlockedFlagTrue() {
            // given
            val savedAttempts = mutableListOf<LoginAttempt>()
            every { loginAttemptRepository.save(any<LoginAttempt>()) } answers {
                val attempt = firstArg<LoginAttempt>()
                savedAttempts.add(attempt)
                attempt
            }

            // when
            repeat(AuthService.MAX_ATTEMPTS) {
                runCatching { authService.login("wrong", testIp, testUserAgent) }
            }

            // then
            val lastAttempt = savedAttempts.last()
            assertEquals(AuthService.MAX_ATTEMPTS, lastAttempt.attemptCount)
            assertTrue(lastAttempt.blocked)
        }
    }

    @Nested
    @DisplayName("validateToken")
    inner class ValidateToken {

        @Test
        fun validateToken_whenValidToken_shouldReturnTrue() {
            // given
            val token = "valid-token"
            every { jwtProvider.validateToken(token) } returns true

            // when
            val result = authService.validateToken(token)

            // then
            assertTrue(result)
        }

        @Test
        fun validateToken_whenInvalidToken_shouldReturnFalse() {
            // given
            val token = "invalid-token"
            every { jwtProvider.validateToken(token) } returns false

            // when
            val result = authService.validateToken(token)

            // then
            assertEquals(false, result)
        }
    }

}
