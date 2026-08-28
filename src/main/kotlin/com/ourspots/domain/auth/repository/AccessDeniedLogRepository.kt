package com.ourspots.domain.auth.repository

import com.ourspots.domain.auth.entity.AccessDeniedLog
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface AccessDeniedLogRepository : JpaRepository<AccessDeniedLog, Long> {
    // 백업/로그 이력 "최근 3개월" 조회용 — LoginAttemptRepository.findAllSince()와 동일한 이유로 명시적 JPQL
    @Query("SELECT a FROM AccessDeniedLog a WHERE a.createdAt >= :cutoff ORDER BY a.id DESC")
    fun findAllSince(cutoff: LocalDateTime): List<AccessDeniedLog>
}
