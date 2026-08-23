package com.ourspots.batch

import com.ourspots.domain.expense.entity.ExpenseCategory
import com.ourspots.domain.expense.entity.PaymentMethod
import java.time.LocalDate

/**
 * 생활비 엑셀 한 행을 검증/파싱하는 순수 로직만 모아둔 객체 — DB/Spring 의존이 없어 단위 테스트가 가능하다.
 * ImportExpensesRunner는 엑셀을 Map<String,String> 행으로 변환한 뒤 이 객체에 검증/파싱을 위임한다.
 */
object ExpenseRowParser {

    val PAYMENT_METHOD_LABELS = mapOf(
        "와우카드" to PaymentMethod.WOW_CARD,
        "국민카드" to PaymentMethod.KB_CARD,
        "우리카드" to PaymentMethod.WOORI_CARD,
        "현대카드" to PaymentMethod.HYUNDAI_CARD,
        "초영결제" to PaymentMethod.CHOYOUNG_PAYMENT,
        "기타" to PaymentMethod.OTHER
    )

    val CATEGORY_LABELS = mapOf(
        "식비" to ExpenseCategory.FOOD,
        "생활비" to ExpenseCategory.LIVING,
        "비정기 지출" to ExpenseCategory.IRREGULAR,
        "비정기지출" to ExpenseCategory.IRREGULAR
    )

    data class ParsedRow(
        val expenseDate: LocalDate,
        val paymentMethod: PaymentMethod,
        val category: ExpenseCategory,
        val merchant: String,
        val amount: Long
    )

    sealed class RowResult {
        data class Valid(val row: ParsedRow) : RowResult()
        data class Invalid(val errors: List<String>) : RowResult()
    }

    fun parseRow(row: Map<String, String>): RowResult {
        val paymentMethodLabel = row["카드사"]?.trim().orEmpty()
        val categoryLabel = row["구분"]?.trim().orEmpty()
        val merchant = row["사용처"]?.trim().orEmpty()
        val amountRaw = row["금액"]?.trim().orEmpty()
        val dateRaw = row["지출일자"]?.trim().orEmpty()

        val paymentMethod = PAYMENT_METHOD_LABELS[paymentMethodLabel]
        val category = CATEGORY_LABELS[categoryLabel]
        val amount = parseAmount(amountRaw)
        val expenseDate = parseDate(dateRaw)

        val errors = buildList {
            if (paymentMethod == null) add("카드사 값을 알 수 없음('$paymentMethodLabel')")
            if (category == null) add("구분 값을 알 수 없음('$categoryLabel')")
            if (merchant.isBlank()) add("사용처 없음")
            if (amount == null || amount <= 0) add("금액이 올바르지 않음('$amountRaw')")
            if (expenseDate == null) add("지출일자가 올바르지 않음('$dateRaw')")
        }

        return if (errors.isNotEmpty()) {
            RowResult.Invalid(errors)
        } else {
            RowResult.Valid(ParsedRow(expenseDate!!, paymentMethod!!, category!!, merchant, amount!!))
        }
    }

    fun parseAmount(raw: String): Long? = raw.replace(",", "").toLongOrNull()

    // 엑셀 날짜 서식(DateUtil)으로 이미 yyyy-MM-dd 문자열이 되어 오거나, 사람이 직접
    // "2026-08-25"/"2026.08.25"/"2026.8.25"처럼 입력한 경우(월/일 앞자리 0 생략 포함)도 모두 지원
    fun parseDate(raw: String): LocalDate? {
        if (raw.isBlank()) return null
        val normalized = raw.replace(".", "-").replace("/", "-").trim().trimEnd('-')
        val parts = normalized.split("-")
        if (parts.size != 3) return null
        val (year, month, day) = parts
        val padded = "$year-${month.padStart(2, '0')}-${day.padStart(2, '0')}"
        return try {
            LocalDate.parse(padded)
        } catch (e: Exception) {
            null
        }
    }
}
