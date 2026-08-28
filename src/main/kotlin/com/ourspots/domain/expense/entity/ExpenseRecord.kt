package com.ourspots.domain.expense.entity

import com.ourspots.common.util.SoftDeletable
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "expense_records")
@SQLDelete(sql = "UPDATE expense_records SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
class ExpenseRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var expenseDate: LocalDate,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var paymentMethod: PaymentMethod,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var category: ExpenseCategory,

    @Column(nullable = false, length = 100)
    var merchant: String,

    @Column(nullable = false)
    var amount: Long,

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
