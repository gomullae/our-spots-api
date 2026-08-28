package com.ourspots.domain.schedule.service

import com.ourspots.api.dto.ScheduleEventRequest
import com.ourspots.common.exception.NotFoundException
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.domain.photo.service.PhotoService
import com.ourspots.domain.schedule.entity.ScheduleCategory
import com.ourspots.domain.schedule.entity.ScheduleEvent
import com.ourspots.domain.schedule.repository.ScheduleEventRepository
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
    }

    private fun createEvent(
        id: Long,
        startAt: LocalDateTime,
        endAt: LocalDateTime = startAt,
        category: ScheduleCategory = ScheduleCategory.SHARED,
        title: String = "일정",
        allDay: Boolean = false,
        memo: String? = null
    ) = ScheduleEvent(
        id = id,
        title = title,
        category = category,
        startAt = startAt,
        endAt = endAt,
        allDay = allDay,
        memo = memo
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
                allDay = false,
                memo = "1시간반"
            )
            every { scheduleEventRepository.save(any<ScheduleEvent>()) } answers { firstArg() }

            val result = scheduleService.createEvent(request)

            assertEquals("커피약속", result.title)
            assertEquals(ScheduleCategory.JINWOO, result.category)
            assertEquals("1시간반", result.memo)
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
                allDay = true,
                memo = null
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
}
