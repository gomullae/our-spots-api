package com.ourspots.domain.expense.service

import com.ourspots.api.dto.ExpenseRecordRequest
import com.ourspots.api.dto.ExpenseRecordResponse
import com.ourspots.common.exception.NotFoundException
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.domain.expense.entity.ExpenseCategory
import com.ourspots.domain.expense.entity.ExpenseRecord
import com.ourspots.domain.expense.repository.ExpenseRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Service
@Transactional(readOnly = true)
class ExpenseService(
    private val expenseRecordRepository: ExpenseRecordRepository,
    private val telegramNotificationService: TelegramNotificationService
) {
    companion object {
        private val WEEK_LABEL_FORMAT = DateTimeFormatter.ofPattern("M/d")
        private const val TOP_ITEMS_COUNT = 3
    }

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

    fun sendWeeklySummary(startDate: LocalDate, endDate: LocalDate, budget: Long) {
        val records = expenseRecordRepository.findByExpenseDateBetween(startDate, endDate, false)
        val foodRecords = records.filter { it.category == ExpenseCategory.FOOD }
        val livingRecords = records.filter { it.category == ExpenseCategory.LIVING }
        val irregularRecords = records.filter { it.category == ExpenseCategory.IRREGULAR }

        telegramNotificationService.notifyWeeklyExpenseSummary(
            weekLabel = "${startDate.format(WEEK_LABEL_FORMAT)}~${endDate.format(WEEK_LABEL_FORMAT)}",
            budget = budget,
            foodTotal = foodRecords.sumOf { it.amount },
            foodTop3 = topItems(foodRecords),
            livingTotal = livingRecords.sumOf { it.amount },
            livingTop3 = topItems(livingRecords),
            irregularTotal = irregularRecords.sumOf { it.amount },
            irregularItems = irregularRecords.sortedByDescending { it.amount }.map { it.merchant to it.amount }
        )
    }

    private fun topItems(records: List<ExpenseRecord>): List<Pair<String, Long>> =
        records.sortedByDescending { it.amount }.take(TOP_ITEMS_COUNT).map { it.merchant to it.amount }
}
