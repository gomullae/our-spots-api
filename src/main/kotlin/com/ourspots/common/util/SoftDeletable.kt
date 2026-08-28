package com.ourspots.common.util

import com.ourspots.common.exception.NotFoundException
import java.time.LocalDateTime

// Place/ExpenseRecord/ScheduleEvent가 공통으로 구현 — "복구(restore)"를 공용 함수로 뽑기 위한 최소 계약
interface SoftDeletable {
    var deletedAt: LocalDateTime?
}

// Place/Expense/Schedule 세 서비스에 각자 구현돼있던 "삭제된 것도 포함해서 조회 → deletedAt null로 리셋 → 저장" 복구 로직 통일
fun <T : SoftDeletable> restoreSoftDeleted(id: Long, entityName: String, findIncludingDeleted: (Long) -> T?, save: (T) -> T): T {
    val entity = findIncludingDeleted(id) ?: throw NotFoundException("$entityName not found: $id")
    entity.deletedAt = null
    return save(entity)
}
