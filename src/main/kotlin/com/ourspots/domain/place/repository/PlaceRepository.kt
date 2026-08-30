package com.ourspots.domain.place.repository

import com.ourspots.domain.place.entity.Place
import com.ourspots.domain.place.entity.PlaceType
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.time.LocalDateTime

// keyword는 호출부(PlaceService)에서 %,_,\ 를 이스케이프해서 전달 — LIKE 패턴 인젝션 방지
private const val SEARCH_RECENT_PLACES_WHERE = """
    WHERE created_at >= :start AND created_at < :end
    AND (:includeDeleted = true OR deleted_at IS NULL)
    AND (
        :keyword IS NULL
        OR LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\'
        OR LOWER(address) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\'
    )
    AND (:type IS NULL OR type = :type)
    AND (:grade IS NULL OR grade = :grade)
"""

// searchRecentPlacesByUpdatedAt 전용 — 정렬 기준(수정일시)에 맞춰 기간 필터의 기준 컬럼도 updated_at으로
// 통일함("최근 3개월"이 수정일시 기준 최근 3개월을 의미하게 하기 위함 — 등록일시 기준으로 두면 최근에
// 수정됐지만 오래 전에 등록된 장소가 조회 범위 밖으로 빠져서, 정렬 기준과 기간 필터 기준이 어긋나 혼란스러웠음.
// 2026-08-30 "수정일시순인데 최근 3개월 등록분 안에서만 재정렬되는 것 같다"는 피드백으로 수정) —
// SEARCH_RECENT_PLACES_WHERE와 날짜 컬럼만 다르고 나머지 조건은 동일
private const val SEARCH_RECENT_PLACES_WHERE_BY_UPDATED_AT = """
    WHERE updated_at >= :start AND updated_at < :end
    AND (:includeDeleted = true OR deleted_at IS NULL)
    AND (
        :keyword IS NULL
        OR LOWER(name) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\'
        OR LOWER(address) LIKE LOWER(CONCAT('%', :keyword, '%')) ESCAPE '\'
    )
    AND (:type IS NULL OR type = :type)
    AND (:grade IS NULL OR grade = :grade)
"""

interface PlaceRepository : JpaRepository<Place, Long> {

    fun findByType(type: PlaceType): List<Place>

    fun existsByNameAndAddress(name: String, address: String): Boolean

    @Query("""
        SELECT p FROM Place p
        WHERE p.latitude BETWEEN :swLat AND :neLat
        AND p.longitude BETWEEN :swLng AND :neLng
    """)
    fun findWithinBounds(swLat: Double, swLng: Double, neLat: Double, neLng: Double): List<Place>

    // 비로그인 사용자용 마커 조회 전용 — 개인 카테고리 제외 + (선택적) 타입/범위 필터에 더해
    // 공개 타입 전부 1등급만 노출(2026-08-30 이전엔 맛집만 1등급 제한이었는데, "전부 1등급으로 통일하자"는
    // 요청으로 확장 — type NOT IN (:personalTypes)로 이미 공개 타입만 남은 상태라 grade=1만 걸면 됨).
    // bounds/type 유무 조합이 여러 겹이라 리포지토리 메서드를 따로 두는 대신 하나의 네이티브 쿼리로 통합해서 처리
    @Query(
        value = """
            SELECT * FROM places
            WHERE type NOT IN (:personalTypes)
            AND (:type IS NULL OR type = :type)
            AND (:swLat IS NULL OR (latitude BETWEEN :swLat AND :neLat AND longitude BETWEEN :swLng AND :neLng))
            AND grade = 1
        """,
        nativeQuery = true
    )
    fun findPublicMarkers(
        personalTypes: List<String>,
        type: String?,
        swLat: Double?,
        swLng: Double?,
        neLat: Double?,
        neLng: Double?
    ): List<Place>

    @Query("""
        SELECT p FROM Place p
        WHERE p.type = :type
        AND p.latitude BETWEEN :swLat AND :neLat
        AND p.longitude BETWEEN :swLng AND :neLng
    """)
    fun findByTypeWithinBounds(
        type: PlaceType,
        swLat: Double,
        swLng: Double,
        neLat: Double,
        neLng: Double
    ): List<Place>

    @Query("""
        SELECT p FROM Place p
        WHERE p.googleRatingFailCount < :maxFailCount
        AND (
            p.googleRating IS NULL
            OR p.googleRatingUpdatedAt IS NULL
            OR p.googleRatingUpdatedAt < :cutoffDate
        )
    """)
    fun findPlacesEligibleForGoogleSync(
        maxFailCount: Int,
        cutoffDate: LocalDateTime,
        pageable: Pageable
    ): List<Place>

    @Query("SELECT * FROM places ORDER BY id", nativeQuery = true)
    fun findAllIncludingDeleted(): List<Place>

    // 백업/로그 이력 화면의 "최근 3개월" 조회용 — findAllIncludingDeleted()로 전체를 퍼온 뒤 코드에서 거르면
    // login_attempts/error_logs/access_denied_logs처럼 정리 배치 없이 계속 누적되는 테이블이 나중에 커졌을 때
    // 매번 테이블 전체를 메모리에 올리게 됨 → DB 단에서 먼저 걸러서 필요한 만큼만 가져오도록 분리
    @Query("SELECT * FROM places WHERE created_at >= :cutoff ORDER BY id", nativeQuery = true)
    fun findAllIncludingDeletedSince(cutoff: LocalDateTime): List<Place>

    // @SQLRestriction("deleted_at IS NULL")은 네이티브 쿼리에는 적용되지 않음 → id로 삭제된 장소도 조회 가능 (복구용)
    @Query("SELECT * FROM places WHERE id = :id", nativeQuery = true)
    fun findByIdIncludingDeleted(id: Long): Place?

    // 관리자 "최근 등록 장소" 화면 전용 검색 — 기간/키워드/유형/등급 + 삭제 포함 여부까지 한 번에 필터링
    // type은 @Enumerated(STRING) 컬럼이라 네이티브 쿼리에서는 String으로 바인딩 (호출부에서 PlaceType.name 전달)
    @Query(
        value = "SELECT * FROM places $SEARCH_RECENT_PLACES_WHERE ORDER BY created_at DESC",
        countQuery = "SELECT count(*) FROM places $SEARCH_RECENT_PLACES_WHERE",
        nativeQuery = true
    )
    fun searchRecentPlaces(
        start: LocalDateTime,
        end: LocalDateTime,
        keyword: String?,
        type: String?,
        grade: Int?,
        includeDeleted: Boolean,
        pageable: Pageable
    ): Page<Place>

    // searchRecentPlaces와 정렬 기준(수정일시 내림차순)뿐 아니라 기간 필터 기준 컬럼도 다름 —
    // SEARCH_RECENT_PLACES_WHERE_BY_UPDATED_AT 참고
    @Query(
        value = "SELECT * FROM places $SEARCH_RECENT_PLACES_WHERE_BY_UPDATED_AT ORDER BY updated_at DESC",
        countQuery = "SELECT count(*) FROM places $SEARCH_RECENT_PLACES_WHERE_BY_UPDATED_AT",
        nativeQuery = true
    )
    fun searchRecentPlacesByUpdatedAt(
        start: LocalDateTime,
        end: LocalDateTime,
        keyword: String?,
        type: String?,
        grade: Int?,
        includeDeleted: Boolean,
        pageable: Pageable
    ): Page<Place>
}
