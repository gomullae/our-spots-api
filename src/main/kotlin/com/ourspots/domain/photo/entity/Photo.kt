package com.ourspots.domain.photo.entity

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.SQLDelete
import org.hibernate.annotations.SQLRestriction
import java.time.LocalDateTime

// 장소/일정 등 여러 도메인이 공용으로 쓰는 사진 테이블 — FK 없이 entityType+entityId로 연결(이 프로젝트의 "FK 없는 독립 테이블" 관례)
enum class PhotoEntityType {
    PLACE,
    SCHEDULE_EVENT
}

@Entity
@Table(name = "photos")
// 소프트 삭제 — 실수/버그로 지워져도 DB 행과 R2 파일 둘 다 즉시 사라지지 않게 함(다른 엔티티와 동일한 관례).
// PhotoService.delete()도 R2 파일은 지우지 않고 이 UPDATE만 타도록 되어있음
@SQLDelete(sql = "UPDATE photos SET deleted_at = NOW() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
class Photo(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    var entityType: PhotoEntityType,

    var entityId: Long,

    var objectKey: String,

    var url: String,

    // 목록/썸네일용 축소본 — 라이트박스(원본)와 분리해서 목록 화면 로딩을 가볍게 함
    var thumbnailObjectKey: String,

    var thumbnailUrl: String,

    var displayOrder: Int = 0,

    val createdAt: LocalDateTime = LocalDateTime.now(),

    var deletedAt: LocalDateTime? = null
)
