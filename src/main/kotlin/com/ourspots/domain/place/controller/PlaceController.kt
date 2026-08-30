package com.ourspots.domain.place.controller

import com.ourspots.api.dto.*
import com.ourspots.common.response.ApiResponse
import com.ourspots.domain.auth.service.JwtProvider
import com.ourspots.domain.place.entity.PlaceType
import com.ourspots.domain.place.service.PlaceService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/places")
class PlaceController(
    private val placeService: PlaceService,
    private val jwtProvider: JwtProvider
) {

    @GetMapping("/recent")
    fun getRecentPlaces(
        @RequestParam(required = false) startDate: LocalDate?,
        @RequestParam(required = false) endDate: LocalDate?,
        @RequestParam(required = false) keyword: String?,
        @RequestParam(required = false) type: PlaceType?,
        @RequestParam(required = false) grade: Int?,
        @RequestParam(defaultValue = "true") includeDeleted: Boolean,
        @RequestParam(defaultValue = "CREATED_AT") sortBy: PlaceRecentSortBy,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ApiResponse<Page<PlaceResponse>> {
        val filter = RecentPlacesFilter(startDate, endDate, keyword, type, grade, includeDeleted, sortBy)
        return ApiResponse.success(placeService.getRecentPlaces(filter, page, size))
    }

    // 관리자 "등록 사진 이력" 화면 전용 — 장소 사진만 대상, 공개/비공개 필터 + 등록일시 내림차순 고정
    // (WebMvcConfig에서 /api/places/photos를 AdminOnlyInterceptor 대상에 명시적으로 등록해야
    // GET도 인증이 걸림 — /api/places/**는 기본적으로 JwtInterceptor가 GET을 통과시키므로 주의)
    @GetMapping("/photos")
    fun getPhotoHistory(
        @RequestParam(required = false) isPublic: Boolean?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "20") size: Int
    ): ApiResponse<Page<PhotoAdminResponse>> {
        return ApiResponse.success(placeService.getPhotoHistory(isPublic, page, size))
    }

    @PostMapping("/{id}/restore")
    fun restorePlace(@PathVariable id: Long): ApiResponse<PlaceResponse> {
        return ApiResponse.success(placeService.restorePlace(id))
    }

    @PostMapping("/{id}/sync-google")
    fun syncGoogleRating(@PathVariable id: Long): ApiResponse<PlaceResponse> {
        return ApiResponse.success(placeService.syncGoogleRating(id))
    }

    @GetMapping("/{id}")
    fun getPlace(
        @PathVariable id: Long,
        @RequestHeader("Authorization", required = false) authHeader: String?
    ): ApiResponse<PlaceResponse> {
        val authenticated = jwtProvider.isValidAuthHeader(authHeader)
        return ApiResponse.success(placeService.getPlace(id, authenticated))
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createPlace(
        @Valid @RequestBody request: PlaceCreateRequest
    ): ApiResponse<PlaceResponse> {
        return ApiResponse.success(placeService.createPlace(request))
    }

    @PutMapping("/{id}")
    fun updatePlace(
        @PathVariable id: Long,
        @Valid @RequestBody request: PlaceUpdateRequest
    ): ApiResponse<PlaceResponse> {
        return ApiResponse.success(placeService.updatePlace(id, request))
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deletePlace(@PathVariable id: Long) {
        placeService.deletePlace(id)
    }

}
