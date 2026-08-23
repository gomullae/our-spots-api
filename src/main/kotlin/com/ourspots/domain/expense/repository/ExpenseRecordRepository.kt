package com.ourspots.domain.expense.repository

import com.ourspots.domain.expense.entity.ExpenseRecord
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDate

interface ExpenseRecordRepository : JpaRepository<ExpenseRecord, Long> {
    // @SQLRestriction("deleted_at IS NULL")은 네이티브 쿼리에는 적용되지 않음 → includeDeleted=true로 소프트 삭제된 것도 조회 가능("이력" 탭용)
    @Query(
        value = "SELECT * FROM expense_records WHERE expense_date BETWEEN :start AND :end AND (:includeDeleted = true OR deleted_at IS NULL) ORDER BY expense_date DESC",
        nativeQuery = true
    )
    fun findByExpenseDateBetween(start: LocalDate, end: LocalDate, includeDeleted: Boolean): List<ExpenseRecord>

    // 복구 대상은 소프트 삭제된 것도 조회할 수 있어야 하므로 네이티브 쿼리 사용
    @Query("SELECT * FROM expense_records WHERE id = :id", nativeQuery = true)
    fun findByIdIncludingDeleted(id: Long): ExpenseRecord?

    // 백업 다운로드용 — PlaceRepository.findAllIncludingDeleted()와 동일 패턴
    @Query("SELECT * FROM expense_records ORDER BY id", nativeQuery = true)
    fun findAllIncludingDeleted(): List<ExpenseRecord>
}
