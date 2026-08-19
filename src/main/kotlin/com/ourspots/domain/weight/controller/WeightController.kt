package com.ourspots.domain.weight.controller

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
