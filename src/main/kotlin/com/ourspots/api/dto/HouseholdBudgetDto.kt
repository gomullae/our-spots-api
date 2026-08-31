package com.ourspots.api.dto

import com.ourspots.domain.household.entity.HouseholdAssetKind
import com.ourspots.domain.household.entity.HouseholdBudgetItem
import com.ourspots.domain.household.entity.HouseholdHistory
import com.ourspots.domain.household.entity.HouseholdHistoryAction
import com.ourspots.domain.household.entity.HouseholdIncome
import com.ourspots.domain.household.entity.HouseholdPayer
import com.ourspots.domain.household.entity.HouseholdSectionType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.time.LocalDateTime

data class HouseholdIncomeRequest(
    @field:NotBlank val label: String,
    @field:NotNull val amount: Long,
    val memo: String? = null
)

data class HouseholdIncomeResponse(
    val id: Long,
    val label: String,
    val amount: Long,
    val memo: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?
) {
    companion object {
        fun from(entity: HouseholdIncome) = HouseholdIncomeResponse(
            id = entity.id,
            label = entity.label,
            amount = entity.amount,
            memo = entity.memo,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt
        )
    }
}

data class HouseholdBudgetItemRequest(
    @field:NotNull val sectionType: HouseholdSectionType,
    val assetKind: HouseholdAssetKind? = null,
    @field:NotBlank val label: String,
    val vendor: String? = null,
    @field:NotNull val amount: Long,
    val payer: HouseholdPayer? = null,
    val autoDebitBank: String? = null,
    @field:Min(1) @field:Max(31) val debitDay: Int? = null,
    val account: String? = null,
    val plannedMonth: String? = null,
    val memo: String? = null
)

data class HouseholdBudgetItemResponse(
    val id: Long,
    val sectionType: HouseholdSectionType,
    val assetKind: HouseholdAssetKind?,
    val label: String,
    val vendor: String?,
    val amount: Long,
    val payer: HouseholdPayer?,
    val autoDebitBank: String?,
    val debitDay: Int?,
    val account: String?,
    val plannedMonth: String?,
    val memo: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?
) {
    companion object {
        fun from(entity: HouseholdBudgetItem) = HouseholdBudgetItemResponse(
            id = entity.id,
            sectionType = entity.sectionType,
            assetKind = entity.assetKind,
            label = entity.label,
            vendor = entity.vendor,
            amount = entity.amount,
            payer = entity.payer,
            autoDebitBank = entity.autoDebitBank,
            debitDay = entity.debitDay,
            account = entity.account,
            plannedMonth = entity.plannedMonth,
            memo = entity.memo,
            createdAt = entity.createdAt,
            updatedAt = entity.updatedAt,
            deletedAt = entity.deletedAt
        )
    }
}

// 대시보드 한 화면에 필요한 전체 데이터 — 수입 + 예산 항목(고정비/자산/지출예정액/구독료)을 한 번에 응답
data class HouseholdBudgetOverviewResponse(
    val incomes: List<HouseholdIncomeResponse>,
    val items: List<HouseholdBudgetItemResponse>
)

// 프론트 localStorage 캐시 검증용 — WeightMetaResponse와 동일 패턴. count는 수입+예산 항목 개수 합,
// lastModified는 두 테이블 중 더 최근에 수정된 값(HouseholdBudgetService.getMeta()에서 조합)
data class HouseholdBudgetMetaResponse(
    val count: Long,
    val lastModified: LocalDateTime?
)

data class HouseholdHistoryResponse(
    val id: Long,
    val action: HouseholdHistoryAction,
    val sectionType: HouseholdSectionType?,
    val assetKind: HouseholdAssetKind?,
    val label: String,
    val vendor: String?,
    val amount: Long,
    val payer: HouseholdPayer?,
    val autoDebitBank: String?,
    val debitDay: Int?,
    val account: String?,
    val plannedMonth: String?,
    val memo: String?,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(entity: HouseholdHistory) = HouseholdHistoryResponse(
            id = entity.id,
            action = entity.action,
            sectionType = entity.sectionType,
            assetKind = entity.assetKind,
            label = entity.label,
            vendor = entity.vendor,
            amount = entity.amount,
            payer = entity.payer,
            autoDebitBank = entity.autoDebitBank,
            debitDay = entity.debitDay,
            account = entity.account,
            plannedMonth = entity.plannedMonth,
            memo = entity.memo,
            createdAt = entity.createdAt
        )
    }
}
