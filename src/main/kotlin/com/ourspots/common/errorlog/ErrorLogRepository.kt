package com.ourspots.common.errorlog

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface ErrorLogRepository : JpaRepository<ErrorLog, Long> {
    // 백업/로그 이력 "최근 3개월" 조회용 — LoginAttemptRepository.findAllSince()와 동일한 이유로 명시적 JPQL
    @Query("SELECT e FROM ErrorLog e WHERE e.createdAt >= :cutoff ORDER BY e.id DESC")
    fun findAllSince(cutoff: LocalDateTime): List<ErrorLog>
}
