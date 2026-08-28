package com.ourspots.api.dto

import com.ourspots.domain.schedule.entity.ScheduleCategory
import com.ourspots.domain.schedule.entity.ScheduleEvent
import java.time.LocalDateTime

// 프론트 로컬 캐시 검증용 — count/lastModified 조합으로 전체 이벤트를 안 내려받고도 변경 여부만 가볍게 확인
data class ScheduleMetaResponse(
    val count: Long,
    val lastModified: LocalDateTime?
)

data class ScheduleEventResponse(
    val id: Long,
    val title: String,
    val category: ScheduleCategory,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val allDay: Boolean,
    val memo: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
    val photos: List<PhotoResponse> = emptyList()
) {
    companion object {
        // 새로 생성되는 일정은 이 시점에 사진이 존재할 수 없어(등록 폼에서 저장 성공 후에야 confirm 호출) 기본값 emptyList로 충분 —
        // 이미 존재하는 일정을 응답할 때만 호출부(ScheduleService)가 실제 사진 목록을 넘겨줌
        fun from(event: ScheduleEvent, photos: List<PhotoResponse> = emptyList()) = ScheduleEventResponse(
            id = event.id,
            title = event.title,
            category = event.category,
            startAt = event.startAt,
            endAt = event.endAt,
            allDay = event.allDay,
            memo = event.memo,
            createdAt = event.createdAt,
            updatedAt = event.updatedAt,
            deletedAt = event.deletedAt,
            photos = photos
        )
    }
}
