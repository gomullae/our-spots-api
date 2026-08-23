package com.ourspots.batch

import com.ourspots.domain.expense.entity.ExpenseCategory
import com.ourspots.domain.expense.entity.PaymentMethod
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ExpenseRowParserTest {

    private fun validRow(overrides: Map<String, String> = emptyMap()): Map<String, String> {
        val base = mapOf(
            "카드사" to "현대카드",
            "구분" to "식비",
            "사용처" to "이마트",
            "금액" to "35000",
            "지출일자" to "2026-08-19"
        )
        return base + overrides
    }

    @Nested
    @DisplayName("parseRow")
    inner class ParseRow {

        @Test
        fun parseRow_whenAllFieldsValid_shouldReturnValid() {
            val result = ExpenseRowParser.parseRow(validRow())

            assertTrue(result is ExpenseRowParser.RowResult.Valid)
            val row = (result as ExpenseRowParser.RowResult.Valid).row
            assertEquals(PaymentMethod.HYUNDAI_CARD, row.paymentMethod)
            assertEquals(ExpenseCategory.FOOD, row.category)
            assertEquals("이마트", row.merchant)
            assertEquals(35000L, row.amount)
            assertEquals(LocalDate.of(2026, 8, 19), row.expenseDate)
        }

        @Test
        fun parseRow_whenPaymentMethodUnknown_shouldReturnInvalidWithReason() {
            val result = ExpenseRowParser.parseRow(validRow(mapOf("카드사" to "신한카드")))

            assertTrue(result is ExpenseRowParser.RowResult.Invalid)
            val errors = (result as ExpenseRowParser.RowResult.Invalid).errors
            assertTrue(errors.any { it.contains("카드사") })
        }

        @Test
        fun parseRow_whenCategoryUnknown_shouldReturnInvalidWithReason() {
            val result = ExpenseRowParser.parseRow(validRow(mapOf("구분" to "여행비")))

            assertTrue(result is ExpenseRowParser.RowResult.Invalid)
            val errors = (result as ExpenseRowParser.RowResult.Invalid).errors
            assertTrue(errors.any { it.contains("구분") })
        }

        @Test
        fun parseRow_whenMerchantBlank_shouldReturnInvalidWithReason() {
            val result = ExpenseRowParser.parseRow(validRow(mapOf("사용처" to "  ")))

            assertTrue(result is ExpenseRowParser.RowResult.Invalid)
            val errors = (result as ExpenseRowParser.RowResult.Invalid).errors
            assertTrue(errors.any { it.contains("사용처") })
        }

        @Test
        fun parseRow_whenAmountZero_shouldReturnInvalidWithReason() {
            val result = ExpenseRowParser.parseRow(validRow(mapOf("금액" to "0")))

            assertTrue(result is ExpenseRowParser.RowResult.Invalid)
            val errors = (result as ExpenseRowParser.RowResult.Invalid).errors
            assertTrue(errors.any { it.contains("금액") })
        }

        @Test
        fun parseRow_whenAmountNegative_shouldReturnInvalidWithReason() {
            val result = ExpenseRowParser.parseRow(validRow(mapOf("금액" to "-1000")))

            assertTrue(result is ExpenseRowParser.RowResult.Invalid)
        }

        @Test
        fun parseRow_whenAmountNotNumeric_shouldReturnInvalidWithReason() {
            val result = ExpenseRowParser.parseRow(validRow(mapOf("금액" to "삼만오천원")))

            assertTrue(result is ExpenseRowParser.RowResult.Invalid)
        }

        @Test
        fun parseRow_whenDateInvalid_shouldReturnInvalidWithReason() {
            val result = ExpenseRowParser.parseRow(validRow(mapOf("지출일자" to "2026년 8월 19일")))

            assertTrue(result is ExpenseRowParser.RowResult.Invalid)
            val errors = (result as ExpenseRowParser.RowResult.Invalid).errors
            assertTrue(errors.any { it.contains("지출일자") })
        }

        @Test
        fun parseRow_whenMultipleFieldsInvalid_shouldCollectAllErrors() {
            val result = ExpenseRowParser.parseRow(
                validRow(mapOf("카드사" to "신한카드", "금액" to "0", "사용처" to ""))
            )

            assertTrue(result is ExpenseRowParser.RowResult.Invalid)
            assertEquals(3, (result as ExpenseRowParser.RowResult.Invalid).errors.size)
        }

        @Test
        fun parseRow_whenIrregularCategoryWithoutSpace_shouldMapToIrregular() {
            val result = ExpenseRowParser.parseRow(validRow(mapOf("구분" to "비정기지출")))

            assertTrue(result is ExpenseRowParser.RowResult.Valid)
            assertEquals(ExpenseCategory.IRREGULAR, (result as ExpenseRowParser.RowResult.Valid).row.category)
        }

        @Test
        fun parseRow_whenIrregularCategoryWithSpace_shouldAlsoMapToIrregular() {
            val result = ExpenseRowParser.parseRow(validRow(mapOf("구분" to "비정기 지출")))

            assertTrue(result is ExpenseRowParser.RowResult.Valid)
            assertEquals(ExpenseCategory.IRREGULAR, (result as ExpenseRowParser.RowResult.Valid).row.category)
        }

        @Test
        fun parseRow_whenChoyoungPayment_shouldMapToChoyoungPayment() {
            val result = ExpenseRowParser.parseRow(validRow(mapOf("카드사" to "초영결제")))

            assertTrue(result is ExpenseRowParser.RowResult.Valid)
            assertEquals(PaymentMethod.CHOYOUNG_PAYMENT, (result as ExpenseRowParser.RowResult.Valid).row.paymentMethod)
        }
    }

    @Nested
    @DisplayName("parseAmount")
    inner class ParseAmount {

        @Test
        fun parseAmount_withPlainDigits_shouldParse() {
            assertEquals(35000L, ExpenseRowParser.parseAmount("35000"))
        }

        @Test
        fun parseAmount_withCommas_shouldStripAndParse() {
            assertEquals(1234567L, ExpenseRowParser.parseAmount("1,234,567"))
        }

        @Test
        fun parseAmount_whenBlank_shouldReturnNull() {
            assertNull(ExpenseRowParser.parseAmount(""))
        }

        @Test
        fun parseAmount_whenNonNumeric_shouldReturnNull() {
            assertNull(ExpenseRowParser.parseAmount("abc"))
        }
    }

    @Nested
    @DisplayName("parseDate")
    inner class ParseDate {

        @Test
        fun parseDate_withIsoHyphenFormat_shouldParse() {
            assertEquals(LocalDate.of(2026, 8, 19), ExpenseRowParser.parseDate("2026-08-19"))
        }

        @Test
        fun parseDate_withDotFormat_shouldParse() {
            assertEquals(LocalDate.of(2026, 8, 19), ExpenseRowParser.parseDate("2026.08.19"))
        }

        @Test
        fun parseDate_withTrailingDot_shouldParse() {
            assertEquals(LocalDate.of(2026, 8, 19), ExpenseRowParser.parseDate("2026.08.19."))
        }

        @Test
        fun parseDate_withSlashFormat_shouldParse() {
            assertEquals(LocalDate.of(2026, 8, 19), ExpenseRowParser.parseDate("2026/08/19"))
        }

        @Test
        fun parseDate_whenBlank_shouldReturnNull() {
            assertNull(ExpenseRowParser.parseDate(""))
        }

        @Test
        fun parseDate_whenUnparseable_shouldReturnNull() {
            assertNull(ExpenseRowParser.parseDate("2026년 8월 19일"))
        }

        @Test
        fun parseDate_whenMonthOrDayMissingLeadingZero_shouldStillParse() {
            assertEquals(LocalDate.of(2026, 8, 9), ExpenseRowParser.parseDate("2026-8-9"))
        }
    }
}
