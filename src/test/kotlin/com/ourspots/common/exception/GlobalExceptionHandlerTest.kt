package com.ourspots.common.exception

import com.ourspots.common.errorlog.ErrorLog
import com.ourspots.common.errorlog.ErrorLogRepository
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.domain.auth.entity.AccessDeniedLog
import com.ourspots.domain.auth.repository.AccessDeniedLogRepository
import io.mockk.MockKAnnotations
import io.mockk.any
import io.mockk.capture
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.mockk
import io.mockk.verify
import jakarta.servlet.http.HttpServletRequest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class GlobalExceptionHandlerTest {

    @MockK
    private lateinit var errorLogRepository: ErrorLogRepository

    @MockK
    private lateinit var accessDeniedLogRepository: AccessDeniedLogRepository

    @MockK(relaxed = true)
    private lateinit var telegramNotificationService: TelegramNotificationService

    @InjectMockKs
    private lateinit var globalExceptionHandler: GlobalExceptionHandler

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    private fun mockRequest(method: String = "GET", uri: String = "/api/places") =
        mockk<HttpServletRequest>().also {
            every { it.method } returns method
            every { it.requestURI } returns uri
            every { it.getHeader(any()) } returns null
            every { it.remoteAddr } returns "127.0.0.1"
        }

    @Test
    fun handleException_shouldPersistErrorLogWithRequestDetails() {
        val slot = mutableListOf<ErrorLog>()
        every { errorLogRepository.save(capture(slot)) } answers { firstArg() }
        val exception = IllegalStateException("boom")

        val response = globalExceptionHandler.handleException(exception, mockRequest(method = "POST", uri = "/api/places"))

        assertEquals(false, response.success)
        assertEquals(1, slot.size)
        assertEquals("IllegalStateException", slot[0].exceptionType)
        assertEquals("boom", slot[0].message)
        assertEquals("POST", slot[0].method)
        assertEquals("/api/places", slot[0].path)
    }

    @Test
    fun handleException_whenErrorLogSaveFails_shouldStillReturnErrorResponse() {
        every { errorLogRepository.save(any<ErrorLog>()) } throws RuntimeException("db down")

        val response = globalExceptionHandler.handleException(IllegalStateException("boom"), mockRequest())

        assertEquals(false, response.success)
        verify { errorLogRepository.save(any<ErrorLog>()) }
    }

    @Test
    fun handleUnauthorizedException_shouldPersistAccessDeniedLog() {
        val slot = mutableListOf<AccessDeniedLog>()
        every { accessDeniedLogRepository.save(capture(slot)) } answers { firstArg() }
        val exception = UnauthorizedException("인증이 필요합니다. 로그인해주세요.")

        val response = globalExceptionHandler.handleUnauthorizedException(
            exception,
            mockRequest(method = "GET", uri = "/api/weights")
        )

        assertEquals(false, response.success)
        assertEquals(1, slot.size)
        assertEquals("GET", slot[0].method)
        assertEquals("/api/weights", slot[0].path)
        assertEquals("인증이 필요합니다. 로그인해주세요.", slot[0].message)
    }

    @Test
    fun handleUnauthorizedException_whenLoginEndpoint_shouldNotPersist() {
        val response = globalExceptionHandler.handleUnauthorizedException(
            UnauthorizedException("권한이 없습니다. 관리자 비밀번호를 확인해주세요"),
            mockRequest(method = "POST", uri = "/api/auth/login")
        )

        assertEquals(false, response.success)
        verify(exactly = 0) { accessDeniedLogRepository.save(any<AccessDeniedLog>()) }
    }

    @Test
    fun handleUnauthorizedException_whenAccessDeniedLogSaveFails_shouldStillReturnErrorResponse() {
        every { accessDeniedLogRepository.save(any<AccessDeniedLog>()) } throws RuntimeException("db down")

        val response = globalExceptionHandler.handleUnauthorizedException(
            UnauthorizedException("인증이 필요합니다. 로그인해주세요."),
            mockRequest(uri = "/api/weights")
        )

        assertEquals(false, response.success)
    }

    @Test
    fun handleException_shouldNotifyTelegram() {
        every { errorLogRepository.save(any<ErrorLog>()) } answers { firstArg() }

        globalExceptionHandler.handleException(IllegalStateException("boom"), mockRequest(method = "POST", uri = "/api/places"))

        verify {
            telegramNotificationService.notifyServerError(
                exceptionType = "IllegalStateException",
                method = "POST",
                path = "/api/places",
                message = "boom"
            )
        }
    }

    @Test
    fun handleUnauthorizedException_shouldNotifyTelegram() {
        every { accessDeniedLogRepository.save(any<AccessDeniedLog>()) } answers { firstArg() }

        globalExceptionHandler.handleUnauthorizedException(
            UnauthorizedException("인증이 필요합니다. 로그인해주세요."),
            mockRequest(method = "GET", uri = "/api/weights")
        )

        verify {
            telegramNotificationService.notifyAccessDenied(
                method = "GET",
                path = "/api/weights",
                ipAddress = "127.0.0.1",
                message = "인증이 필요합니다. 로그인해주세요."
            )
        }
    }

    @Test
    fun handleUnauthorizedException_whenLoginEndpoint_shouldNotNotifyTelegram() {
        globalExceptionHandler.handleUnauthorizedException(
            UnauthorizedException("권한이 없습니다. 관리자 비밀번호를 확인해주세요"),
            mockRequest(method = "POST", uri = "/api/auth/login")
        )

        verify(exactly = 0) { telegramNotificationService.notifyAccessDenied(any(), any(), any(), any()) }
    }
}
