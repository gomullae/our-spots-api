package com.ourspots.api.dto

import com.ourspots.domain.expense.entity.ExpenseCategory
import com.ourspots.domain.expense.entity.PaymentMethod
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class ExpenseRecordRequest(
    @field:NotNull
    @field:PastOrPresent
    val expenseDate: LocalDate,

    @field:NotNull
    val paymentMethod: PaymentMethod,

    @field:NotNull
    val category: ExpenseCategory,

    @field:NotBlank
    @field:Size(max = 100)
    val merchant: String,

    // 결제수단 무관하게 음수 등록 허용 — 거래취소/환불을 별도 지원금(SUBSIDY) 없이 그대로 기록할 수 있게 함. 0만 막음
    @field:NotNull
    val amount: Long
) {
    @get:AssertTrue(message = "금액은 0이 될 수 없습니다")
    val isAmountNonZero: Boolean
        get() = amount != 0L
}
