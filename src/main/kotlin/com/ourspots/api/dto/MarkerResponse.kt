package com.ourspots.api.dto

import com.ourspots.domain.place.entity.Place
import com.ourspots.domain.place.entity.PlaceType

data class MarkerResponse(
    val id: Long,
    val name: String,
    val type: PlaceType,
    val latitude: Double,
    val longitude: Double,
    val grade: Int?,
    val hasPhotos: Boolean,
    // 공개 사진이 하나라도 있는지 — hasPhotos는 true인데 이게 false면 "사진은 있지만 전부 비공개"라 프론트가
    // 마커 배지를 옅은 회색으로 표시함(눌러봐야 비로그인 사용자에겐 안 보인다는 힌트)
    val hasPublicPhoto: Boolean
) {
    companion object {
        fun from(place: Place, hasPhotos: Boolean = false, hasPublicPhoto: Boolean = false) = MarkerResponse(
            id = place.id,
            name = place.name,
            type = place.type,
            latitude = place.latitude,
            longitude = place.longitude,
            grade = place.grade,
            hasPhotos = hasPhotos,
            hasPublicPhoto = hasPublicPhoto
        )
    }
}
