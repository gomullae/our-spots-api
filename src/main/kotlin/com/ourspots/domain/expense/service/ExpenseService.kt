package com.ourspots.domain.expense.service

import com.ourspots.api.dto.ExpenseRecordRequest
import com.ourspots.api.dto.ExpenseRecordResponse
import com.ourspots.common.exception.NotFoundException
import com.ourspots.domain.expense.entity.ExpenseRecord
import com.ourspots.domain.expense.repository.ExpenseRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class ExpenseService(
    private val expenseRecordRepository: ExpenseRecordRepository
) {

    fun getRecords(startDate: LocalDate, endDate: LocalDate, includeDeleted: Boolean = false): List<ExpenseRecordResponse> =
        expenseRecordRepository.findByExpenseDateBetween(startDate, endDate, includeDeleted)
            .map { ExpenseRecordResponse.from(it) }

    @Transactional
    fun createRecord(request: ExpenseRecordRequest): ExpenseRecordResponse {
        val record = ExpenseRecord(
            expenseDate = request.expenseDate,
            paymentMethod = request.paymentMethod,
            category = request.category,
            merchant = request.merchant,
            amount = request.amount
        )
        return ExpenseRecordResponse.from(expenseRecordRepository.save(record))
    }

    @Transactional
    fun updateRecord(id: Long, request: ExpenseRecordRequest): ExpenseRecordResponse {
        val record = expenseRecordRepository.findById(id)
            .orElseThrow { NotFoundException("Expense record not found: $id") }

        record.expenseDate = request.expenseDate
        record.paymentMethod = request.paymentMethod
        record.category = request.category
        record.merchant = request.merchant
        record.amount = request.amount

        return ExpenseRecordResponse.from(expenseRecordRepository.save(record))
    }

    @Transactional
    fun deleteRecord(id: Long) {
        val record = expenseRecordRepository.findById(id)
            .orElseThrow { NotFoundException("Expense record not found: $id") }
        expenseRecordRepository.delete(record)
    }

    @Transactional
    fun restoreRecord(id: Long): ExpenseRecordResponse {
        val record = expenseRecordRepository.findByIdIncludingDeleted(id)
            ?: throw NotFoundException("Expense record not found: $id")
        record.deletedAt = null
        return ExpenseRecordResponse.from(expenseRecordRepository.save(record))
    }
}
