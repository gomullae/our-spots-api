package com.ourspots.domain.place.entity

import com.ourspots.common.util.SoftDeletable
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@Table(name = "places")
@SQLDelete(sql = "UPDATE places SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
class Place(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var type: PlaceType,

    @Column(nullable = false)
    var address: String,

    @Column(nullable = false)
    var latitude: Double,

    @Column(nullable = false)
    var longitude: Double,

    var description: String? = null,

    // 맛집 등급 (1=최고, 2=좋음, 3=보통)
    var grade: Int? = null,

    // Google Places API 연동
    var googlePlaceId: String? = null,
    var googleRating: Double? = null,
    var googleRatingsTotal: Int? = null,
    var googleRatingFailCount: Int = 0,
    var googleRatingUpdatedAt: LocalDateTime? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    // Soft Delete
    override var deletedAt: LocalDateTime? = null
) : SoftDeletable {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
