package com.ourspots.domain.weight.service

import com.ourspots.api.dto.WeightRecordUpsertRequest
import com.ourspots.common.exception.NotFoundException
import com.ourspots.domain.weight.entity.WeightRecord
import com.ourspots.domain.weight.repository.WeightRecordRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import java.util.*
import kotlin.test.assertEquals

class WeightServiceTest {

    @MockK
    private lateinit var weightRecordRepository: WeightRecordRepository

    @InjectMockKs
    private lateinit var weightService: WeightService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    private fun createRecord(id: Long, date: LocalDate, weightKg: Double) = WeightRecord(
        id = id,
        recordedDate = date,
        weightKg = weightKg
    )

    @Nested
    @DisplayName("getMeta")
    inner class GetMeta {

        @Test
        fun getMeta_shouldReturnCountAndLastModified() {
            val lastModified = java.time.LocalDateTime.of(2026, 8, 20, 9, 0)
            every { weightRecordRepository.count() } returns 3L
            every { weightRecordRepository.findMaxUpdatedAt() } returns lastModified

            val result = weightService.getMeta()

            assertEquals(3L, result.count)
            assertEquals(lastModified, result.lastModified)
        }

        @Test
        fun getMeta_whenNoRecords_shouldReturnZeroCountAndNullLastModified() {
            every { weightRecordRepository.count() } returns 0L
            every { weightRecordRepository.findMaxUpdatedAt() } returns null

            val result = weightService.getMeta()

            assertEquals(0L, result.count)
            assertEquals(null, result.lastModified)
        }
    }

    @Nested
    @DisplayName("getAllRecords")
    inner class GetAllRecords {

        @Test
        fun getAllRecords_shouldReturnRecordsOrderedByDateDesc() {
            val records = listOf(
                createRecord(2L, LocalDate.of(2026, 8, 19), 70.9),
                createRecord(1L, LocalDate.of(2026, 8, 18), 71.2)
            )
            every { weightRecordRepository.findAllByOrderByRecordedDateDesc() } returns records

            val result = weightService.getAllRecords()

            assertEquals(2, result.size)
            assertEquals(70.9, result[0].weightKg)
        }
    }

    @Nested
    @DisplayName("upsertRecord")
    inner class UpsertRecord {

        @Test
        fun upsertRecord_whenNoExistingRecordForDate_shouldCreateNew() {
            val date = LocalDate.of(2026, 8, 19)
            val request = WeightRecordUpsertRequest(recordedDate = date, weightKg = 71.0)
            every { weightRecordRepository.findByRecordedDate(date) } returns null
            every { weightRecordRepository.save(any<WeightRecord>()) } answers { firstArg() }

            weightService.upsertRecord(request)

            verify { weightRecordRepository.save(match { it.recordedDate == date && it.weightKg == 71.0 }) }
        }

        @Test
        fun upsertRecord_whenExistingRecordForDate_shouldUpdateInPlace() {
            val date = LocalDate.of(2026, 8, 19)
            val existing = createRecord(1L, date, 70.0)
            val request = WeightRecordUpsertRequest(recordedDate = date, weightKg = 71.5, memo = "많이 먹음")
            every { weightRecordRepository.findByRecordedDate(date) } returns existing
            every { weightRecordRepository.save(any<WeightRecord>()) } answers { firstArg() }

            val result = weightService.upsertRecord(request)

            assertEquals(1L, result.id)
            assertEquals(71.5, result.weightKg)
            assertEquals("많이 먹음", result.memo)
        }

        @Test
        fun upsertRecord_whenWeightHasMoreThanOneDecimal_shouldRoundToOneDecimal() {
            val date = LocalDate.of(2026, 8, 19)
            val request = WeightRecordUpsertRequest(recordedDate = date, weightKg = 71.83)
            every { weightRecordRepository.findByRecordedDate(date) } returns null
            every { weightRecordRepository.save(any<WeightRecord>()) } answers { firstArg() }

            val result = weightService.upsertRecord(request)

            assertEquals(71.8, result.weightKg)
        }

        @Test
        fun upsertRecord_whenDateOmitted_shouldDefaultToToday() {
            val request = WeightRecordUpsertRequest(weightKg = 70.0)
            every { weightRecordRepository.findByRecordedDate(LocalDate.now()) } returns null
            every { weightRecordRepository.save(any<WeightRecord>()) } answers { firstArg() }

            val result = weightService.upsertRecord(request)

            assertEquals(LocalDate.now(), result.recordedDate)
        }
    }

    @Nested
    @DisplayName("deleteRecord")
    inner class DeleteRecord {

        @Test
        fun deleteRecord_whenExists_shouldDelete() {
            val record = createRecord(1L, LocalDate.now(), 70.0)
            every { weightRecordRepository.findById(1L) } returns Optional.of(record)
            every { weightRecordRepository.delete(record) } just Runs

            weightService.deleteRecord(1L)

            verify { weightRecordRepository.delete(record) }
        }

        @Test
        fun deleteRecord_whenNotFound_shouldThrowNotFoundException() {
            every { weightRecordRepository.findById(99L) } returns Optional.empty()

            assertThrows<NotFoundException> {
                weightService.deleteRecord(99L)
            }
        }
    }
}
