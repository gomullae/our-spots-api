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
    val deletedAt: LocalDateTime?
) {
    companion object {
        fun from(event: ScheduleEvent) = ScheduleEventResponse(
            id = event.id,
            title = event.title,
            category = event.category,
            startAt = event.startAt,
            endAt = event.endAt,
            allDay = event.allDay,
            memo = event.memo,
            createdAt = event.createdAt,
            updatedAt = event.updatedAt,
            deletedAt = event.deletedAt
        )
    }
}
