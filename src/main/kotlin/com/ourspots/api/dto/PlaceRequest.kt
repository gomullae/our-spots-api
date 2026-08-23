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

// 관리자 "최근 등록 장소" 화면의 검색 조건 — 파라미터가 늘어날 때 순서 실수를 방지하기 위해 하나로 묶음
data class RecentPlacesFilter(
    val startDate: LocalDate? = null,
    val endDate: LocalDate? = null,
    val keyword: String? = null,
    val type: PlaceType? = null,
    val grade: Int? = null,
    val includeDeleted: Boolean = true
)
