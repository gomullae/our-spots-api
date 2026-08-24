package com.ourspots.api.dto

import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.PastOrPresent
import jakarta.validation.constraints.Size
import java.time.LocalDate

data class WeightRecordUpsertRequest(
    @field:PastOrPresent
    val recordedDate: LocalDate? = null,

    @field:NotNull
    @field:DecimalMin("20.0")
    @field:DecimalMax("300.0")
    val weightKg: Double,

    @field:Size(max = 200)
    val memo: String? = null
)
