package com.ourspots.domain.place.entity

enum class PlaceType {
    RESTAURANT,              // 맛집
    KIDS_PLAYGROUND,         // 아이 놀이터
    RELAXATION,              // 아빠의 쉼터
    MY_FOOTPRINT,            // 나의 발자취
    RECOMMENDED_RESTAURANT,  // 추천 맛집
    RECOMMENDED_SPOT,        // 추천 명소
    OTHER;                   // 기타 — 위 카테고리에 안 들어가는 장소(세차장/정비소 등)를 모아두는 catch-all

    companion object {
        val PERSONAL_TYPES = listOf(MY_FOOTPRINT, RECOMMENDED_RESTAURANT, RECOMMENDED_SPOT, OTHER)
    }
}
