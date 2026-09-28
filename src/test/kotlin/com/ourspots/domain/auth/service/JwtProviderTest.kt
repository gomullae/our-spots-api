package com.ourspots.domain.auth.service

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class JwtProviderTest {

    private lateinit var jwtProvider: JwtProvider

    private val testSecret = "this-is-a-test-secret-key-for-jwt-signing-minimum-32-chars"
    private val expirationHours = 24L

    @BeforeEach
    fun setUp() {
        jwtProvider = JwtProvider(
            secret = testSecret,
            expirationHours = expirationHours
        )
    }

    @Nested
    @DisplayName("generateToken")
    inner class GenerateToken {

        @Test
        fun generateToken_whenCalled_shouldReturnNonEmptyToken() {
            // when
            val token = jwtProvider.generateToken()

            // then
            assertNotNull(token)
            assertTrue(token.isNotBlank())
        }

        @Test
        fun generateToken_whenCalled_shouldReturnValidJwtFormat() {
            // when
            val token = jwtProvider.generateToken()

            // then
            val parts = token.split(".")
            assertTrue(parts.size == 3, "JWT는 3개의 부분으로 구성되어야 함")
        }

        @Test
        fun generateToken_whenCalledWithDelay_shouldReturnDifferentTokens() {
            // when
            val token1 = jwtProvider.generateToken()
            Thread.sleep(1100) // 1초 이상 대기하여 iat 값이 다르게 생성되도록
            val token2 = jwtProvider.generateToken()

            // then - iat(발급시간)이 다르므로 토큰도 다름
            assertNotEquals(token1, token2)
        }
    }

    @Nested
    @DisplayName("validateToken")
    inner class ValidateToken {

        @Test
        fun validateToken_whenValidToken_shouldReturnTrue() {
            // given
            val token = jwtProvider.generateToken()

            // when
            val result = jwtProvider.validateToken(token)

            // then
            assertTrue(result)
        }

        @Test
        fun validateToken_whenInvalidFormat_shouldReturnFalse() {
            // given
            val invalidToken = "invalid-token-format"

            // when
            val result = jwtProvider.validateToken(invalidToken)

            // then
            assertFalse(result)
        }

        @Test
        fun validateToken_whenEmptyToken_shouldReturnFalse() {
            // when
            val result = jwtProvider.validateToken("")

            // then
            assertFalse(result)
        }

        @Test
        fun validateToken_whenTamperedToken_shouldReturnFalse() {
            // given
            val validToken = jwtProvider.generateToken()
            val tamperedToken = validToken.dropLast(5) + "xxxxx"

            // when
            val result = jwtProvider.validateToken(tamperedToken)

            // then
            assertFalse(result)
        }

        @Test
        fun validateToken_whenSignedWithDifferentKey_shouldReturnFalse() {
            // given
            val otherProvider = JwtProvider(
                secret = "another-secret-key-for-testing-minimum-32-characters-long",
                expirationHours = 24
            )
            val tokenFromOther = otherProvider.generateToken()

            // when
            val result = jwtProvider.validateToken(tokenFromOther)

            // then
            assertFalse(result)
        }
    }

    @Nested
    @DisplayName("Token Expiration")
    inner class TokenExpiration {

        @Test
        fun validateToken_whenTokenExpired_shouldReturnFalse() {
            // given - 만료 시간 0시간으로 설정 (즉시 만료)
            val expiredProvider = JwtProvider(
                secret = testSecret,
                expirationHours = 0
            )
            val token = expiredProvider.generateToken()

            // 토큰 생성 직후에는 만료되지 않았을 수 있으므로 잠시 대기
            Thread.sleep(100)

            // when
            val result = expiredProvider.validateToken(token)

            // then
            assertFalse(result)
        }

        @Test
        fun validateToken_whenTokenNotExpired_shouldReturnTrue() {
            // given - 충분히 긴 만료 시간
            val longLivedProvider = JwtProvider(
                secret = testSecret,
                expirationHours = 24
            )
            val token = longLivedProvider.generateToken()

            // when
            val result = longLivedProvider.validateToken(token)

            // then
            assertTrue(result)
        }

        @Test
        fun isExpiredToken_whenTokenExpired_shouldReturnTrue() {
            // given - 만료 시간 0시간으로 설정 (즉시 만료)
            val expiredProvider = JwtProvider(
                secret = testSecret,
                expirationHours = 0
            )
            val token = expiredProvider.generateToken()
            Thread.sleep(100)

            // when
            val result = expiredProvider.isExpiredToken(token)

            // then
            assertTrue(result)
        }

        @Test
        fun isExpiredToken_whenTokenValid_shouldReturnFalse() {
            // given
            val token = jwtProvider.generateToken()

            // when
            val result = jwtProvider.isExpiredToken(token)

            // then
            assertFalse(result)
        }

        @Test
        fun isExpiredToken_whenTokenTampered_shouldReturnFalse() {
            // given - 만료가 아니라 위조/변조된 토큰은 별개로 취급해야 함
            val validToken = jwtProvider.generateToken()
            val tamperedToken = validToken.dropLast(5) + "xxxxx"

            // when
            val result = jwtProvider.isExpiredToken(tamperedToken)

            // then
            assertFalse(result)
        }

        @Test
        fun isExpiredToken_whenInvalidFormat_shouldReturnFalse() {
            // when
            val result = jwtProvider.isExpiredToken("not-a-jwt")

            // then
            assertFalse(result)
        }
    }
}
