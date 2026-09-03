package com.ourspots.domain.schedule.entity

import com.ourspots.common.util.SoftDeletable
import jakarta.persistence.*
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

@Entity
@Table(name = "schedule_events")
@SQLDelete(sql = "UPDATE schedule_events SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
class ScheduleEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 100)
    var title: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var category: ScheduleCategory,

    @Column(nullable = false)
    var startAt: LocalDateTime,

    // all-day 일정도 endAt으로 마지막 날짜를 표현(자정 기준) — 시작일만 있는 단일 일정은 startAt과 동일
    @Column(nullable = false)
    var endAt: LocalDateTime,

    @Column(nullable = false)
    var allDay: Boolean,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now(),

    @Column(nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now(),

    override var deletedAt: LocalDateTime? = null
) : SoftDeletable {
    @PreUpdate
    fun onUpdate() {
        updatedAt = LocalDateTime.now()
    }
}
