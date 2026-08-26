package com.ourspots.domain.weight.controller

import com.ourspots.api.dto.WeightMetaResponse
import com.ourspots.api.dto.WeightRecordResponse
import com.ourspots.api.dto.WeightRecordUpsertRequest
import com.ourspots.common.response.ApiResponse
import com.ourspots.domain.weight.service.WeightService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/weights")
class WeightController(
    private val weightService: WeightService
) {

    @GetMapping
    fun getAllRecords(): ApiResponse<List<WeightRecordResponse>> =
        ApiResponse.success(weightService.getAllRecords())

    // 프론트가 로컬(localStorage) 캐시를 그대로 써도 되는지 확인하는 가벼운 엔드포인트 — 전체 목록 대신 count/lastModified만 반환
    @GetMapping("/meta")
    fun getMeta(): ApiResponse<WeightMetaResponse> =
        ApiResponse.success(weightService.getMeta())

    @PostMapping
    fun upsertRecord(
        @Valid @RequestBody request: WeightRecordUpsertRequest
    ): ApiResponse<WeightRecordResponse> =
        ApiResponse.success(weightService.upsertRecord(request))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRecord(@PathVariable id: Long) {
        weightService.deleteRecord(id)
    }
}
