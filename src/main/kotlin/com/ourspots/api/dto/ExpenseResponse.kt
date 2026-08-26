package com.ourspots.api.dto

import com.ourspots.domain.expense.entity.ExpenseCategory
import com.ourspots.domain.expense.entity.ExpenseRecord
import com.ourspots.domain.expense.entity.PaymentMethod
import java.time.LocalDate
import java.time.LocalDateTime

// 프론트 로컬 캐시 검증용 — count/lastModified 조합으로 전체 목록을 안 내려받고도 변경 여부만 가볍게 확인
data class ExpenseMetaResponse(
    val count: Long,
    val lastModified: LocalDateTime?
)

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
