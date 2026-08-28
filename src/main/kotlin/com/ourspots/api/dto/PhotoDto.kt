package com.ourspots.api.dto

import com.ourspots.domain.photo.entity.Photo
import com.ourspots.domain.photo.entity.PhotoEntityType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import jakarta.validation.constraints.Positive

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
    val displayOrder: Int
) {
    companion object {
        fun from(photo: Photo) = PhotoResponse(
            id = photo.id,
            url = photo.url,
            // 이 기능 추가 전에 업로드된 사진은 thumbnailUrl이 빈 문자열이라 프론트가 원본(url)으로 대체 표시함
            thumbnailUrl = photo.thumbnailUrl,
            displayOrder = photo.displayOrder
        )
    }
}
