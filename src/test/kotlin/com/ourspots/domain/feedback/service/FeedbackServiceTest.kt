package com.ourspots.domain.feedback.service

import com.ourspots.api.dto.FeedbackCreateRequest
import com.ourspots.common.exception.TooManyRequestsException
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.domain.feedback.entity.Feedback
import com.ourspots.domain.feedback.repository.FeedbackRepository
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FeedbackServiceTest {

    private lateinit var feedbackRepository: FeedbackRepository
    private lateinit var telegramNotificationService: TelegramNotificationService
    private lateinit var feedbackService: FeedbackService

    private val testIp = "192.168.1.1"
    private val otherIp = "192.168.1.2"

    @BeforeEach
    fun setUp() {
        feedbackRepository = mockk()
        telegramNotificationService = mockk()
        feedbackService = FeedbackService(feedbackRepository, telegramNotificationService)
        every { feedbackRepository.save(any<Feedback>()) } answers {
            firstArg<Feedback>().also { it.prePersist() }
        }
        every { telegramNotificationService.notifyNewFeedback(any()) } just Runs
    }

    @Nested
    @DisplayName("createFeedback")
    inner class CreateFeedback {

        @Test
        fun createFeedback_shouldSaveFeedback() {
            val request = FeedbackCreateRequest(content = "좋은 서비스입니다")

            feedbackService.createFeedback(request, testIp)

            verify { feedbackRepository.save(any<Feedback>()) }
        }

        @Test
        fun createFeedback_shouldTrimContent() {
            val request = FeedbackCreateRequest(content = "  피드백  ")

            feedbackService.createFeedback(request, testIp)

            verify { feedbackRepository.save(match { it.content == "피드백" }) }
        }

        @Test
        fun createFeedback_shouldNotifyTelegramWithTrimmedContent() {
            val request = FeedbackCreateRequest(content = "  피드백  ")

            feedbackService.createFeedback(request, testIp)

            verify { telegramNotificationService.notifyNewFeedback("피드백") }
        }

        @Test
        fun createFeedback_whenExceedsRateLimit_shouldThrowTooManyRequests() {
            val request = FeedbackCreateRequest(content = "피드백")

            repeat(3) { feedbackService.createFeedback(request, testIp) }

            assertThrows<TooManyRequestsException> {
                feedbackService.createFeedback(request, testIp)
            }
        }

        @Test
        fun createFeedback_whenDifferentIp_shouldNotBlock() {
            val request = FeedbackCreateRequest(content = "피드백")

            repeat(3) { feedbackService.createFeedback(request, testIp) }

            // 다른 IP는 영향 없음
            feedbackService.createFeedback(request, otherIp)

            verify(exactly = 4) { feedbackRepository.save(any<Feedback>()) }
        }

        @Test
        fun createFeedback_whenBlockThresholdReached_shouldBlockIp() {
            val request = FeedbackCreateRequest(content = "피드백")

            repeat(3) { feedbackService.createFeedback(request, testIp) }

            // 4번째 시도 → TooManyRequestsException + IP 차단 등록
            assertThrows<TooManyRequestsException> {
                feedbackService.createFeedback(request, testIp)
            }

            // 이후 즉시 다시 시도해도 차단됨
            assertThrows<TooManyRequestsException> {
                feedbackService.createFeedback(request, testIp)
            }
        }
    }
}
