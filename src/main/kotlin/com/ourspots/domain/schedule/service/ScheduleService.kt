package com.ourspots.domain.schedule.service

import com.ourspots.api.dto.ScheduleEventRequest
import com.ourspots.api.dto.ScheduleEventResponse
import com.ourspots.api.dto.ScheduleMemoRequest
import com.ourspots.api.dto.ScheduleMemoResponse
import com.ourspots.api.dto.ScheduleMetaResponse
import com.ourspots.api.dto.SchedulePhotoAddedRequest
import com.ourspots.common.exception.LimitExceededException
import com.ourspots.common.notification.ScheduleEventSummary
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.common.util.findByIdOrThrow
import com.ourspots.common.util.restoreSoftDeleted
import com.ourspots.domain.photo.entity.PhotoEntityType
import com.ourspots.domain.photo.service.PhotoService
import com.ourspots.domain.schedule.entity.ScheduleCategory
import com.ourspots.domain.schedule.entity.ScheduleEvent
import com.ourspots.domain.schedule.entity.ScheduleMemo
import com.ourspots.domain.schedule.repository.ScheduleEventRepository
import com.ourspots.domain.schedule.repository.ScheduleMemoRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.DayOfWeek
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class ScheduleService(
    private val scheduleEventRepository: ScheduleEventRepository,
    private val scheduleMemoRepository: ScheduleMemoRepository,
    private val telegramNotificationService: TelegramNotificationService,
    private val photoService: PhotoService
) {

    fun getEvents(start: LocalDateTime, end: LocalDateTime, includeDeleted: Boolean = false): List<ScheduleEventResponse> {
        val events = scheduleEventRepository.findOverlapping(start, end, includeDeleted)
        val eventIds = events.map { it.id }
        val photosByEventId = photoService.listByEntities(PhotoEntityType.SCHEDULE_EVENT, eventIds)
        // 빈 IN절은 DB에 따라 문제가 될 수 있어 photoService.listByEntities와 동일하게 방어
        val memosByEventId = if (eventIds.isEmpty()) emptyMap() else scheduleMemoRepository.findByScheduleEventIdInOrderByCreatedAtAsc(eventIds)
            .groupBy({ it.scheduleEventId }, { ScheduleMemoResponse.from(it) })
        return events.map { ScheduleEventResponse.from(it, photosByEventId[it.id] ?: emptyList(), memosByEventId[it.id] ?: emptyList()) }
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
            // @field:NotNull 검증을 이미 통과했으므로 이 시점엔 항상 non-null
            allDay = request.allDay!!
        )
        val saved = scheduleEventRepository.save(event)
        telegramNotificationService.notifyScheduleCreated(toSummary(saved, request.newPhotoCount))
        return ScheduleEventResponse.from(saved)
    }

    // createEvent와 동일한 이유(위 주석 참고) — findById/save 각각 리포지토리 자체 트랜잭션으로 원자적으로 처리되고,
    // 그 사이 조회한 detached 엔티티를 메모리에서 수정 후 save()에 넘기는 것도 JPA merge로 정상 동작함
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun updateEvent(id: Long, request: ScheduleEventRequest): ScheduleEventResponse {
        val event = scheduleEventRepository.findByIdOrThrow(id, "Schedule event")
        val before = toSummary(event)

        event.title = request.title
        event.category = request.category
        event.startAt = request.startAt
        event.endAt = request.endAt
        event.allDay = request.allDay!!

        val saved = scheduleEventRepository.save(event)
        telegramNotificationService.notifyScheduleUpdated(before, toSummary(saved, request.newPhotoCount))
        return ScheduleEventResponse.from(
            saved,
            photoService.listByEntity(PhotoEntityType.SCHEDULE_EVENT, saved.id),
            scheduleMemoRepository.findByScheduleEventIdOrderByCreatedAtAsc(saved.id).map { ScheduleMemoResponse.from(it) }
        )
    }

    @Transactional
    fun deleteEvent(id: Long) {
        val event = scheduleEventRepository.findByIdOrThrow(id, "Schedule event")
        scheduleEventRepository.delete(event)
    }

    @Transactional
    fun restoreEvent(id: Long): ScheduleEventResponse {
        val saved = restoreSoftDeleted(id, "Schedule event", scheduleEventRepository::findByIdIncludingDeleted) { scheduleEventRepository.save(it) }
        return ScheduleEventResponse.from(
            saved,
            photoService.listByEntity(PhotoEntityType.SCHEDULE_EVENT, saved.id),
            scheduleMemoRepository.findByScheduleEventIdOrderByCreatedAtAsc(saved.id).map { ScheduleMemoResponse.from(it) }
        )
    }

    // 메모 추가 — 개수 상한(MAX_MEMOS_PER_EVENT) 초과 시 400. 텔레그램은 등록/수정 알림과 별개로 그때그때 바로 알림.
    // ScheduleMemo는 ScheduleEvent와 FK/연관관계 없이 scheduleEventId 컬럼으로만 느슨하게 연결돼있어(Photo와 동일한 관례)
    // 저장해도 ScheduleEvent.updatedAt이 자동으로 안 바뀜 — touchUpdatedAt()으로 직접 갱신 안 하면 프론트가
    // /api/schedules/meta로 "변경 없음"이라 오판하고 낡은 로컬 캐시를 계속 씀(PhotoService.touchParentIfNeeded와 동일한 이유)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun addMemo(scheduleEventId: Long, request: ScheduleMemoRequest): ScheduleMemoResponse {
        val event = scheduleEventRepository.findByIdOrThrow(scheduleEventId, "Schedule event")
        val currentCount = scheduleMemoRepository.countByScheduleEventId(scheduleEventId)
        if (currentCount >= MAX_MEMOS_PER_EVENT) {
            throw LimitExceededException("메모는 일정당 최대 ${MAX_MEMOS_PER_EVENT}개까지만 추가할 수 있습니다.")
        }
        val saved = scheduleMemoRepository.save(ScheduleMemo(scheduleEventId = scheduleEventId, content = request.content))
        scheduleEventRepository.touchUpdatedAt(scheduleEventId)
        telegramNotificationService.notifyScheduleMemoAdded(event.title, saved.content)
        return ScheduleMemoResponse.from(saved)
    }

    // 상세보기에서 붙여넣기로 사진을 바로 추가한 직후 프론트가 호출 — 사진 저장 자체는 이미 끝난 상태라
    // 여기선 알림만 발송(addMemo와 동일하게 외부 HTTP 호출이라 트랜잭션 밖에서 처리)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun notifyPhotosAdded(scheduleEventId: Long, request: SchedulePhotoAddedRequest) {
        val event = scheduleEventRepository.findByIdOrThrow(scheduleEventId, "Schedule event")
        telegramNotificationService.notifyScheduleEventPhotoAdded(event.title, request.count)
    }

    // 수정/삭제 둘 다 다른 도메인과 동일하게 알림 대상 아님(알림은 "추가"에만) — 캐시 무효화를 위한
    // touchUpdatedAt()은 addMemo와 동일한 이유로 여전히 필요(알림 여부와 무관)
    @Transactional
    fun updateMemo(scheduleEventId: Long, memoId: Long, request: ScheduleMemoRequest): ScheduleMemoResponse {
        val memo = scheduleMemoRepository.findByIdOrThrow(memoId, "Schedule memo")
        require(memo.scheduleEventId == scheduleEventId) { "메모가 해당 일정에 속하지 않습니다." }
        memo.content = request.content
        val saved = scheduleMemoRepository.save(memo)
        scheduleEventRepository.touchUpdatedAt(scheduleEventId)
        return ScheduleMemoResponse.from(saved)
    }

    @Transactional
    fun deleteMemo(scheduleEventId: Long, memoId: Long) {
        val memo = scheduleMemoRepository.findByIdOrThrow(memoId, "Schedule memo")
        require(memo.scheduleEventId == scheduleEventId) { "메모가 해당 일정에 속하지 않습니다." }
        scheduleMemoRepository.delete(memo)
        scheduleEventRepository.touchUpdatedAt(scheduleEventId)
    }

    // ScheduleCategory 등 도메인 타입을 common(TelegramNotificationService)에 노출하지 않기 위해 사람이 읽는 문자열로 미리 변환
    private fun toSummary(event: ScheduleEvent, newPhotoCount: Int = 0) = ScheduleEventSummary(
        title = event.title,
        categoryLabel = categoryLabel(event.category),
        dateTimeText = formatDateTimeRange(event.startAt, event.endAt, event.allDay),
        newPhotoCount = newPhotoCount
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
        // 메모가 무한정 쌓이는 걸 막기 위한 일정당 상한 — 초과 시 LimitExceededException(400)
        private const val MAX_MEMOS_PER_EVENT = 10

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
