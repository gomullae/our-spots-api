package com.ourspots.api.dto

import com.ourspots.domain.place.entity.Place
import com.ourspots.domain.place.entity.PlaceType
import java.time.LocalDateTime

data class PlaceResponse(
    val id: Long,
    val name: String,
    val type: PlaceType,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val description: String?,
    val grade: Int?,
    val googlePlaceId: String?,
    val googleRating: Double?,
    val googleRatingsTotal: Int?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?
) {
    companion object {
        fun from(place: Place) = PlaceResponse(
            id = place.id,
            name = place.name,
            type = place.type,
            address = place.address,
            latitude = place.latitude,
            longitude = place.longitude,
            description = place.description,
            grade = place.grade,
            googlePlaceId = place.googlePlaceId,
            googleRating = place.googleRating,
            googleRatingsTotal = place.googleRatingsTotal,
            createdAt = place.createdAt,
            updatedAt = place.updatedAt,
            deletedAt = place.deletedAt
        )
    }
}
