package com.ourspots.api.controller

import com.ourspots.api.dto.MarkerResponse
import com.ourspots.common.response.ApiResponse
import com.ourspots.domain.auth.service.JwtProvider
import com.ourspots.domain.place.entity.PlaceType
import com.ourspots.domain.place.service.PlaceService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/map")
class MapController(
    private val placeService: PlaceService,
    private val jwtProvider: JwtProvider
) {

    @GetMapping("/markers")
    fun getMarkers(
        @RequestParam(required = false) type: PlaceType?,
        @RequestParam(required = false) swLat: Double?,
        @RequestParam(required = false) swLng: Double?,
        @RequestParam(required = false) neLat: Double?,
        @RequestParam(required = false) neLng: Double?,
        @RequestHeader("Authorization", required = false) authHeader: String?
    ): ApiResponse<List<MarkerResponse>> {
        val authenticated = jwtProvider.isValidAuthHeader(authHeader)
        return ApiResponse.success(placeService.getMarkers(type, swLat, swLng, neLat, neLng, authenticated))
    }

    // JwtInterceptor가 이 경로("/api/map/markers/refresh")를 별도로 등록해 인증을 강제함
    @PostMapping("/markers/refresh")
    fun refreshMarkers(): ApiResponse<List<MarkerResponse>> {
        placeService.evictMarkersCache()
        return ApiResponse.success(placeService.getMarkers(null, null, null, null, null, true))
    }

}
