package com.ourspots.domain.schedule.controller

import com.ourspots.api.dto.ScheduleEventRequest
import com.ourspots.api.dto.ScheduleEventResponse
import com.ourspots.api.dto.ScheduleMemoRequest
import com.ourspots.api.dto.ScheduleMemoResponse
import com.ourspots.api.dto.ScheduleMetaResponse
import com.ourspots.api.dto.SchedulePhotoAddedRequest
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

    // 상세보기에서 붙여넣기로 사진을 바로 추가한 직후 프론트가 호출 — 사진 자체는 이미 /api/photos/confirm으로
    // 저장 완료된 상태라 여기선 알림만 발송(별도 응답 데이터 없음)
    @PostMapping("/{id}/notify-photos-added")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun notifyPhotosAdded(
        @PathVariable id: Long,
        @Valid @RequestBody request: SchedulePhotoAddedRequest
    ) {
        scheduleService.notifyPhotosAdded(id, request)
    }

    @PostMapping("/{id}/memos")
    @ResponseStatus(HttpStatus.CREATED)
    fun addMemo(
        @PathVariable id: Long,
        @Valid @RequestBody request: ScheduleMemoRequest
    ): ApiResponse<ScheduleMemoResponse> =
        ApiResponse.success(scheduleService.addMemo(id, request))

    @PutMapping("/{id}/memos/{memoId}")
    fun updateMemo(
        @PathVariable id: Long,
        @PathVariable memoId: Long,
        @Valid @RequestBody request: ScheduleMemoRequest
    ): ApiResponse<ScheduleMemoResponse> =
        ApiResponse.success(scheduleService.updateMemo(id, memoId, request))

    @DeleteMapping("/{id}/memos/{memoId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteMemo(@PathVariable id: Long, @PathVariable memoId: Long) {
        scheduleService.deleteMemo(id, memoId)
    }
}
