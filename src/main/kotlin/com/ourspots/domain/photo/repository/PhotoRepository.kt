package com.ourspots.domain.photo.repository

import com.ourspots.domain.photo.entity.Photo
import com.ourspots.domain.photo.entity.PhotoEntityType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface PhotoRepository : JpaRepository<Photo, Long> {
    fun findByEntityTypeAndEntityIdOrderByDisplayOrderAscIdAsc(entityType: PhotoEntityType, entityId: Long): List<Photo>

    // 장소 목록처럼 여러 건을 한 번에 조회할 때 건당 쿼리를 날리지 않기 위한 벌크 조회(N+1 방지)
    fun findByEntityTypeAndEntityIdInOrderByDisplayOrderAscIdAsc(entityType: PhotoEntityType, entityIds: Collection<Long>): List<Photo>

    // 마커처럼 사진 URL 등은 필요 없이 "있냐/없냐"만 필요한 경우용 — entityId만 뽑아서 불필요한 컬럼 조회를 피함.
    // 메서드명 기반 파생 쿼리(findDistinctEntityIdBy...)로 했다가 "EntityId"와 "EntityType"이 Entity 접두사를
    // 공유해서 파서가 잘못 해석해 런타임에 Photo 엔티티 전체를 선택하는 쿼리로 깨짐(JpaSystemException) —
    // 명시적 JPQL로 바꿔서 entityId 하나만 확실하게 선택하도록 고정
    @Query("SELECT DISTINCT p.entityId FROM Photo p WHERE p.entityType = :entityType AND p.entityId IN :entityIds")
    fun findDistinctEntityIdByEntityTypeAndEntityIdIn(entityType: PhotoEntityType, entityIds: Collection<Long>): Set<Long>

    // 마커 배지를 "사진 있음(회색)"과 "공개 사진 있음(진하게)"으로 구분하기 위한 전용 쿼리 —
    // 위 findDistinctEntityIdByEntityTypeAndEntityIdIn과 동일한 이유로 명시적 JPQL 사용
    @Query("SELECT DISTINCT p.entityId FROM Photo p WHERE p.entityType = :entityType AND p.entityId IN :entityIds AND p.isPublic = true")
    fun findDistinctEntityIdByEntityTypeAndEntityIdInAndIsPublicTrue(entityType: PhotoEntityType, entityIds: Collection<Long>): Set<Long>

    // 관리자 "등록 사진 이력" 화면 전용 — entityType은 호출부(PhotoService)가 항상 PLACE로 고정해서 넘김,
    // isPublic은 null이면 전체/true면 공개/false면 비공개. 네이티브 쿼리라 entityType도 String으로 바인딩
    @Query(
        value = "SELECT * FROM photos WHERE entity_type = :entityType AND (:isPublic IS NULL OR is_public = :isPublic) ORDER BY created_at DESC",
        countQuery = "SELECT count(*) FROM photos WHERE entity_type = :entityType AND (:isPublic IS NULL OR is_public = :isPublic)",
        nativeQuery = true
    )
    fun findByEntityTypeAndIsPublicOptional(entityType: String, isPublic: Boolean?, pageable: Pageable): Page<Photo>
}
