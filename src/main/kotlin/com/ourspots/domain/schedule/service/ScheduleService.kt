package com.ourspots.domain.schedule.service

import com.ourspots.api.dto.ScheduleEventRequest
import com.ourspots.api.dto.ScheduleEventResponse
import com.ourspots.api.dto.ScheduleMetaResponse
import com.ourspots.common.exception.NotFoundException
import com.ourspots.common.notification.ScheduleEventSummary
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.domain.photo.entity.PhotoEntityType
import com.ourspots.domain.photo.service.PhotoService
import com.ourspots.domain.schedule.entity.ScheduleCategory
import com.ourspots.domain.schedule.entity.ScheduleEvent
import com.ourspots.domain.schedule.repository.ScheduleEventRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ScheduleService(
    private val scheduleEventRepository: ScheduleEventRepository,
    private val telegramNotificationService: TelegramNotificationService,
    private val photoService: PhotoService
) {

    fun getEvents(start: LocalDateTime, end: LocalDateTime, includeDeleted: Boolean = false): List<ScheduleEventResponse> {
        val events = scheduleEventRepository.findOverlapping(start, end, includeDeleted)
        val photosByEventId = photoService.listByEntities(PhotoEntityType.SCHEDULE_EVENT, events.map { it.id })
        return events.map { ScheduleEventResponse.from(it, photosByEventId[it.id] ?: emptyList()) }
    }

    // 프론트가 로컬 캐시를 그대로 써도 되는지 확인하는 용도 — count(등록/삭제 감지) + lastModified(수정 감지) 조합
    fun getMeta(): ScheduleMetaResponse =
        ScheduleMetaResponse(count = scheduleEventRepository.count(), lastModified = scheduleEventRepository.findMaxUpdatedAt())

    // NOT_SUPPORTED: 텔레그램 발송(최대 수 초 소요되는 외부 HTTP 호출)이 DB 트랜잭션 안에 들어있으면 그 시간 내내
    // 커넥션 풀(운영 5개, 앱 전체 공유)의 커넥션 하나를 붙잡고 있게 됨 — save()는 Spring Data JPA 리포지토리 자체가
    // 짧은 자체 트랜잭션으로 처리하므로, 이 메서드 레벨에서는 트랜잭션을 열지 않아도 저장은 그대로 원자적으로 처리됨
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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

    // createEvent와 동일한 이유(위 주석 참고) — findById/save 각각 리포지토리 자체 트랜잭션으로 원자적으로 처리되고,
    // 그 사이 조회한 detached 엔티티를 메모리에서 수정 후 save()에 넘기는 것도 JPA merge로 정상 동작함
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
        return ScheduleEventResponse.from(saved, photoService.listByEntity(PhotoEntityType.SCHEDULE_EVENT, saved.id))
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
        val saved = scheduleEventRepository.save(event)
        return ScheduleEventResponse.from(saved, photoService.listByEntity(PhotoEntityType.SCHEDULE_EVENT, saved.id))
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
