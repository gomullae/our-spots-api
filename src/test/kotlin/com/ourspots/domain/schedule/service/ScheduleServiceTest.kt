package com.ourspots.domain.schedule.service

import com.ourspots.api.dto.ScheduleEventRequest
import com.ourspots.api.dto.ScheduleMemoRequest
import com.ourspots.api.dto.SchedulePhotoAddedRequest
import com.ourspots.common.exception.LimitExceededException
import com.ourspots.common.exception.NotFoundException
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.domain.photo.service.PhotoService
import com.ourspots.domain.schedule.entity.ScheduleCategory
import com.ourspots.domain.schedule.entity.ScheduleEvent
import com.ourspots.domain.schedule.entity.ScheduleMemo
import com.ourspots.domain.schedule.repository.ScheduleEventRepository
import com.ourspots.domain.schedule.repository.ScheduleMemoRepository
import io.mockk.MockKAnnotations
import io.mockk.Runs
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

class ScheduleServiceTest {

    @MockK
    private lateinit var scheduleEventRepository: ScheduleEventRepository

    @MockK
    private lateinit var scheduleMemoRepository: ScheduleMemoRepository

    @MockK(relaxed = true)
    private lateinit var telegramNotificationService: TelegramNotificationService

    @MockK(relaxed = true)
    private lateinit var photoService: PhotoService

