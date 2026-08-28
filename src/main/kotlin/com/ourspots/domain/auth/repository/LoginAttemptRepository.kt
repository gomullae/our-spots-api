package com.ourspots.domain.auth.repository

import com.ourspots.domain.auth.entity.LoginAttempt
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface LoginAttemptRepository : JpaRepository<LoginAttempt, Long> {
    // 백업/로그 이력 "최근 3개월" 조회용 — 정리 배치 없이 계속 누적되는 테이블이라 findAll()로 전체를 퍼온 뒤
    // 코드에서 거르지 않고 DB 단에서 먼저 걸러서 필요한 만큼만 가져옴. 메서드명 기반 파생 쿼리에서 파서가
    // 프로퍼티 이름을 잘못 해석해 마커 API가 깨진 적이 있어서(PhotoRepository 참고) 명시적 JPQL로 작성
    @Query("SELECT l FROM LoginAttempt l WHERE l.createdAt >= :cutoff ORDER BY l.id DESC")
    fun findAllSince(cutoff: LocalDateTime): List<LoginAttempt>
}
