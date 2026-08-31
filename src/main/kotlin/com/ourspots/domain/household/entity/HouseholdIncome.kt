package com.ourspots.domain.household.entity

import com.ourspots.common.crypto.EncryptedLongConverter
import com.ourspots.common.util.SoftDeletable
import jakarta.persistence.Column
import jakarta.persistence.Convert
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

// 수입 항목(급여/아동수당 등) — "요약"의 잔액은 이 합계 - household_budget_items(FIXED_COST) 합계로
// 매번 계산해서 보여줌(별도 저장 안 함, 항목이 바뀔 때마다 손으로 맞출 필요 없게)
@Entity
@Table(name = "household_incomes")
@SQLDelete(sql = "UPDATE household_incomes SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
class HouseholdIncome(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 100)
    var label: String,

    @Convert(converter = EncryptedLongConverter::class)
    @Column(nullable = false, columnDefinition = "TEXT")
    var amount: Long,

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
