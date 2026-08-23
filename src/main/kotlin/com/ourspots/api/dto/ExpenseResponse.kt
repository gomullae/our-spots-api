package com.ourspots.api.dto

import com.ourspots.domain.expense.entity.ExpenseCategory
import com.ourspots.domain.expense.entity.ExpenseRecord
import com.ourspots.domain.expense.entity.PaymentMethod
import java.time.LocalDate
import java.time.LocalDateTime

data class ExpenseRecordResponse(
    val id: Long,
    val expenseDate: LocalDate,
    val paymentMethod: PaymentMethod,
    val category: ExpenseCategory,
    val merchant: String,
    val amount: Long,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?
) {
    companion object {
        fun from(record: ExpenseRecord) = ExpenseRecordResponse(
            id = record.id,
            expenseDate = record.expenseDate,
            paymentMethod = record.paymentMethod,
            category = record.category,
            merchant = record.merchant,
            amount = record.amount,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt,
            deletedAt = record.deletedAt
        )
    }
}
