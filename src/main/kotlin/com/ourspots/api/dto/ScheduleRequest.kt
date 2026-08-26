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

    @field:NotNull
    val allDay: Boolean,

    @field:Size(max = 500)
    val memo: String? = null
) {
    @AssertTrue(message = "종료 일시는 시작 일시보다 빠를 수 없습니다.")
    fun isEndAtValid(): Boolean = !endAt.isBefore(startAt)
}
