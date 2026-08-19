package com.ourspots.api.dto

import com.ourspots.domain.weight.entity.WeightRecord
import java.time.LocalDate
import java.time.LocalDateTime

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
