package com.ourspots.domain.photo.repository

import com.ourspots.domain.photo.entity.Photo
import com.ourspots.domain.photo.entity.PhotoEntityType
import org.springframework.data.jpa.repository.JpaRepository

interface PhotoRepository : JpaRepository<Photo, Long> {
    fun findByEntityTypeAndEntityIdOrderByDisplayOrderAscIdAsc(entityType: PhotoEntityType, entityId: Long): List<Photo>

    // 장소 목록처럼 여러 건을 한 번에 조회할 때 건당 쿼리를 날리지 않기 위한 벌크 조회(N+1 방지)
    fun findByEntityTypeAndEntityIdInOrderByDisplayOrderAscIdAsc(entityType: PhotoEntityType, entityIds: Collection<Long>): List<Photo>
}
