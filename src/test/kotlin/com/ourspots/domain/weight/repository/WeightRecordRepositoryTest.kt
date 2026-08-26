package com.ourspots.domain.weight.repository

import com.ourspots.domain.weight.entity.WeightRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

@DataJpaTest
@ActiveProfiles("test")
class WeightRecordRepositoryTest {

    @Autowired
    private lateinit var weightRecordRepository: WeightRecordRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @BeforeEach
    fun setUp() {
        weightRecordRepository.deleteAll()
        entityManager.flush()
        entityManager.clear()
    }

    @Nested
    @DisplayName("findByRecordedDate")
    inner class FindByRecordedDate {

        @Test
        fun findByRecordedDate_whenExists_shouldReturnRecord() {
            createRecord(LocalDate.of(2026, 8, 19), 70.5)

            val result = weightRecordRepository.findByRecordedDate(LocalDate.of(2026, 8, 19))

            assertEquals(70.5, result?.weightKg)
        }

        @Test
        fun findByRecordedDate_whenNotExists_shouldReturnNull() {
            val result = weightRecordRepository.findByRecordedDate(LocalDate.of(2026, 8, 19))

            assertNull(result)
        }
    }

    @Nested
    @DisplayName("findAllByOrderByRecordedDateDesc")
    inner class FindAllOrdered {

        @Test
        fun findAllByOrderByRecordedDateDesc_shouldReturnNewestFirst() {
            createRecord(LocalDate.of(2026, 8, 1), 70.0)
            createRecord(LocalDate.of(2026, 8, 19), 71.0)
            createRecord(LocalDate.of(2026, 7, 15), 69.0)

            val result = weightRecordRepository.findAllByOrderByRecordedDateDesc()

            assertEquals(listOf(
                LocalDate.of(2026, 8, 19),
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 7, 15)
            ), result.map { it.recordedDate })
        }
    }

    @Nested
    @DisplayName("findMaxUpdatedAt")
    inner class FindMaxUpdatedAt {

        @Test
        fun findMaxUpdatedAt_whenNoRecords_shouldReturnNull() {
            val result = weightRecordRepository.findMaxUpdatedAt()

            assertNull(result)
        }

        @Test
        fun findMaxUpdatedAt_shouldReturnLatestUpdatedAtAcrossRecords() {
            val first = createRecord(LocalDate.of(2026, 8, 1), 70.0)
            createRecord(LocalDate.of(2026, 8, 19), 71.0)
            entityManager.clear()

            val toUpdate = weightRecordRepository.findById(first.id).get()
            toUpdate.weightKg = 72.0
            weightRecordRepository.save(toUpdate)
            entityManager.flush()
            entityManager.clear()

            val result = weightRecordRepository.findMaxUpdatedAt()

            assertEquals(toUpdate.updatedAt, result)
        }

        @Test
        fun findMaxUpdatedAt_shouldIgnoreSoftDeletedRecords() {
            val record = createRecord(LocalDate.of(2026, 8, 19), 70.0)
            weightRecordRepository.delete(record)
            entityManager.flush()
            entityManager.clear()

            val result = weightRecordRepository.findMaxUpdatedAt()

            assertNull(result)
        }
    }

    @Nested
    @DisplayName("Soft Delete")
    inner class SoftDelete {

        @Test
        fun delete_whenCalled_shouldExcludeFromFindById() {
            val record = createRecord(LocalDate.of(2026, 8, 19), 70.0)

            weightRecordRepository.delete(record)
            entityManager.flush()
            entityManager.clear()

            val result = weightRecordRepository.findById(record.id)
            assertFalse(result.isPresent)
        }

        @Test
        fun delete_whenCalled_shouldExcludeFromFindAll() {
            val record1 = createRecord(LocalDate.of(2026, 8, 19), 70.0)
            createRecord(LocalDate.of(2026, 8, 18), 71.0)

            weightRecordRepository.delete(record1)
            entityManager.flush()
            entityManager.clear()

            val result = weightRecordRepository.findAllByOrderByRecordedDateDesc()
            assertEquals(1, result.size)
            assertEquals(LocalDate.of(2026, 8, 18), result[0].recordedDate)
        }

        @Test
        fun delete_whenCalled_shouldExcludeFromFindByRecordedDate() {
            val record = createRecord(LocalDate.of(2026, 8, 19), 70.0)

            weightRecordRepository.delete(record)
            entityManager.flush()
            entityManager.clear()

            val result = weightRecordRepository.findByRecordedDate(LocalDate.of(2026, 8, 19))
            assertNull(result)
        }
    }

    private fun createRecord(date: LocalDate, weightKg: Double): WeightRecord {
        return weightRecordRepository.save(WeightRecord(recordedDate = date, weightKg = weightKg))
    }
}
