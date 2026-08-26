package com.ourspots.domain.schedule.controller

import com.ourspots.api.dto.ScheduleEventRequest
import com.ourspots.api.dto.ScheduleEventResponse
import com.ourspots.api.dto.ScheduleMetaResponse
import com.ourspots.common.response.ApiResponse
import com.ourspots.domain.schedule.service.ScheduleService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime

@RestController
@RequestMapping("/api/schedules")
class ScheduleController(
    private val scheduleService: ScheduleService
) {

    @GetMapping
    fun getEvents(
        @RequestParam start: LocalDateTime,
        @RequestParam end: LocalDateTime,
        @RequestParam(defaultValue = "false") includeDeleted: Boolean
    ): ApiResponse<List<ScheduleEventResponse>> =
        ApiResponse.success(scheduleService.getEvents(start, end, includeDeleted))

    // 프론트가 로컬(localStorage) 캐시를 그대로 써도 되는지 확인하는 가벼운 엔드포인트 — 전체 목록 대신 count/lastModified만 반환
    @GetMapping("/meta")
    fun getMeta(): ApiResponse<ScheduleMetaResponse> =
        ApiResponse.success(scheduleService.getMeta())

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createEvent(
        @Valid @RequestBody request: ScheduleEventRequest
    ): ApiResponse<ScheduleEventResponse> =
        ApiResponse.success(scheduleService.createEvent(request))

    @PutMapping("/{id}")
    fun updateEvent(
        @PathVariable id: Long,
        @Valid @RequestBody request: ScheduleEventRequest
    ): ApiResponse<ScheduleEventResponse> =
        ApiResponse.success(scheduleService.updateEvent(id, request))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteEvent(@PathVariable id: Long) {
        scheduleService.deleteEvent(id)
    }

    @PostMapping("/{id}/restore")
    fun restoreEvent(@PathVariable id: Long): ApiResponse<ScheduleEventResponse> =
        ApiResponse.success(scheduleService.restoreEvent(id))
}
