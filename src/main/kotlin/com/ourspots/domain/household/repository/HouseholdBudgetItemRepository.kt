package com.ourspots.domain.household.repository

import com.ourspots.domain.household.entity.HouseholdBudgetItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface HouseholdBudgetItemRepository : JpaRepository<HouseholdBudgetItem, Long> {

    // @SQLRestriction("deleted_at IS NULL")은 네이티브 쿼리에는 적용되지 않음 → id로 삭제된 항목도 조회 가능(복구용)
    @Query("SELECT * FROM household_budget_items WHERE id = :id", nativeQuery = true)
    fun findByIdIncludingDeleted(id: Long): HouseholdBudgetItem?

    // 대시보드 전체 조회 — includeDeleted=false(기본)면 활성 항목만, true면 삭제된 것도 포함(복구 UI용)
    @Query(
        value = "SELECT * FROM household_budget_items WHERE (:includeDeleted = true OR deleted_at IS NULL) ORDER BY section_type, id",
        nativeQuery = true
    )
    fun findAllForDashboard(includeDeleted: Boolean): List<HouseholdBudgetItem>

    // 프론트 캐시 검증(/api/household-budget/meta)용 — JPQL이라 @SQLRestriction(deleted_at IS NULL)이 자동 적용됨
    @Query("SELECT MAX(i.updatedAt) FROM HouseholdBudgetItem i")
    fun findMaxUpdatedAt(): LocalDateTime?
}
