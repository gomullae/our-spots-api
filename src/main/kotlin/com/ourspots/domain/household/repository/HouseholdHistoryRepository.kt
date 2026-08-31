package com.ourspots.domain.household.repository

import com.ourspots.domain.household.entity.HouseholdHistory
import com.ourspots.domain.household.entity.HouseholdHistoryItemType
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface HouseholdHistoryRepository : JpaRepository<HouseholdHistory, Long> {

    // itemType/itemId가 "item" 접두사를 공유해서 파생 쿼리 파서가 잘못 해석할 위험이 있는 패턴
    // (PhotoRepository의 entityType/entityId 사례 참고) — 명시적 JPQL로 고정
    @Query("SELECT h FROM HouseholdHistory h WHERE h.itemType = :itemType AND h.itemId = :itemId ORDER BY h.createdAt DESC")
    fun findByItemTypeAndItemId(itemType: HouseholdHistoryItemType, itemId: Long): List<HouseholdHistory>
}