    @InjectMockKs
    private lateinit var scheduleService: ScheduleService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        // 사진 기능은 별도 테스트에서 검증 — 여기선 항상 빈 목록을 반환하게 해서 기존 테스트 로직에 영향 없게 함
        every { photoService.listByEntity(any(), any()) } returns emptyList()
        every { photoService.listByEntities(any(), any()) } returns emptyMap()
        // 메모도 마찬가지로 기본값은 빈 목록 — 메모 자체를 검증하는 테스트에서만 별도로 stub
        every { scheduleMemoRepository.findByScheduleEventIdInOrderByCreatedAtAsc(any<Collection<Long>>()) } returns emptyList()
        every { scheduleMemoRepository.findByScheduleEventIdOrderByCreatedAtAsc(any()) } returns emptyList()
        // addMemo/updateMemo/deleteMemo가 캐시 무효화를 위해 항상 호출 — 반환값 자체는 검증 대상 아니라 공통 기본값
        every { scheduleEventRepository.touchUpdatedAt(any()) } returns 1
    }

    private fun createEvent(
        id: Long,
        startAt: LocalDateTime,
        endAt: LocalDateTime = startAt,
        category: ScheduleCategory = ScheduleCategory.SHARED,
        title: String = "일정",
        allDay: Boolean = false
    ) = ScheduleEvent(
        id = id,
        title = title,
        category = category,
        startAt = startAt,
        endAt = endAt,
        allDay = allDay
    )

    @Nested
    @DisplayName("getMeta")
    inner class GetMeta {

        @Test
        fun getMeta_shouldReturnCountAndLastModified() {
            val lastModified = LocalDateTime.of(2026, 8, 20, 9, 0)
            every { scheduleEventRepository.count() } returns 3L
            every { scheduleEventRepository.findMaxUpdatedAt() } returns lastModified

            val result = scheduleService.getMeta()

            assertEquals(3L, result.count)
            assertEquals(lastModified, result.lastModified)
        }

        @Test
        fun getMeta_whenNoEvents_shouldReturnZeroCountAndNullLastModified() {
            every { scheduleEventRepository.count() } returns 0L
            every { scheduleEventRepository.findMaxUpdatedAt() } returns null

            val result = scheduleService.getMeta()

            assertEquals(0L, result.count)
            assertEquals(null, result.lastModified)
        }
    }

    @Nested
    @DisplayName("getEvents")
    inner class GetEvents {

        @Test
        fun getEvents_shouldReturnOverlappingEvents() {
            val start = LocalDateTime.of(2026, 8, 1, 0, 0)
            val end = LocalDateTime.of(2026, 8, 31, 23, 59)
            val events = listOf(createEvent(1L, LocalDateTime.of(2026, 8, 10, 10, 0)))
            every { scheduleEventRepository.findOverlapping(start, end, false) } returns events

            val result = scheduleService.getEvents(start, end)

            assertEquals(1, result.size)
            verify { scheduleEventRepository.findOverlapping(start, end, false) }
        }

        @Test
        fun getEvents_whenIncludeDeletedTrue_shouldPassThroughToRepository() {
            val start = LocalDateTime.of(2026, 8, 1, 0, 0)
            val end = LocalDateTime.of(2026, 8, 31, 23, 59)
            every { scheduleEventRepository.findOverlapping(start, end, true) } returns emptyList()

            scheduleService.getEvents(start, end, includeDeleted = true)

            verify { scheduleEventRepository.findOverlapping(start, end, true) }
        }

        @Test
        fun getEvents_shouldIncludeMemosGroupedByEvent() {
            val start = LocalDateTime.of(2026, 8, 1, 0, 0)
            val end = LocalDateTime.of(2026, 8, 31, 23, 59)
            val events = listOf(createEvent(1L, LocalDateTime.of(2026, 8, 10, 10, 0)))
            every { scheduleEventRepository.findOverlapping(start, end, false) } returns events
            every { scheduleMemoRepository.findByScheduleEventIdInOrderByCreatedAtAsc(listOf(1L)) } returns
                listOf(ScheduleMemo(id = 1L, scheduleEventId = 1L, content = "주차는 지하 2층"))

            val result = scheduleService.getEvents(start, end)

            assertEquals(1, result[0].memos.size)
            assertEquals("주차는 지하 2층", result[0].memos[0].content)
        }
    }

    @Nested
    @DisplayName("createEvent")
    inner class CreateEvent {

        @Test
        fun createEvent_shouldSaveAndReturnEvent() {
            val request = ScheduleEventRequest(
                title = "커피약속",
                category = ScheduleCategory.JINWOO,
                startAt = LocalDateTime.of(2026, 8, 10, 10, 0),
                endAt = LocalDateTime.of(2026, 8, 10, 11, 30),
                allDay = false
            )
            every { scheduleEventRepository.save(any<ScheduleEvent>()) } answers { firstArg() }

            val result = scheduleService.createEvent(request)

            assertEquals("커피약속", result.title)
            assertEquals(ScheduleCategory.JINWOO, result.category)
            verify { telegramNotificationService.notifyScheduleCreated(any()) }
        }
    }

    @Nested
    @DisplayName("updateEvent")
    inner class UpdateEvent {

        @Test
        fun updateEvent_whenExists_shouldUpdateInPlace() {
            val existing = createEvent(1L, LocalDateTime.of(2026, 8, 10, 10, 0))
            val request = ScheduleEventRequest(
                title = "변경된 제목",
                category = ScheduleCategory.CHOYOUNG,
                startAt = LocalDateTime.of(2026, 8, 11, 9, 0),
                endAt = LocalDateTime.of(2026, 8, 11, 9, 0),
                allDay = true
            )
            every { scheduleEventRepository.findById(1L) } returns Optional.of(existing)
            every { scheduleEventRepository.save(any<ScheduleEvent>()) } answers { firstArg() }

            val result = scheduleService.updateEvent(1L, request)

            assertEquals(1L, result.id)
            assertEquals("변경된 제목", result.title)
            assertEquals(ScheduleCategory.CHOYOUNG, result.category)
            assertEquals(true, result.allDay)
            verify { telegramNotificationService.notifyScheduleUpdated(any(), any()) }
        }

        @Test
        fun updateEvent_whenNotFound_shouldThrowNotFoundException() {
            val request = ScheduleEventRequest(
                title = "제목",
                category = ScheduleCategory.COMMON,
                startAt = LocalDateTime.now(),
                endAt = LocalDateTime.now(),
                allDay = true
            )
            every { scheduleEventRepository.findById(99L) } returns Optional.empty()

            assertThrows<NotFoundException> {
                scheduleService.updateEvent(99L, request)
            }
        }
    }

    @Nested
    @DisplayName("deleteEvent")
    inner class DeleteEvent {

        @Test
        fun deleteEvent_whenExists_shouldDelete() {
            val event = createEvent(1L, LocalDateTime.now())
            every { scheduleEventRepository.findById(1L) } returns Optional.of(event)
            every { scheduleEventRepository.delete(event) } just Runs

            scheduleService.deleteEvent(1L)

            verify { scheduleEventRepository.delete(event) }
        }

        @Test
        fun deleteEvent_whenNotFound_shouldThrowNotFoundException() {
            every { scheduleEventRepository.findById(99L) } returns Optional.empty()

            assertThrows<NotFoundException> {
                scheduleService.deleteEvent(99L)
            }
        }
    }

    @Nested
    @DisplayName("restoreEvent")
    inner class RestoreEvent {

        @Test
        fun restoreEvent_whenExists_shouldClearDeletedAt() {
            val event = createEvent(1L, LocalDateTime.now())
            event.deletedAt = LocalDateTime.now()
            every { scheduleEventRepository.findByIdIncludingDeleted(1L) } returns event
            every { scheduleEventRepository.save(any<ScheduleEvent>()) } answers { firstArg() }

            val result = scheduleService.restoreEvent(1L)

            assertEquals(1L, result.id)
            assertEquals(null, result.deletedAt)
        }

        @Test
        fun restoreEvent_whenNotFound_shouldThrowNotFoundException() {
            every { scheduleEventRepository.findByIdIncludingDeleted(99L) } returns null

            assertThrows<NotFoundException> {
                scheduleService.restoreEvent(99L)
            }
        }
    }

    @Nested
    @DisplayName("addMemo")
    inner class AddMemo {

        @Test
        fun addMemo_whenUnderLimit_shouldSaveAndNotify() {
            val event = createEvent(1L, LocalDateTime.now(), title = "헬스케어센터 서대문")
            every { scheduleEventRepository.findById(1L) } returns Optional.of(event)
            every { scheduleMemoRepository.countByScheduleEventId(1L) } returns 2L
            every { scheduleMemoRepository.save(any<ScheduleMemo>()) } answers { firstArg() }

            val result = scheduleService.addMemo(1L, ScheduleMemoRequest(content = "주차는 지하 2층"))

            assertEquals("주차는 지하 2층", result.content)
            verify { telegramNotificationService.notifyScheduleMemoAdded("헬스케어센터 서대문", "주차는 지하 2층") }
            // ScheduleMemo는 ScheduleEvent와 FK 없이 느슨하게 연결돼있어 저장만으로는 event.updatedAt이 안 바뀜 —
            // 프론트가 /api/schedules/meta로 캐시 유효성을 판단하므로 touchUpdatedAt()을 직접 호출해야 캐시가 무효화됨
            verify { scheduleEventRepository.touchUpdatedAt(1L) }
        }

        @Test
        fun addMemo_whenAtLimit_shouldThrowLimitExceededException() {
            val event = createEvent(1L, LocalDateTime.now())
            every { scheduleEventRepository.findById(1L) } returns Optional.of(event)
            every { scheduleMemoRepository.countByScheduleEventId(1L) } returns 10L

            assertThrows<LimitExceededException> {
                scheduleService.addMemo(1L, ScheduleMemoRequest(content = "11번째 메모"))
            }
            verify(exactly = 0) { scheduleMemoRepository.save(any<ScheduleMemo>()) }
        }

        @Test
        fun addMemo_whenEventNotFound_shouldThrowNotFoundException() {
            every { scheduleEventRepository.findById(99L) } returns Optional.empty()

            assertThrows<NotFoundException> {
                scheduleService.addMemo(99L, ScheduleMemoRequest(content = "메모"))
            }
        }
    }

    @Nested
    @DisplayName("notifyPhotosAdded")
    inner class NotifyPhotosAdded {

        @Test
        fun notifyPhotosAdded_whenEventExists_shouldNotify() {
            val event = createEvent(1L, LocalDateTime.now(), title = "헬스케어센터 서대문")
            every { scheduleEventRepository.findById(1L) } returns Optional.of(event)

            scheduleService.notifyPhotosAdded(1L, SchedulePhotoAddedRequest(count = 3))

            verify { telegramNotificationService.notifyScheduleEventPhotoAdded("헬스케어센터 서대문", 3) }
        }

        @Test
        fun notifyPhotosAdded_whenEventNotFound_shouldThrowNotFoundException() {
            every { scheduleEventRepository.findById(99L) } returns Optional.empty()

            assertThrows<NotFoundException> {
                scheduleService.notifyPhotosAdded(99L, SchedulePhotoAddedRequest(count = 1))
            }
        }
    }

    @Nested
    @DisplayName("updateMemo")
    inner class UpdateMemo {

        @Test
        fun updateMemo_whenBelongsToEvent_shouldUpdateContent() {
            val memo = ScheduleMemo(id = 10L, scheduleEventId = 1L, content = "원본 메모")
            every { scheduleMemoRepository.findById(10L) } returns Optional.of(memo)
            every { scheduleMemoRepository.save(any<ScheduleMemo>()) } answers { firstArg() }

            val result = scheduleService.updateMemo(1L, 10L, ScheduleMemoRequest(content = "수정된 메모"))

            assertEquals("수정된 메모", result.content)
            verify(exactly = 0) { telegramNotificationService.notifyScheduleMemoAdded(any(), any()) }
            verify { scheduleEventRepository.touchUpdatedAt(1L) }
        }

        @Test
        fun updateMemo_whenBelongsToDifferentEvent_shouldThrow() {
            val memo = ScheduleMemo(id = 10L, scheduleEventId = 2L, content = "메모")
            every { scheduleMemoRepository.findById(10L) } returns Optional.of(memo)

            assertThrows<IllegalArgumentException> {
                scheduleService.updateMemo(1L, 10L, ScheduleMemoRequest(content = "수정"))
            }
        }

        @Test
        fun updateMemo_whenNotFound_shouldThrowNotFoundException() {
            every { scheduleMemoRepository.findById(99L) } returns Optional.empty()

            assertThrows<NotFoundException> {
                scheduleService.updateMemo(1L, 99L, ScheduleMemoRequest(content = "수정"))
            }
        }
    }

    @Nested
    @DisplayName("deleteMemo")
    inner class DeleteMemo {

        @Test
        fun deleteMemo_whenBelongsToEvent_shouldDelete() {
            val memo = ScheduleMemo(id = 10L, scheduleEventId = 1L, content = "메모")
            every { scheduleMemoRepository.findById(10L) } returns Optional.of(memo)
            every { scheduleMemoRepository.delete(memo) } just Runs

            scheduleService.deleteMemo(1L, 10L)

            verify { scheduleMemoRepository.delete(memo) }
            verify { scheduleEventRepository.touchUpdatedAt(1L) }
        }

        @Test
        fun deleteMemo_whenBelongsToDifferentEvent_shouldThrow() {
            val memo = ScheduleMemo(id = 10L, scheduleEventId = 2L, content = "메모")
            every { scheduleMemoRepository.findById(10L) } returns Optional.of(memo)

            assertThrows<IllegalArgumentException> {
                scheduleService.deleteMemo(1L, 10L)
            }
        }

        @Test
        fun deleteMemo_whenNotFound_shouldThrowNotFoundException() {
            every { scheduleMemoRepository.findById(99L) } returns Optional.empty()

            assertThrows<NotFoundException> {
                scheduleService.deleteMemo(1L, 99L)
            }
        }
    }
}
