package com.ourspots.api.dto

import com.ourspots.domain.expense.entity.ExpenseCategory
import com.ourspots.domain.expense.entity.PaymentMethod
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Positive
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

    @field:NotNull
    @field:Positive
    val amount: Long
)
