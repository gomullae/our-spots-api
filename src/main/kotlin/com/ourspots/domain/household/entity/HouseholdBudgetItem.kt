package com.ourspots.domain.household.entity

import com.ourspots.common.crypto.EncryptedLongConverter
import com.ourspots.common.util.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

// 월비용/자산/지출예정액/고정지출 4개 섹션을 sectionType 하나로 묶은 통합 테이블 — 섹션마다 필드가
// 조금씩만 다르고(공통: label/vendor/amount/payer/memo) 행 수도 적어서(다 합쳐 수십 건) 4개 테이블로
// 쪼개지 않고 하나로 관리. 수입(HouseholdIncome)은 성격이 달라(들어오는 돈 vs 나가는/보유한 돈) 별도 테이블
@Entity
@Table(name = "household_budget_items")
@SQLDelete(sql = "UPDATE household_budget_items SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
class HouseholdBudgetItem(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var sectionType: HouseholdSectionType,

    // sectionType=ASSET일 때만 사용
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var assetKind: HouseholdAssetKind? = null,

    @Column(nullable = false, length = 100)
    var label: String,

    @Column(length = 100)
    var vendor: String? = null,

    // AES-256-GCM 암호화(EncryptedLongConverter) — DB 컬럼은 TEXT(암호문), 애플리케이션 코드에서는
    // 평문 Long으로 그대로 다룸(Hibernate가 읽기/쓰기 시점에 자동 변환)
    @Convert(converter = EncryptedLongConverter::class)
    @Column(nullable = false, columnDefinition = "TEXT")
    var amount: Long,

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    var payer: HouseholdPayer? = null,

    @Column(length = 50)
    var autoDebitBank: String? = null,

    var debitDay: Int? = null,

    // 연결계좌 그룹핑 라벨(공과금통장/진우통장/생활비통장 등) — sectionType=FIXED_COST용
    @Column(length = 50)
    var account: String? = null,

    // "2027-04" 형식 — sectionType=PLANNED_EXPENSE일 때만 사용
    @Column(length = 7)
    var plannedMonth: String? = null,

    @Column(length = 300)
    var memo: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    override var deletedAt: LocalDateTime? = null
) : SoftDeletable {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
