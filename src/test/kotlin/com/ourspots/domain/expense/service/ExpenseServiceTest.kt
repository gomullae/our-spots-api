package com.ourspots.domain.expense.service

import com.ourspots.api.dto.ExpenseRecordRequest
import com.ourspots.common.exception.NotFoundException
import com.ourspots.common.notification.CategorySpend
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.domain.expense.entity.ExpenseCategory
import com.ourspots.domain.expense.entity.ExpenseRecord
import com.ourspots.domain.expense.entity.PaymentMethod
import com.ourspots.domain.expense.repository.ExpenseRecordRepository
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
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals

class ExpenseServiceTest {

    @MockK
    private lateinit var expenseRecordRepository: ExpenseRecordRepository

    @MockK(relaxed = true)
    private lateinit var telegramNotificationService: TelegramNotificationService

    @InjectMockKs
    private lateinit var expenseService: ExpenseService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    private fun createRecord(
        id: Long,
        date: LocalDate,
        paymentMethod: PaymentMethod = PaymentMethod.WOORI_CARD,
        category: ExpenseCategory = ExpenseCategory.FOOD,
        merchant: String = "이마트",
        amount: Long = 30000
    ) = ExpenseRecord(
        id = id,
        expenseDate = date,
        paymentMethod = paymentMethod,
        category = category,
        merchant = merchant,
        amount = amount
    )

    @Nested
    @DisplayName("getMeta")
    inner class GetMeta {

        @Test
        fun getMeta_shouldReturnCountAndLastModified() {
            val lastModified = LocalDateTime.of(2026, 8, 20, 9, 0)
            every { expenseRecordRepository.count() } returns 3L
            every { expenseRecordRepository.findMaxUpdatedAt() } returns lastModified

            val result = expenseService.getMeta()

            assertEquals(3L, result.count)
            assertEquals(lastModified, result.lastModified)
        }

        @Test
        fun getMeta_whenNoRecords_shouldReturnZeroCountAndNullLastModified() {
            every { expenseRecordRepository.count() } returns 0L
            every { expenseRecordRepository.findMaxUpdatedAt() } returns null

            val result = expenseService.getMeta()

            assertEquals(0L, result.count)
            assertEquals(null, result.lastModified)
        }
    }

    @Nested
    @DisplayName("getRecords")
    inner class GetRecords {

        @Test
        fun getRecords_shouldReturnRecordsWithinRange() {
            val start = LocalDate.of(2026, 8, 1)
            val end = LocalDate.of(2026, 8, 31)
            val records = listOf(
                createRecord(2L, LocalDate.of(2026, 8, 19)),
                createRecord(1L, LocalDate.of(2026, 8, 1))
            )
            every { expenseRecordRepository.findByExpenseDateBetween(start, end, false) } returns records

            val result = expenseService.getRecords(start, end)

            assertEquals(2, result.size)
            assertEquals(LocalDate.of(2026, 8, 19), result[0].expenseDate)
        }

        @Test
        fun getRecords_whenIncludeDeletedTrue_shouldPassThroughToRepository() {
            val start = LocalDate.of(2026, 8, 1)
            val end = LocalDate.of(2026, 8, 31)
            every { expenseRecordRepository.findByExpenseDateBetween(start, end, true) } returns emptyList()

            expenseService.getRecords(start, end, includeDeleted = true)

            verify { expenseRecordRepository.findByExpenseDateBetween(start, end, true) }
        }
    }

    @Nested
    @DisplayName("createRecord")
    inner class CreateRecord {

        @Test
        fun createRecord_shouldSaveAndReturnRecord() {
            val request = ExpenseRecordRequest(
                expenseDate = LocalDate.of(2026, 8, 19),
                paymentMethod = PaymentMethod.HYUNDAI_CARD,
                category = ExpenseCategory.LIVING,
                merchant = "다이소",
                amount = 15000
            )
            every { expenseRecordRepository.save(any<ExpenseRecord>()) } answers { firstArg() }

            val result = expenseService.createRecord(request)

            assertEquals("다이소", result.merchant)
            assertEquals(15000, result.amount)
            assertEquals(PaymentMethod.HYUNDAI_CARD, result.paymentMethod)
            assertEquals(ExpenseCategory.LIVING, result.category)
        }
    }

    @Nested
    @DisplayName("updateRecord")
    inner class UpdateRecord {

        @Test
        fun updateRecord_whenExists_shouldUpdateInPlace() {
            val existing = createRecord(1L, LocalDate.of(2026, 8, 19))
            val request = ExpenseRecordRequest(
                expenseDate = LocalDate.of(2026, 8, 20),
                paymentMethod = PaymentMethod.KB_CARD,
                category = ExpenseCategory.IRREGULAR,
                merchant = "병원",
                amount = 50000
            )
            every { expenseRecordRepository.findById(1L) } returns Optional.of(existing)
            every { expenseRecordRepository.save(any<ExpenseRecord>()) } answers { firstArg() }

            val result = expenseService.updateRecord(1L, request)

            assertEquals(1L, result.id)
            assertEquals(LocalDate.of(2026, 8, 20), result.expenseDate)
            assertEquals(PaymentMethod.KB_CARD, result.paymentMethod)
            assertEquals(ExpenseCategory.IRREGULAR, result.category)
            assertEquals("병원", result.merchant)
            assertEquals(50000, result.amount)
        }

        @Test
        fun updateRecord_whenNotFound_shouldThrowNotFoundException() {
            val request = ExpenseRecordRequest(
                expenseDate = LocalDate.now(),
                paymentMethod = PaymentMethod.OTHER,
                category = ExpenseCategory.FOOD,
                merchant = "매장",
                amount = 1000
            )
            every { expenseRecordRepository.findById(99L) } returns Optional.empty()

            assertThrows<NotFoundException> {
                expenseService.updateRecord(99L, request)
            }
        }
    }

