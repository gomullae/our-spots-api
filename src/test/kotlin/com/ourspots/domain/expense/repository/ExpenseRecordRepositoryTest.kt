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
                false,
                null
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
                false,
                null
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
                true,
                null
            )

            assertEquals(1, result.size)
            assertEquals(record.id, result[0].id)
        }

        @Test
        fun findByExpenseDateBetween_whenKeywordMatchesMerchant_shouldReturnOnlyMatching() {
            createRecord(LocalDate.of(2026, 8, 10), merchant = "이마트 용산점")
            createRecord(LocalDate.of(2026, 8, 11), merchant = "스타벅스")

            val result = expenseRecordRepository.findByExpenseDateBetween(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                false,
                "이마트"
            )

            assertEquals(1, result.size)
            assertEquals("이마트 용산점", result[0].merchant)
        }

        @Test
        fun findByExpenseDateBetween_whenKeywordIsCaseInsensitive_shouldMatch() {
            createRecord(LocalDate.of(2026, 8, 10), merchant = "Coupang")

            val result = expenseRecordRepository.findByExpenseDateBetween(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                false,
                "coupang"
            )

            assertEquals(1, result.size)
        }

        @Test
        fun findByExpenseDateBetween_whenKeywordContainsUnderscore_shouldTreatAsLiteral() {
            // 이스케이프 없이 그대로 넘기면 '_'가 와일드카드(임의의 한 글자)로 해석돼 "이마트"도 매치되는 버그가
            // 있었음 — 호출부(ExpenseService)가 이스케이프한다는 전제이므로, 여기선 이미 이스케이프된 값을 직접 넘겨서 검증
            createRecord(LocalDate.of(2026, 8, 10), merchant = "이_마트")
            createRecord(LocalDate.of(2026, 8, 11), merchant = "이마트")

            val result = expenseRecordRepository.findByExpenseDateBetween(
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                false,
                "이\\_마트"
            )

            assertEquals(1, result.size)
            assertEquals("이_마트", result[0].merchant)
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

    private fun createRecord(date: LocalDate, merchant: String = "이마트"): ExpenseRecord {
        return expenseRecordRepository.save(
            ExpenseRecord(
                expenseDate = date,
                paymentMethod = PaymentMethod.WOORI_CARD,
                category = ExpenseCategory.FOOD,
                merchant = merchant,
                amount = 10000
            )
        )
    }
}
