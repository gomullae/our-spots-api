package com.ourspots.domain.schedule.repository

import com.ourspots.domain.schedule.entity.ScheduleCategory
import com.ourspots.domain.schedule.entity.ScheduleEvent
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DataJpaTest
@ActiveProfiles("test")
class ScheduleEventRepositoryTest {

    @Autowired
    private lateinit var scheduleEventRepository: ScheduleEventRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @BeforeEach
    fun setUp() {
        scheduleEventRepository.deleteAll()
        entityManager.flush()
        entityManager.clear()
    }

    @Nested
    @DisplayName("findOverlapping")
    inner class FindOverlapping {

        @Test
        fun findOverlapping_shouldIncludeEventFullyWithinRange() {
            createEvent(LocalDateTime.of(2026, 8, 10, 10, 0), LocalDateTime.of(2026, 8, 10, 11, 0))

            val result = queryAugust()

            assertEquals(1, result.size)
        }

        @Test
        fun findOverlapping_shouldIncludeMultiDayEventStartingBeforeRange() {
            createEvent(LocalDateTime.of(2026, 7, 29, 0, 0), LocalDateTime.of(2026, 8, 2, 0, 0))

            val result = queryAugust()

            assertEquals(1, result.size)
        }

        @Test
        fun findOverlapping_shouldIncludeMultiDayEventEndingAfterRange() {
            createEvent(LocalDateTime.of(2026, 8, 30, 0, 0), LocalDateTime.of(2026, 9, 2, 0, 0))

            val result = queryAugust()

            assertEquals(1, result.size)
        }

        @Test
        fun findOverlapping_shouldExcludeEventFullyOutsideRange() {
            createEvent(LocalDateTime.of(2026, 9, 5, 10, 0), LocalDateTime.of(2026, 9, 5, 11, 0))

            val result = queryAugust()

            assertTrue(result.isEmpty())
        }

        @Test
        fun findOverlapping_whenIncludeDeletedFalse_shouldExcludeDeleted() {
            val event = createEvent(LocalDateTime.of(2026, 8, 10, 10, 0), LocalDateTime.of(2026, 8, 10, 11, 0))
            scheduleEventRepository.delete(event)
            entityManager.flush()
            entityManager.clear()

            val result = queryAugust(includeDeleted = false)

            assertTrue(result.isEmpty())
        }

        @Test
        fun findOverlapping_whenIncludeDeletedTrue_shouldIncludeDeleted() {
            val event = createEvent(LocalDateTime.of(2026, 8, 10, 10, 0), LocalDateTime.of(2026, 8, 10, 11, 0))
            scheduleEventRepository.delete(event)
            entityManager.flush()
            entityManager.clear()

            val result = queryAugust(includeDeleted = true)

            assertEquals(1, result.size)
        }

        private fun queryAugust(includeDeleted: Boolean = false) =
            scheduleEventRepository.findOverlapping(
                LocalDateTime.of(2026, 8, 1, 0, 0),
                LocalDateTime.of(2026, 8, 31, 23, 59, 59),
                includeDeleted
            )
    }

    @Nested
    @DisplayName("findByIdIncludingDeleted")
    inner class FindByIdIncludingDeleted {

        @Test
        fun findByIdIncludingDeleted_whenSoftDeleted_shouldStillReturnEvent() {
            val event = createEvent(LocalDateTime.of(2026, 8, 10, 10, 0), LocalDateTime.of(2026, 8, 10, 11, 0))
            scheduleEventRepository.delete(event)
            entityManager.flush()
            entityManager.clear()

            val result = scheduleEventRepository.findByIdIncludingDeleted(event.id)

            assertEquals(event.id, result?.id)
        }

        @Test
        fun findByIdIncludingDeleted_whenNotExists_shouldReturnNull() {
            val result = scheduleEventRepository.findByIdIncludingDeleted(99999L)

            assertEquals(null, result)
        }
    }

    @Nested
    @DisplayName("findMaxUpdatedAt")
    inner class FindMaxUpdatedAt {

        @Test
        fun findMaxUpdatedAt_whenNoEvents_shouldReturnNull() {
            val result = scheduleEventRepository.findMaxUpdatedAt()

            assertEquals(null, result)
        }

        @Test
        fun findMaxUpdatedAt_shouldReturnLatestUpdatedAtAcrossEvents() {
            val first = createEvent(LocalDateTime.of(2026, 8, 10, 10, 0), LocalDateTime.of(2026, 8, 10, 11, 0))
            createEvent(LocalDateTime.of(2026, 8, 11, 10, 0), LocalDateTime.of(2026, 8, 11, 11, 0))
            entityManager.clear()

            val toUpdate = scheduleEventRepository.findById(first.id).get()
            toUpdate.title = "변경된 제목"
            scheduleEventRepository.save(toUpdate)
            entityManager.flush()
            entityManager.clear()

            val result = scheduleEventRepository.findMaxUpdatedAt()

            assertEquals(toUpdate.updatedAt, result)
        }

        @Test
        fun findMaxUpdatedAt_shouldIgnoreSoftDeletedEvents() {
            val event = createEvent(LocalDateTime.of(2026, 8, 10, 10, 0), LocalDateTime.of(2026, 8, 10, 11, 0))
            scheduleEventRepository.delete(event)
            entityManager.flush()
            entityManager.clear()

            val result = scheduleEventRepository.findMaxUpdatedAt()

            assertEquals(null, result)
        }
    }

    @Nested
    @DisplayName("findAllIncludingDeleted")
    inner class FindAllIncludingDeleted {

        @Test
        fun findAllIncludingDeleted_shouldIncludeSoftDeletedEvents() {
            val active = createEvent(LocalDateTime.of(2026, 8, 10, 10, 0), LocalDateTime.of(2026, 8, 10, 11, 0))
            val deleted = createEvent(LocalDateTime.of(2026, 8, 11, 10, 0), LocalDateTime.of(2026, 8, 11, 11, 0))
            scheduleEventRepository.delete(deleted)
            entityManager.flush()
            entityManager.clear()

            val result = scheduleEventRepository.findAllIncludingDeleted()

            assertEquals(setOf(active.id, deleted.id), result.map { it.id }.toSet())
        }

        @Test
        fun findAllIncludingDeleted_shouldOrderById() {
            val first = createEvent(LocalDateTime.of(2026, 8, 10, 10, 0), LocalDateTime.of(2026, 8, 10, 11, 0))
            val second = createEvent(LocalDateTime.of(2026, 8, 11, 10, 0), LocalDateTime.of(2026, 8, 11, 11, 0))

            val result = scheduleEventRepository.findAllIncludingDeleted()

            assertEquals(listOf(first.id, second.id), result.map { it.id })
        }
    }

    @Nested
    @DisplayName("Soft Delete")
    inner class SoftDelete {

        @Test
        fun delete_whenCalled_shouldExcludeFromFindById() {
            val event = createEvent(LocalDateTime.of(2026, 8, 10, 10, 0), LocalDateTime.of(2026, 8, 10, 11, 0))

            scheduleEventRepository.delete(event)
            entityManager.flush()
            entityManager.clear()

            assertFalse(scheduleEventRepository.findById(event.id).isPresent)
        }
    }

    private fun createEvent(startAt: LocalDateTime, endAt: LocalDateTime): ScheduleEvent {
        return scheduleEventRepository.save(
            ScheduleEvent(
                title = "일정",
                category = ScheduleCategory.SHARED,
                startAt = startAt,
                endAt = endAt,
                allDay = false
            )
        )
    }
}
