package com.ourspots.domain.weight.entity

import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDate
import java.time.LocalDateTime

@Entity
@Table(name = "weight_records")
@SQLDelete(sql = "UPDATE weight_records SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
class WeightRecord(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var recordedDate: LocalDate,

    @Column(nullable = false)
    var weightKg: Double,

    var memo: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    var deletedAt: LocalDateTime? = null
) {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
