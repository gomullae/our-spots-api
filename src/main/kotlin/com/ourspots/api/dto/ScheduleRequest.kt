package com.ourspots.api.dto

import com.ourspots.domain.schedule.entity.ScheduleCategory
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min
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

    // 이 저장(등록/수정)에 새로 딸려 들어오는(아직 confirm 안 된) 사진 개수 — DB에 저장되는 값이 아니라
    // 텔레그램 알림에 "사진 N장 추가됨" 한 줄을 넣기 위한 신호. 프론트가 이벤트 저장 성공 후 그 개수만큼
    // confirm()을 반복 호출하는 흐름은 그대로고, 그 호출들과 별개로 이 저장 요청 자체에도 개수를 실어 보냄
    // (사진마다 알림이 따로 나가는 스팸을 피하려고 이벤트 저장 알림 하나에 묶음)
    @field:Min(0)
    val newPhotoCount: Int = 0
) {
    @AssertTrue(message = "종료 일시는 시작 일시보다 빠를 수 없습니다.")
    fun isEndAtValid(): Boolean = !endAt.isBefore(startAt)
}

data class ScheduleMemoRequest(
    @field:NotBlank
    @field:Size(max = 500)
    val content: String
)

// 상세보기(ScheduleEventDetail)에서 붙여넣기로 사진을 바로 추가한 직후, 프론트가 그 배치의 성공 개수를
// 실어 보내 텔레그램 알림만 트리거하는 용도 — 사진 자체는 이미 /api/photos/confirm으로 저장 완료된 상태
data class SchedulePhotoAddedRequest(
    @field:Min(1)
    val count: Int
)
