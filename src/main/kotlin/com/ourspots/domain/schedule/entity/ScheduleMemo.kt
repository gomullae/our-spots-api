package com.ourspots.domain.schedule.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

// 일정 하나에 여러 개 붙는 메모(스레드형) — schedule_events.memo(단일 문자열) 필드를 대체(2026-09-03~).
// 수정은 없고 추가/삭제만 있는 append-only라 updatedAt 없음. 복구 UI는 없음(가벼운 텍스트라 필요하면 나중에 DB에서 직접 처리)
@Entity
@Table(name = "schedule_memos")
@SQLDelete(sql = "UPDATE schedule_memos SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
class ScheduleMemo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(name = "schedule_event_id", nullable = false)
    val scheduleEventId: Long,

    @Column(nullable = false, length = 500)
    var content: String,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    var deletedAt: LocalDateTime? = null
)
