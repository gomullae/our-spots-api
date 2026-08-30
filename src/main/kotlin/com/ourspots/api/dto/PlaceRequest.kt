package com.ourspots.api.dto

import com.ourspots.domain.place.entity.PlaceType
import jakarta.validation.constraints.*
import java.time.LocalDate

data class PlaceCreateRequest(
    @field:NotBlank
    val name: String,

    @field:NotNull
    val type: PlaceType,

    @field:NotBlank
    val address: String,

    @field:NotNull
    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val latitude: Double,

    @field:NotNull
    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val longitude: Double,

    val description: String? = null,

    @field:Min(1)
    @field:Max(3)
    val grade: Int? = null
)

data class PlaceUpdateRequest(
    @field:Size(min = 1)
    val name: String? = null,
    val type: PlaceType? = null,
    @field:Size(min = 1)
    val address: String? = null,
    @field:DecimalMin("-90.0")
    @field:DecimalMax("90.0")
    val latitude: Double? = null,
    @field:DecimalMin("-180.0")
    @field:DecimalMax("180.0")
    val longitude: Double? = null,
    val description: String? = null,
    @field:Min(1)
    @field:Max(3)
    val grade: Int? = null,
    val googlePlaceId: String? = null,
    val googleRating: Double? = null,
    val googleRatingsTotal: Int? = null
)

// "기간" 필터의 기준 컬럼도 이 값을 따라감 — CREATED_AT이면 등록일시, UPDATED_AT이면 수정일시 기준으로
// 기간을 해석(PlaceRepository의 SEARCH_RECENT_PLACES_WHERE / _BY_UPDATED_AT 참고). 처음엔 정렬만 바꾸고
// 기간은 항상 등록일시 기준으로 뒀었는데, "수정일시순인데 최근 3개월 등록분 안에서만 재정렬되는 것 같다"는
// 피드백(2026-08-30)으로 정렬 기준과 기간 필터 기준을 통일함
enum class PlaceRecentSortBy { CREATED_AT, UPDATED_AT }

// 관리자 "등록 장소 이력" 화면의 검색 조건 — 파라미터가 늘어날 때 순서 실수를 방지하기 위해 하나로 묶음
data class RecentPlacesFilter(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val keyword: String? = null,
    val type: PlaceType? = null,
    val grade: Int? = null,
    val includeDeleted: Boolean = true,
    val sortBy: PlaceRecentSortBy = PlaceRecentSortBy.CREATED_AT
)
