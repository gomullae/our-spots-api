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
    val deletedAt: LocalDateTime?,
    val photos: List<PhotoResponse> = emptyList()
) {
    companion object {
        // 새로 생성되는 장소는 이 시점에 사진이 존재할 수 없어(등록 폼에서 저장 성공 후에야 confirm 호출) 기본값 emptyList로 충분 —
        // 이미 존재하는 장소를 응답할 때만 호출부(PlaceService)가 실제 사진 목록을 넘겨줌
        fun from(place: Place, photos: List<PhotoResponse> = emptyList()) = PlaceResponse(
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
            deletedAt = place.deletedAt,
            photos = photos
        )
    }
}
