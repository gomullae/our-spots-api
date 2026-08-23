package com.ourspots.domain.feedback.service

import com.ourspots.api.dto.FeedbackCreateRequest
import com.ourspots.common.exception.TooManyRequestsException
import com.ourspots.domain.feedback.entity.Feedback
import com.ourspots.domain.feedback.repository.FeedbackRepository
import org.springframework.stereotype.Service
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.concurrent.ConcurrentHashMap

@Service
class FeedbackService(
    private val feedbackRepository: FeedbackRepository
) {
    // IP별 최근 전송 시각 목록 (분당 횟수 추적)
    private val recentSendsByIp = ConcurrentHashMap<String, MutableList<OffsetDateTime>>()
    // 24시간 차단 목록 (IP → 차단 만료 시각)
    private val blockedUntilByIp = ConcurrentHashMap<String, OffsetDateTime>()

    companion object {
        private const val RATE_LIMIT_PER_MINUTE = 3
        private const val BLOCK_THRESHOLD = 4 // 4번째 시도 시 차단
        private const val BLOCK_HOURS = 24L
    }

    fun createFeedback(request: FeedbackCreateRequest, clientIp: String) {
        val now = OffsetDateTime.now(ZoneOffset.UTC)

        val blockedUntil = blockedUntilByIp[clientIp]
        if (blockedUntil != null && blockedUntil.isAfter(now)) {
            throw TooManyRequestsException("일시적으로 차단되었습니다. 잠시 후 다시 시도해주세요.")
        }

        val oneMinuteAgo = now.minusMinutes(1)
        val sends = recentSendsByIp.getOrPut(clientIp) { mutableListOf() }
        sends.removeIf { it.isBefore(oneMinuteAgo) }

        if (sends.size >= RATE_LIMIT_PER_MINUTE) {
            if (sends.size >= BLOCK_THRESHOLD - 1) {
                blockedUntilByIp[clientIp] = now.plusHours(BLOCK_HOURS)
            }
            throw TooManyRequestsException("잠시 후 다시 시도해주세요.")
        }

        feedbackRepository.save(
            Feedback(
                content = request.content.trim(),
                ipAddress = clientIp
            )
        )

        sends.add(now)
    }
}
