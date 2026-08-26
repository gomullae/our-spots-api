package com.ourspots.domain.schedule.repository

import com.ourspots.domain.schedule.entity.ScheduleEvent
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

interface ScheduleEventRepository : JpaRepository<ScheduleEvent, Long> {
    // 캘린더 화면에 보이는 기간과 "겹치는" 일정 전부 조회 — 여러 날에 걸친 일정이 기간 경계 밖에서 시작/끝나도 놓치지 않기 위함
    @Query(
        value = "SELECT * FROM schedule_events WHERE start_at <= :end AND end_at >= :start AND (:includeDeleted = true OR deleted_at IS NULL) ORDER BY start_at",
        nativeQuery = true
    )
    fun findOverlapping(start: LocalDateTime, end: LocalDateTime, includeDeleted: Boolean): List<ScheduleEvent>

    // 복구 대상은 소프트 삭제된 것도 조회할 수 있어야 하므로 네이티브 쿼리 사용
    @Query("SELECT * FROM schedule_events WHERE id = :id", nativeQuery = true)
    fun findByIdIncludingDeleted(id: Long): ScheduleEvent?

    // 프론트 캐시 검증(/api/schedules/meta)용 — JPQL이라 @SQLRestriction(deleted_at IS NULL)이 자동 적용됨
    @Query("SELECT MAX(e.updatedAt) FROM ScheduleEvent e")
    fun findMaxUpdatedAt(): LocalDateTime?

    // 데이터 백업용 — 소프트 삭제된 것도 포함해서 전체 조회해야 하므로 네이티브 쿼리 사용
    @Query("SELECT * FROM schedule_events ORDER BY id", nativeQuery = true)
    fun findAllIncludingDeleted(): List<ScheduleEvent>
}
