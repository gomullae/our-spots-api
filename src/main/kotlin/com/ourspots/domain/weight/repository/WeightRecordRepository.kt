package com.ourspots.domain.weight.repository

import com.ourspots.domain.weight.entity.WeightRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate
import java.time.LocalDateTime

interface WeightRecordRepository : JpaRepository<WeightRecord, Long> {
    fun findByRecordedDate(recordedDate: LocalDate): WeightRecord?
    fun findAllByOrderByRecordedDateDesc(): List<WeightRecord>

    // 백업 다운로드용 — PlaceRepository.findAllIncludingDeleted()와 동일 패턴
    @Query("SELECT * FROM weight_records ORDER BY id", nativeQuery = true)
    fun findAllIncludingDeleted(): List<WeightRecord>

    // 백업/로그 이력 "최근 3개월" 조회용 — PlaceRepository.findAllIncludingDeletedSince()와 동일 패턴
    @Query("SELECT * FROM weight_records WHERE created_at >= :cutoff ORDER BY id", nativeQuery = true)
    fun findAllIncludingDeletedSince(cutoff: LocalDateTime): List<WeightRecord>

    // 프론트 캐시 검증(/api/weights/meta)용 — JPQL이라 @SQLRestriction(deleted_at IS NULL)이 자동 적용됨
    @Query("SELECT MAX(w.updatedAt) FROM WeightRecord w")
    fun findMaxUpdatedAt(): LocalDateTime?
}
