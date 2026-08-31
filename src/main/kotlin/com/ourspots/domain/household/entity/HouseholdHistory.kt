package com.ourspots.domain.household.entity

import com.ourspots.common.crypto.EncryptedLongConverter
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

// 항목 하나(HouseholdIncome 또는 HouseholdBudgetItem)가 추가/수정/삭제/복구될 때마다 그 순간의 값을
// 스냅샷으로 한 줄 남김 — 전체 데이터셋을 통째로 복사하는 무거운 스냅샷이 아니라 "바뀐 그 항목 하나"만
// 기록하는 가벼운 방식. item_id로 조회하면 그 항목이 시간에 따라 어떻게 바뀌어왔는지 타임라인이 그대로 나옴.
// append-only 로그라 소프트 삭제 없음(수정/삭제 자체가 없는 테이블)
@Entity
@Table(name = "household_history")
class HouseholdHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val itemType: HouseholdHistoryItemType,

    @Column(nullable = false)
    val itemId: Long,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val action: HouseholdHistoryAction,

    // 아래 필드들은 HouseholdBudgetItem일 때만 값이 있고(INCOME이면 전부 null), amount/label/vendor/memo는
    // 두 타입 다 공용으로 사용
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val sectionType: HouseholdSectionType? = null,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val assetKind: HouseholdAssetKind? = null,

    @Column(nullable = false, length = 100)
    val label: String,

    @Column(length = 100)
    val vendor: String? = null,

    @Convert(converter = EncryptedLongConverter::class)
    @Column(nullable = false, columnDefinition = "TEXT")
    val amount: Long,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    val payer: HouseholdPayer? = null,

    @Column(length = 50)
    val autoDebitBank: String? = null,

    val debitDay: Int? = null,

    @Column(length = 50)
    val account: String? = null,

    @Column(length = 7)
    val plannedMonth: String? = null,

    @Column(length = 300)
    val memo: String? = null,

    @Column(nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    companion object {
        fun fromBudgetItem(item: HouseholdBudgetItem, action: HouseholdHistoryAction) = HouseholdHistory(
            itemType = HouseholdHistoryItemType.BUDGET_ITEM,
            itemId = item.id,
            action = action,
            sectionType = item.sectionType,
            assetKind = item.assetKind,
            label = item.label,
            vendor = item.vendor,
            amount = item.amount,
            payer = item.payer,
            autoDebitBank = item.autoDebitBank,
            debitDay = item.debitDay,
            account = item.account,
            plannedMonth = item.plannedMonth,
            memo = item.memo
        )

        fun fromIncome(income: HouseholdIncome, action: HouseholdHistoryAction) = HouseholdHistory(
            itemType = HouseholdHistoryItemType.INCOME,
            itemId = income.id,
            action = action,
            label = income.label,
            amount = income.amount,
            memo = income.memo
        )
    }
}
