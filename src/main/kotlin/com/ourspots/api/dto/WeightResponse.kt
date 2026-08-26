package com.ourspots.api.dto

import com.ourspots.domain.weight.entity.WeightRecord
import java.time.LocalDate
import java.time.LocalDateTime

// 프론트 로컬 캐시 검증용 — count/lastModified 조합으로 전체 목록을 안 내려받고도 변경 여부만 가볍게 확인
data class WeightMetaResponse(
    val count: Long,
    val lastModified: LocalDateTime?
)

data class WeightRecordResponse(
    val id: Long,
    val recordedDate: LocalDate,
    val weightKg: Double,
    val memo: String?,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
) {
    companion object {
        fun from(record: WeightRecord) = WeightRecordResponse(
            id = record.id,
            recordedDate = record.recordedDate,
            weightKg = record.weightKg,
            memo = record.memo,
            createdAt = record.createdAt,
            updatedAt = record.updatedAt
        )
    }
}
