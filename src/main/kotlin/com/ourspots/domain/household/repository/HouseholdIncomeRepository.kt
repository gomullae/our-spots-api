package com.ourspots.domain.household.repository

import com.ourspots.domain.household.entity.HouseholdIncome
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface HouseholdIncomeRepository : JpaRepository<HouseholdIncome, Long> {

    @Query("SELECT * FROM household_incomes WHERE id = :id", nativeQuery = true)
    fun findByIdIncludingDeleted(id: Long): HouseholdIncome?

    @Query(
        value = "SELECT * FROM household_incomes WHERE (:includeDeleted = true OR deleted_at IS NULL) ORDER BY id",
        nativeQuery = true
    )
    fun findAllForDashboard(includeDeleted: Boolean): List<HouseholdIncome>

    // 프론트 캐시 검증(/api/household-budget/meta)용 — JPQL이라 @SQLRestriction(deleted_at IS NULL)이 자동 적용됨
    @Query("SELECT MAX(i.updatedAt) FROM HouseholdIncome i")
    fun findMaxUpdatedAt(): LocalDateTime?
}
