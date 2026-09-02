package com.ourspots.domain.household.repository

import com.ourspots.domain.household.entity.HouseholdHistory
import com.ourspots.domain.household.entity.HouseholdHistoryItemType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface HouseholdHistoryRepository : JpaRepository<HouseholdHistory, Long> {

    // itemType/itemId가 "item" 접두사를 공유해서 파생 쿼리 파서가 잘못 해석할 위험이 있는 패턴
    // (PhotoRepository의 entityType/entityId 사례 참고) — 명시적 JPQL로 고정
    @Query("SELECT h FROM HouseholdHistory h WHERE h.itemType = :itemType AND h.itemId = :itemId ORDER BY h.createdAt DESC")
    fun findByItemTypeAndItemId(itemType: HouseholdHistoryItemType, itemId: Long): List<HouseholdHistory>

    // 백업 "최근 3개월" 조회용 — append-only 로그라 소프트 삭제 개념이 없어서 LoginAttemptRepository.findAllSince()와 동일 패턴
    @Query("SELECT h FROM HouseholdHistory h WHERE h.createdAt >= :cutoff ORDER BY h.id DESC")
    fun findAllSince(cutoff: LocalDateTime): List<HouseholdHistory>
}
