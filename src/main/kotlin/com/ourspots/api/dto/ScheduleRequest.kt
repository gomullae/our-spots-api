package com.ourspots.api.dto

import com.ourspots.domain.schedule.entity.ScheduleCategory
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Size
import java.time.LocalDateTime

data class ScheduleEventRequest(
    @field:NotBlank
    @field:Size(max = 100)
    val title: String,

    @field:NotNull
    val category: ScheduleCategory,

    @field:NotNull
    val startAt: LocalDateTime,

    @field:NotNull
    val endAt: LocalDateTime,

    // Boolean(원시 타입)이면 JSON에 키가 없을 때 Jackson이 예외 대신 조용히 false로 채워서 @field:NotNull이
    // 무력화됨(PhotoVisibilityUpdateRequest.isPublic과 동일한 함정) — Boolean?로 열고 Bean Validation이 null을 직접 잡게 함
    @field:NotNull
    val allDay: Boolean?,

    @field:Size(max = 500)
    val memo: String? = null
) {
    @AssertTrue(message = "종료 일시는 시작 일시보다 빠를 수 없습니다.")
    fun isEndAtValid(): Boolean = !endAt.isBefore(startAt)
}
