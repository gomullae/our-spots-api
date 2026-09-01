package com.ourspots.domain.household.service

import com.ourspots.api.dto.HouseholdBudgetItemRequest
import com.ourspots.api.dto.HouseholdBudgetItemResponse
import com.ourspots.api.dto.HouseholdBudgetMetaResponse
import com.ourspots.api.dto.HouseholdBudgetOverviewResponse
import com.ourspots.api.dto.HouseholdHistoryResponse
import com.ourspots.api.dto.HouseholdIncomeRequest
import com.ourspots.api.dto.HouseholdIncomeResponse
import com.ourspots.common.notification.HouseholdItemSummary
import com.ourspots.common.notification.TelegramNotificationService
import com.ourspots.common.util.findByIdOrThrow
import com.ourspots.common.util.restoreSoftDeleted
import com.ourspots.domain.household.entity.HouseholdBudgetItem
import com.ourspots.domain.household.entity.HouseholdHistory
import com.ourspots.domain.household.entity.HouseholdHistoryAction
import com.ourspots.domain.household.entity.HouseholdHistoryItemType
import com.ourspots.domain.household.entity.HouseholdIncome
import com.ourspots.domain.household.entity.HouseholdPayer
import com.ourspots.domain.household.entity.HouseholdSectionType
import com.ourspots.domain.household.repository.HouseholdBudgetItemRepository
import com.ourspots.domain.household.repository.HouseholdHistoryRepository
import com.ourspots.domain.household.repository.HouseholdIncomeRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class HouseholdBudgetService(
    private val incomeRepository: HouseholdIncomeRepository,
    private val itemRepository: HouseholdBudgetItemRepository,
    private val historyRepository: HouseholdHistoryRepository,
    private val telegramNotificationService: TelegramNotificationService
) {

    fun getOverview(includeDeleted: Boolean): HouseholdBudgetOverviewResponse {
        val incomes = incomeRepository.findAllForDashboard(includeDeleted).map { HouseholdIncomeResponse.from(it) }
        val items = itemRepository.findAllForDashboard(includeDeleted).map { HouseholdBudgetItemResponse.from(it) }
        return HouseholdBudgetOverviewResponse(incomes, items)
    }

    // 프론트 localStorage 캐시 검증용(WeightService.getMeta()와 동일 패턴) — 수입+예산 항목을 합쳐서
    // count/lastModified 하나로 응답(대시보드가 둘을 한 화면에서 같이 보여주므로 캐시도 하나로 묶어서 검증)
    fun getMeta(): HouseholdBudgetMetaResponse {
        val incomeCount = incomeRepository.count()
        val itemCount = itemRepository.count()
        val lastModified = listOfNotNull(incomeRepository.findMaxUpdatedAt(), itemRepository.findMaxUpdatedAt()).maxOrNull()
        return HouseholdBudgetMetaResponse(count = incomeCount + itemCount, lastModified = lastModified)
    }

    // ===== 수입 =====

    // NOT_SUPPORTED: 텔레그램 발송(최대 수 초 소요되는 외부 HTTP 호출)이 DB 트랜잭션 안에 들어있으면 그 시간
    // 내내 커넥션 풀(운영 5개, 앱 전체 공유)의 커넥션 하나를 붙잡고 있게 됨 — save()는 Spring Data JPA
    // 리포지토리 자체가 짧은 자체 트랜잭션으로 처리하므로, 이 메서드 레벨에서 트랜잭션을 열지 않아도 저장은
    // 그대로 원자적으로 처리됨(ScheduleService.createEvent/updateEvent와 동일한 이유)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun createIncome(request: HouseholdIncomeRequest): HouseholdIncomeResponse {
        val entity = HouseholdIncome(label = request.label, amount = request.amount, memo = request.memo)
        val saved = incomeRepository.save(entity)
        historyRepository.save(HouseholdHistory.fromIncome(saved, HouseholdHistoryAction.CREATE))
        telegramNotificationService.notifyHouseholdItemCreated(incomeSummary(saved))
        return HouseholdIncomeResponse.from(saved)
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun updateIncome(id: Long, request: HouseholdIncomeRequest): HouseholdIncomeResponse {
        val entity = incomeRepository.findByIdOrThrow(id, "Household income")
        val before = incomeSummary(entity)

        entity.label = request.label
        entity.amount = request.amount
        entity.memo = request.memo

        val saved = incomeRepository.save(entity)
        historyRepository.save(HouseholdHistory.fromIncome(saved, HouseholdHistoryAction.UPDATE))
        telegramNotificationService.notifyHouseholdItemUpdated(before, incomeSummary(saved))
        return HouseholdIncomeResponse.from(saved)
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun deleteIncome(id: Long) {
        val entity = incomeRepository.findByIdOrThrow(id, "Household income")
        val summary = incomeSummary(entity)
        incomeRepository.delete(entity)
        historyRepository.save(HouseholdHistory.fromIncome(entity, HouseholdHistoryAction.DELETE))
        telegramNotificationService.notifyHouseholdItemDeleted(summary)
    }

    @Transactional
    fun restoreIncome(id: Long): HouseholdIncomeResponse {
        val saved = restoreSoftDeleted(id, "Household income", incomeRepository::findByIdIncludingDeleted) { incomeRepository.save(it) }
        historyRepository.save(HouseholdHistory.fromIncome(saved, HouseholdHistoryAction.RESTORE))
        return HouseholdIncomeResponse.from(saved)
    }

    fun getIncomeHistory(id: Long): List<HouseholdHistoryResponse> =
        historyRepository.findByItemTypeAndItemId(HouseholdHistoryItemType.INCOME, id).map { HouseholdHistoryResponse.from(it) }

    // ===== 예산 항목(고정비/자산/지출예정액/구독료) =====

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun createItem(request: HouseholdBudgetItemRequest): HouseholdBudgetItemResponse {
        val entity = HouseholdBudgetItem(
            sectionType = request.sectionType,
            assetKind = request.assetKind,
            label = request.label,
            vendor = request.vendor,
            amount = request.amount,
            payer = request.payer,
            autoDebitBank = request.autoDebitBank,
            debitDay = request.debitDay,
            account = request.account,
            plannedMonth = request.plannedMonth,
            memo = request.memo
        )
        val saved = itemRepository.save(entity)
        historyRepository.save(HouseholdHistory.fromBudgetItem(saved, HouseholdHistoryAction.CREATE))
        notifyUnlessSubscription(saved.sectionType) { telegramNotificationService.notifyHouseholdItemCreated(itemSummary(saved)) }
        return HouseholdBudgetItemResponse.from(saved)
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun updateItem(id: Long, request: HouseholdBudgetItemRequest): HouseholdBudgetItemResponse {
        val entity = itemRepository.findByIdOrThrow(id, "Household budget item")
        val before = itemSummary(entity)

        entity.sectionType = request.sectionType
        entity.assetKind = request.assetKind
        entity.label = request.label
        entity.vendor = request.vendor
        entity.amount = request.amount
        entity.payer = request.payer
        entity.autoDebitBank = request.autoDebitBank
        entity.debitDay = request.debitDay
        entity.account = request.account
        entity.plannedMonth = request.plannedMonth
        entity.memo = request.memo

        val saved = itemRepository.save(entity)
        historyRepository.save(HouseholdHistory.fromBudgetItem(saved, HouseholdHistoryAction.UPDATE))
        notifyUnlessSubscription(saved.sectionType) { telegramNotificationService.notifyHouseholdItemUpdated(before, itemSummary(saved)) }
        return HouseholdBudgetItemResponse.from(saved)
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun deleteItem(id: Long) {
        val entity = itemRepository.findByIdOrThrow(id, "Household budget item")
        val summary = itemSummary(entity)
        itemRepository.delete(entity)
        historyRepository.save(HouseholdHistory.fromBudgetItem(entity, HouseholdHistoryAction.DELETE))
        notifyUnlessSubscription(entity.sectionType) { telegramNotificationService.notifyHouseholdItemDeleted(summary) }
    }

    @Transactional
    fun restoreItem(id: Long): HouseholdBudgetItemResponse {
        val saved = restoreSoftDeleted(id, "Household budget item", itemRepository::findByIdIncludingDeleted) { itemRepository.save(it) }
        historyRepository.save(HouseholdHistory.fromBudgetItem(saved, HouseholdHistoryAction.RESTORE))
        return HouseholdBudgetItemResponse.from(saved)
    }

    fun getItemHistory(id: Long): List<HouseholdHistoryResponse> =
        historyRepository.findByItemTypeAndItemId(HouseholdHistoryItemType.BUDGET_ITEM, id).map { HouseholdHistoryResponse.from(it) }

    private fun sectionLabel(sectionType: HouseholdSectionType): String = when (sectionType) {
        HouseholdSectionType.FIXED_COST -> "고정비"
        HouseholdSectionType.ASSET -> "자산"
        HouseholdSectionType.PLANNED_EXPENSE -> "지출예정액"
        HouseholdSectionType.SUBSCRIPTION -> "구독료"
    }

    // TODO(household-notify-subscription): 구독료는 일단 알림 대상에서 제외(2026-09-01, "나중에 추가할게")
    // — 재개할 땐 이 가드(호출부 3곳이 아니라 이 한 곳만) 지우면 됨
    private fun notifyUnlessSubscription(sectionType: HouseholdSectionType, notify: () -> Unit) {
        if (sectionType != HouseholdSectionType.SUBSCRIPTION) notify()
    }

    private fun payerLabel(payer: HouseholdPayer): String = when (payer) {
        HouseholdPayer.JINWOO -> "진우"
        HouseholdPayer.CHOYOUNG -> "초영"
        HouseholdPayer.FAMILY -> "가족"
    }

    // 알림엔 이 7개 필드만 노출(구분/항목명/업체명/금액/대상자/이체일/비고) — 수입은 payer/debitDay 개념이
    // 없어서 null로 고정
    private fun incomeSummary(income: HouseholdIncome) =
        HouseholdItemSummary(
            sectionLabel = "수입",
            label = income.label,
            vendor = null,
            amount = income.amount,
            payerLabel = null,
            debitDay = null,
            memo = income.memo
        )

    private fun itemSummary(item: HouseholdBudgetItem) =
        HouseholdItemSummary(
            sectionLabel = sectionLabel(item.sectionType),
            label = item.label,
            vendor = item.vendor,
            amount = item.amount,
            payerLabel = item.payer?.let { payerLabel(it) },
            debitDay = item.debitDay,
            memo = item.memo
        )
}
