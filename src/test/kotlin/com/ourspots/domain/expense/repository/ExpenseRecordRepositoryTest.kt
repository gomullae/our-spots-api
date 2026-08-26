package com.ourspots.domain.expense.repository

import com.ourspots.domain.expense.entity.ExpenseCategory
import com.ourspots.domain.expense.entity.ExpenseRecord
import com.ourspots.domain.expense.entity.PaymentMethod
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
import kotlin.test.assertTrue

@DataJpaTest
@ActiveProfiles("test")
class ExpenseRecordRepositoryTest {

    @Autowired
    private lateinit var expenseRecordRepository: ExpenseRecordRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @BeforeEach
    fun setUp() {
        expenseRecordRepository.deleteAll()
        entityManager.flush()
        entityManager.clear()
    }

    @Nested
    @DisplayName("findByExpenseDateBetween")
    inner class FindByExpenseDateBetween {

        @Test
        fun findByExpenseDateBetween_shouldReturnOnlyRecordsWithinRangeNewestFirst() {
            createRecord(LocalDate.of(2026, 7, 31))
            createRecord(LocalDate.of(2026, 8, 1))
            createRecord(LocalDate.of(2026, 8, 19))
            createRecord(LocalDate.of(2026, 9, 1))

            val result = expenseRecordRepository.findByExpenseDateBetween(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                false
            )

            assertEquals(
                listOf(LocalDate.of(2026, 8, 19), LocalDate.of(2026, 8, 1)),
                result.map { it.expenseDate }
            )
        }

        @Test
        fun findByExpenseDateBetween_whenIncludeDeletedFalse_shouldExcludeDeleted() {
            val record = createRecord(LocalDate.of(2026, 8, 19))
            expenseRecordRepository.delete(record)
            entityManager.flush()
            entityManager.clear()

            val result = expenseRecordRepository.findByExpenseDateBetween(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                false
            )

            assertTrue(result.isEmpty())
        }

        @Test
        fun findByExpenseDateBetween_whenIncludeDeletedTrue_shouldIncludeDeleted() {
            val record = createRecord(LocalDate.of(2026, 8, 19))
            expenseRecordRepository.delete(record)
            entityManager.flush()
            entityManager.clear()

            val result = expenseRecordRepository.findByExpenseDateBetween(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                true
            )

            assertEquals(1, result.size)
            assertEquals(record.id, result[0].id)
        }
    }

    @Nested
    @DisplayName("findMaxUpdatedAt")
    inner class FindMaxUpdatedAt {

        @Test
        fun findMaxUpdatedAt_whenNoRecords_shouldReturnNull() {
            val result = expenseRecordRepository.findMaxUpdatedAt()

            assertNull(result)
        }

        @Test
        fun findMaxUpdatedAt_shouldReturnLatestUpdatedAtAcrossRecords() {
            val first = createRecord(LocalDate.of(2026, 8, 1))
            createRecord(LocalDate.of(2026, 8, 19))
            entityManager.clear()

            val toUpdate = expenseRecordRepository.findById(first.id).get()
            toUpdate.amount = 20000
            expenseRecordRepository.save(toUpdate)
            entityManager.flush()
            entityManager.clear()

            val result = expenseRecordRepository.findMaxUpdatedAt()

            assertEquals(toUpdate.updatedAt, result)
        }

        @Test
        fun findMaxUpdatedAt_shouldIgnoreSoftDeletedRecords() {
            val record = createRecord(LocalDate.of(2026, 8, 19))
            expenseRecordRepository.delete(record)
            entityManager.flush()
            entityManager.clear()

            val result = expenseRecordRepository.findMaxUpdatedAt()

            assertNull(result)
        }
    }

    @Nested
    @DisplayName("Soft Delete")
    inner class SoftDelete {

        @Test
        fun delete_whenCalled_shouldExcludeFromFindById() {
            val record = createRecord(LocalDate.of(2026, 8, 19))

            expenseRecordRepository.delete(record)
            entityManager.flush()
            entityManager.clear()

            assertFalse(expenseRecordRepository.findById(record.id).isPresent)
        }
    }

    @Nested
    @DisplayName("findByIdIncludingDeleted")
    inner class FindByIdIncludingDeleted {

        @Test
        fun findByIdIncludingDeleted_whenSoftDeleted_shouldStillReturnRecord() {
            val record = createRecord(LocalDate.of(2026, 8, 19))
            expenseRecordRepository.delete(record)
            entityManager.flush()
            entityManager.clear()

            val result = expenseRecordRepository.findByIdIncludingDeleted(record.id)

            assertEquals(record.id, result?.id)
        }

        @Test
        fun findByIdIncludingDeleted_whenNotExists_shouldReturnNull() {
            val result = expenseRecordRepository.findByIdIncludingDeleted(99999L)

            assertEquals(null, result)
        }
    }

    private fun createRecord(date: LocalDate): ExpenseRecord {
        return expenseRecordRepository.save(
            ExpenseRecord(
                expenseDate = date,
                paymentMethod = PaymentMethod.WOORI_CARD,
                category = ExpenseCategory.FOOD,
                merchant = "이마트",
                amount = 10000
            )
        )
    }
}
