package com.ourspots.domain.weight.repository

import com.ourspots.domain.weight.entity.WeightRecord
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface WeightRecordRepository : JpaRepository<WeightRecord, Long> {
    fun findByRecordedDate(recordedDate: LocalDate): WeightRecord?
    fun findAllByOrderByRecordedDateDesc(): List<WeightRecord>
}
