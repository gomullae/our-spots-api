package com.ourspots.api.dto

import com.ourspots.domain.photo.entity.Photo
import com.ourspots.domain.photo.entity.PhotoEntityType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive
import java.time.LocalDateTime

data class PhotoPresignRequest(
    val entityType: PhotoEntityType,
    // 업로드 가능한 형식을 화이트리스트로 제한 — PhotoService.EXTENSION_BY_CONTENT_TYPE와 동일한 목록
    @field:Pattern(regexp = "^image/(jpeg|png|webp|gif)$", message = "지원하지 않는 이미지 형식입니다.")
    val contentType: String
)

data class PhotoPresignResponse(
    val uploadUrl: String,
    val objectKey: String,
    val publicUrl: String
)

data class PhotoConfirmRequest(
    val entityType: PhotoEntityType,
    @field:Positive val entityId: Long,
    @field:NotBlank val objectKey: String,
    @field:NotBlank val thumbnailObjectKey: String
)

data class PhotoResponse(
    val id: Long,
    val url: String,
    val thumbnailUrl: String,
    val displayOrder: Int,
    val isPublic: Boolean
) {
    companion object {
        fun from(photo: Photo) = PhotoResponse(
            id = photo.id,
            url = photo.url,
            // 이 기능 추가 전에 업로드된 사진은 thumbnailUrl이 빈 문자열이라 프론트가 원본(url)으로 대체 표시함
            thumbnailUrl = photo.thumbnailUrl,
            displayOrder = photo.displayOrder,
            isPublic = photo.isPublic
        )
    }
}

// isPublic이 원시 Boolean(비-null)이면 JSON에 키 자체가 없을 때 Jackson이 예외 대신 조용히 false로
// 채워버려서(원시 타입이라 "없음"을 표현 못 함) @field:NotNull이 무력화됨 — Boolean?로 열어두고
// Bean Validation이 null을 직접 잡게 함(컨트롤러에서 검증 통과 후 request.isPublic!!로 사용)
data class PhotoVisibilityUpdateRequest(
    @field:NotNull val isPublic: Boolean?
)

// 관리자 "등록 사진 이력" 화면 전용 — Photo는 Place와 FK 없이 연결돼있어서 장소명을 PlaceService가
// 별도로 조회해서 채워줌(PhotoResponse와 분리한 이유)
data class PhotoAdminResponse(
    val id: Long,
    val placeId: Long,
    val placeName: String,
    val url: String,
    val thumbnailUrl: String,
    val isPublic: Boolean,
    val createdAt: LocalDateTime
) {
    companion object {
        fun from(photo: Photo, placeName: String) = PhotoAdminResponse(
            id = photo.id,
            placeId = photo.entityId,
            placeName = placeName,
            url = photo.url,
            thumbnailUrl = photo.thumbnailUrl,
            isPublic = photo.isPublic,
            createdAt = photo.createdAt
        )
    }
}