    @Nested
    @DisplayName("deleteRecord")
    inner class DeleteRecord {

        @Test
        fun deleteRecord_whenExists_shouldDelete() {
            val record = createRecord(1L, LocalDate.now())
            every { expenseRecordRepository.findById(1L) } returns Optional.of(record)
            every { expenseRecordRepository.delete(record) } just Runs

            expenseService.deleteRecord(1L)

            verify { expenseRecordRepository.delete(record) }
        }

        @Test
        fun deleteRecord_whenNotFound_shouldThrowNotFoundException() {
            every { expenseRecordRepository.findById(99L) } returns Optional.empty()

            assertThrows<NotFoundException> {
                expenseService.deleteRecord(99L)
            }
        }
    }

    @Nested
    @DisplayName("restoreRecord")
    inner class RestoreRecord {

        @Test
        fun restoreRecord_whenExists_shouldClearDeletedAt() {
            val record = createRecord(1L, LocalDate.now())
            record.deletedAt = java.time.LocalDateTime.now()
            every { expenseRecordRepository.findByIdIncludingDeleted(1L) } returns record
            every { expenseRecordRepository.save(any<ExpenseRecord>()) } answers { firstArg() }

            val result = expenseService.restoreRecord(1L)

            assertEquals(1L, result.id)
            assertEquals(null, result.deletedAt)
        }

        @Test
        fun restoreRecord_whenNotFound_shouldThrowNotFoundException() {
            every { expenseRecordRepository.findByIdIncludingDeleted(99L) } returns null

            assertThrows<NotFoundException> {
                expenseService.restoreRecord(99L)
            }
        }
    }

    @Nested
    @DisplayName("sendWeeklySummary")
    inner class SendWeeklySummary {

        @Test
        fun sendWeeklySummary_shouldMergeFoodAndLivingAndCapTop5ByAmount() {
            val start = LocalDate.of(2026, 8, 17)
            val end = LocalDate.of(2026, 8, 23)
            val records = listOf(
                createRecord(1L, start, category = ExpenseCategory.FOOD, merchant = "이마트", amount = 45000),
                createRecord(2L, start, category = ExpenseCategory.FOOD, merchant = "배달의민족", amount = 32000),
                createRecord(3L, start, category = ExpenseCategory.FOOD, merchant = "스타벅스", amount = 18000, paymentMethod = PaymentMethod.CHOYOUNG_PAYMENT),
                createRecord(4L, start, category = ExpenseCategory.FOOD, merchant = "편의점", amount = 5000),
                createRecord(5L, start, category = ExpenseCategory.LIVING, merchant = "다이소", amount = 80000),
                createRecord(6L, start, category = ExpenseCategory.LIVING, merchant = "올리브영", amount = 20000, paymentMethod = PaymentMethod.CHOYOUNG_PAYMENT),
                createRecord(7L, start, category = ExpenseCategory.IRREGULAR, merchant = "병원", amount = 50000)
            )
            every { expenseRecordRepository.findByExpenseDateBetween(start, end, false) } returns records

            expenseService.sendWeeklySummary(start, end, budget = 500000)

            verify {
                telegramNotificationService.notifyWeeklyExpenseSummary(
                    weekLabel = "8/17~8/23",
                    budget = 500000,
                    // 스타벅스(식비 18,000)는 초영결제라 식비 진우결제=82,000/초영결제=18,000
                    foodSpend = CategorySpend(total = 100000, jinwooTotal = 82000, choyoungTotal = 18000),
                    // 올리브영(생활비 20,000)은 초영결제라 생활비 진우결제=80,000/초영결제=20,000
                    livingSpend = CategorySpend(total = 100000, jinwooTotal = 80000, choyoungTotal = 20000),
                    // 식비/생활비 통합, 금액 큰 순 상위 5개 (편의점 5,000원은 6번째라 제외)
                    topItems = listOf(
                        Triple("생활비", "다이소", 80000L),
                        Triple("식비", "이마트", 45000L),
                        Triple("식비", "배달의민족", 32000L),
                        Triple("생활비", "올리브영", 20000L),
                        Triple("식비", "스타벅스", 18000L)
                    ),
                    irregularTotal = 50000,
                    irregularItems = listOf("병원" to 50000L)
                )
            }
        }
    }
}
