package com.ourspots.domain.schedule.service

import com.ourspots.api.dto.ScheduleEventRequest
import com.ourspots.api.dto.ScheduleEventResponse
import com.ourspots.api.dto.ScheduleMetaResponse
import com.ourspots.common.exception.NotFoundException
import com.ourspots.common.notification.ScheduleEventSummary
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.domain.schedule.entity.ScheduleCategory
import com.ourspots.domain.schedule.entity.ScheduleEvent
import com.ourspots.domain.schedule.repository.ScheduleEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ScheduleService(
    private val scheduleEventRepository: ScheduleEventRepository,
    private val telegramNotificationService: TelegramNotificationService
) {

    fun getEvents(start: LocalDateTime, end: LocalDateTime, includeDeleted: Boolean = false): List<ScheduleEventResponse> =
        scheduleEventRepository.findOverlapping(start, end, includeDeleted).map { ScheduleEventResponse.from(it) }

    // 프론트가 로컬 캐시를 그대로 써도 되는지 확인하는 용도 — count(등록/삭제 감지) + lastModified(수정 감지) 조합
    fun getMeta(): ScheduleMetaResponse =
        ScheduleMetaResponse(count = scheduleEventRepository.count(), lastModified = scheduleEventRepository.findMaxUpdatedAt())

    @Transactional
    fun createEvent(request: ScheduleEventRequest): ScheduleEventResponse {
        val event = ScheduleEvent(
            title = request.title,
            category = request.category,
            startAt = request.startAt,
            endAt = request.endAt,
            allDay = request.allDay,
            memo = request.memo
        )
        val saved = scheduleEventRepository.save(event)
        telegramNotificationService.notifyScheduleCreated(toSummary(saved))
        return ScheduleEventResponse.from(saved)
    }

    @Transactional
    fun updateEvent(id: Long, request: ScheduleEventRequest): ScheduleEventResponse {
        val event = scheduleEventRepository.findById(id)
            .orElseThrow { NotFoundException("Schedule event not found: $id") }
        val before = toSummary(event)

        event.title = request.title
        event.category = request.category
        event.startAt = request.startAt
        event.endAt = request.endAt
        event.allDay = request.allDay
        event.memo = request.memo

        val saved = scheduleEventRepository.save(event)
        telegramNotificationService.notifyScheduleUpdated(before, toSummary(saved))
        return ScheduleEventResponse.from(saved)
    }

    @Transactional
    fun deleteEvent(id: Long) {
        val event = scheduleEventRepository.findById(id)
            .orElseThrow { NotFoundException("Schedule event not found: $id") }
        scheduleEventRepository.delete(event)
    }

    @Transactional
    fun restoreEvent(id: Long): ScheduleEventResponse {
        val event = scheduleEventRepository.findByIdIncludingDeleted(id)
            ?: throw NotFoundException("Schedule event not found: $id")
        event.deletedAt = null
        return ScheduleEventResponse.from(scheduleEventRepository.save(event))
    }

    // ScheduleCategory 등 도메인 타입을 common(TelegramNotificationService)에 노출하지 않기 위해 사람이 읽는 문자열로 미리 변환
    private fun toSummary(event: ScheduleEvent) = ScheduleEventSummary(
        title = event.title,
        categoryLabel = categoryLabel(event.category),
        dateTimeText = formatDateTimeRange(event.startAt, event.endAt, event.allDay),
        memo = event.memo?.takeIf { it.isNotBlank() }
    )

    // when(exhaustive)이라 ScheduleCategory에 새 값이 추가되면 컴파일 에러로 바로 드러남 — Map.getValue()였다면 런타임에서야 NoSuchElementException으로 발견됐을 것
    private fun categoryLabel(category: ScheduleCategory): String = when (category) {
        ScheduleCategory.COMMON -> "공통 일정"
        ScheduleCategory.SHARED -> "공유 일정"
        ScheduleCategory.MUST_CHECK -> "필수 체크"
        ScheduleCategory.JINWOO -> "진우 일정"
        ScheduleCategory.CHOYOUNG -> "초영 일정"
        ScheduleCategory.WORK_FROM_HOME -> "재택 근무"
        ScheduleCategory.HOLIDAY -> "공휴일"
        ScheduleCategory.TRAVEL -> "여행"
        ScheduleCategory.ANNIVERSARY -> "기념일"
    }

    companion object {
        private val WEEKDAY_LABELS = mapOf(
            DayOfWeek.MONDAY to "월", DayOfWeek.TUESDAY to "화", DayOfWeek.WEDNESDAY to "수",
            DayOfWeek.THURSDAY to "목", DayOfWeek.FRIDAY to "금", DayOfWeek.SATURDAY to "토", DayOfWeek.SUNDAY to "일"
        )

        private fun formatDate(dateTime: LocalDateTime): String =
            "${dateTime.monthValue}월 ${dateTime.dayOfMonth}일(${WEEKDAY_LABELS.getValue(dateTime.dayOfWeek)})"

        private fun formatTime(dateTime: LocalDateTime): String {
            val period = if (dateTime.hour < 12) "오전" else "오후"
            val displayHour = if (dateTime.hour % 12 == 0) 12 else dateTime.hour % 12
            return "$period $displayHour:${"%02d".format(dateTime.minute)}"
        }

        private fun formatDateTimeRange(startAt: LocalDateTime, endAt: LocalDateTime, allDay: Boolean): String {
            if (!allDay) return "${formatDate(startAt)} ${formatTime(startAt)}"
            return if (startAt.toLocalDate() == endAt.toLocalDate()) {
                formatDate(startAt)
            } else {
                "${formatDate(startAt)} ~ ${formatDate(endAt)}"
            }
        }
    }
}
