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
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "10") size: Int
    ): ApiResponse<Page<PlaceResponse>> {
        val filter = RecentPlacesFilter(startDate, endDate, keyword, type, grade, includeDeleted)
        return ApiResponse.success(placeService.getRecentPlaces(filter, page, size))
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
