package com.ourspots.domain.expense.service

import com.ourspots.api.dto.ExpenseRecordRequest
import com.ourspots.api.dto.ExpenseRecordResponse
import com.ourspots.common.exception.NotFoundException
import com.ourspots.common.notification.CategorySpend
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.domain.expense.entity.ExpenseCategory
import com.ourspots.domain.expense.entity.ExpenseRecord
import com.ourspots.domain.expense.entity.PaymentMethod
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
        // 식비/생활비 구분 없이 통합해서 금액 큰 순 상위 5개만 상세 내역으로 보여줌
        private const val REGULAR_TOP_ITEMS_COUNT = 5
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

        val regularTopItems = (foodRecords.map { "식비" to it } + livingRecords.map { "생활비" to it })
            .sortedByDescending { it.second.amount }
            .take(REGULAR_TOP_ITEMS_COUNT)
            .map { Triple(it.first, it.second.merchant, it.second.amount) }

        telegramNotificationService.notifyWeeklyExpenseSummary(
            weekLabel = "${startDate.format(WEEK_LABEL_FORMAT)}~${endDate.format(WEEK_LABEL_FORMAT)}",
            budget = budget,
            foodSpend = categorySpend(foodRecords),
            livingSpend = categorySpend(livingRecords),
            topItems = regularTopItems,
            irregularTotal = irregularRecords.sumOf { it.amount },
            irregularItems = irregularRecords.sortedByDescending { it.amount }.map { it.merchant to it.amount }
        )
    }

    // 진우 결제 = 초영결제(CHOYOUNG_PAYMENT)를 제외한 나머지 결제수단 전부, 초영 결제 = CHOYOUNG_PAYMENT만
    private fun categorySpend(records: List<ExpenseRecord>): CategorySpend {
        val total = records.sumOf { it.amount }
        val choyoungTotal = records.filter { it.paymentMethod == PaymentMethod.CHOYOUNG_PAYMENT }.sumOf { it.amount }
        return CategorySpend(total = total, jinwooTotal = total - choyoungTotal, choyoungTotal = choyoungTotal)
    }
}
