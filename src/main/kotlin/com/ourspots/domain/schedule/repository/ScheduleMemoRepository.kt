package com.ourspots.domain.schedule.repository

import com.ourspots.domain.schedule.entity.ScheduleMemo
import org.springframework.data.jpa.repository.JpaRepository

interface ScheduleMemoRepository : JpaRepository<ScheduleMemo, Long> {
    // 일정 상세에 보여줄 메모 목록 — 오래된 순(등록 순)
    fun findByScheduleEventIdOrderByCreatedAtAsc(scheduleEventId: Long): List<ScheduleMemo>

    // 캘린더 화면에서 여러 일정을 한 번에 그릴 때 건당 쿼리를 피하기 위한 벌크 조회
    fun findByScheduleEventIdInOrderByCreatedAtAsc(scheduleEventIds: Collection<Long>): List<ScheduleMemo>

    // 개수 상한(10개) 체크용 — @SQLRestriction이 derived query에도 적용되므로 활성 메모만 셈
    fun countByScheduleEventId(scheduleEventId: Long): Long
}
