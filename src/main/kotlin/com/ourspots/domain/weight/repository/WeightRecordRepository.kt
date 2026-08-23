package com.ourspots.domain.weight.repository

import com.ourspots.domain.weight.entity.WeightRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface WeightRecordRepository : JpaRepository<WeightRecord, Long> {
    fun findByRecordedDate(recordedDate: LocalDate): WeightRecord?
    fun findAllByOrderByRecordedDateDesc(): List<WeightRecord>

    // 백업 다운로드용 — PlaceRepository.findAllIncludingDeleted()와 동일 패턴
    @Query("SELECT * FROM weight_records ORDER BY id", nativeQuery = true)
    fun findAllIncludingDeleted(): List<WeightRecord>
}
