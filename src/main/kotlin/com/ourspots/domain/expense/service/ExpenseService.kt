package com.ourspots.domain.expense.service

import com.ourspots.api.dto.ExpenseMetaResponse
import com.ourspots.api.dto.ExpenseRecordRequest
import com.ourspots.api.dto.ExpenseRecordResponse
import com.ourspots.common.notification.CategorySpend
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.common.util.findByIdOrThrow
import com.ourspots.common.util.restoreSoftDeleted
import com.ourspots.domain.expense.entity.ExpenseCategory
import com.ourspots.domain.expense.entity.ExpenseRecord
import com.ourspots.domain.expense.entity.PaymentMethod
import com.ourspots.domain.expense.repository.ExpenseRecordRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
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

    // 프론트가 로컬 캐시를 그대로 써도 되는지 확인하는 용도 — count(등록/삭제 감지) + lastModified(수정 감지) 조합
    fun getMeta(): ExpenseMetaResponse =
        ExpenseMetaResponse(count = expenseRecordRepository.count(), lastModified = expenseRecordRepository.findMaxUpdatedAt())

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
        val record = expenseRecordRepository.findByIdOrThrow(id, "Expense record")

        record.expenseDate = request.expenseDate
        record.paymentMethod = request.paymentMethod
        record.category = request.category
        record.merchant = request.merchant
        record.amount = request.amount

        return ExpenseRecordResponse.from(expenseRecordRepository.save(record))
    }

    @Transactional
    fun deleteRecord(id: Long) {
        val record = expenseRecordRepository.findByIdOrThrow(id, "Expense record")
        expenseRecordRepository.delete(record)
    }

    @Transactional
    fun restoreRecord(id: Long): ExpenseRecordResponse {
        val saved = restoreSoftDeleted(id, "Expense record", expenseRecordRepository::findByIdIncludingDeleted) { expenseRecordRepository.save(it) }
        return ExpenseRecordResponse.from(saved)
    }

    // NOT_SUPPORTED로 클래스 레벨 @Transactional(readOnly=true)를 명시적으로 덮어씀 — 안 그러면 이 메서드 전체가
    // 읽기 전용 트랜잭션 하나로 묶여서, 텔레그램 발송(최대 수 초 소요되는 외부 HTTP 호출) 내내 커넥션 풀(운영 5개,
    // 앱 전체 공유)의 커넥션 하나를 붙잡고 있게 됨. findByExpenseDateBetween은 리포지토리 자체가 짧은 자체
    // 트랜잭션으로 처리하므로 조회 자체는 그대로 정상 동작함
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
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
