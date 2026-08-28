package com.ourspots.domain.feedback.repository

import com.ourspots.domain.feedback.entity.Feedback
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.OffsetDateTime

interface FeedbackRepository : JpaRepository<Feedback, Long> {
    // 백업/로그 이력 "최근 3개월" 조회용 — LoginAttemptRepository.findAllSince()와 동일한 이유로 명시적 JPQL
    @Query("SELECT f FROM Feedback f WHERE f.createdAt >= :cutoff ORDER BY f.id DESC")
    fun findAllSince(cutoff: OffsetDateTime): List<Feedback>
}
