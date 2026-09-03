package com.ourspots.api.dto

import com.ourspots.domain.schedule.entity.ScheduleCategory
import com.ourspots.domain.schedule.entity.ScheduleEvent
import com.ourspots.domain.schedule.entity.ScheduleMemo
import java.time.LocalDateTime

// 프론트 로컬 캐시 검증용 — count/lastModified 조합으로 전체 이벤트를 안 내려받고도 변경 여부만 가볍게 확인
data class ScheduleMetaResponse(
    val count: Long,
    val lastModified: LocalDateTime?
)

data class ScheduleMemoResponse(
    val id: Long,
    val content: String,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(memo: ScheduleMemo) = ScheduleMemoResponse(memo.id, memo.content, memo.createdAt)
    }
}

data class ScheduleEventResponse(
    val id: Long,
    val title: String,
    val category: ScheduleCategory,
    val startAt: LocalDateTime,
    val endAt: LocalDateTime,
    val allDay: Boolean,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime,
    val deletedAt: LocalDateTime?,
    val photos: List<PhotoResponse> = emptyList(),
    val memos: List<ScheduleMemoResponse> = emptyList()
) {
    companion object {
        // 새로 생성되는 일정은 이 시점에 사진/메모가 존재할 수 없어(둘 다 저장 성공 후에야 추가 가능) 기본값
        // emptyList로 충분 — 이미 존재하는 일정을 응답할 때만 호출부(ScheduleService)가 실제 목록을 넘겨줌
        fun from(event: ScheduleEvent, photos: List<PhotoResponse> = emptyList(), memos: List<ScheduleMemoResponse> = emptyList()) = ScheduleEventResponse(
            id = event.id,
            title = event.title,
            category = event.category,
            startAt = event.startAt,
            endAt = event.endAt,
            allDay = event.allDay,
            createdAt = event.createdAt,
            updatedAt = event.updatedAt,
            deletedAt = event.deletedAt,
            photos = photos,
            memos = memos
        )
    }
}
