package com.ourspots.domain.photo.controller

import com.ourspots.api.dto.PhotoConfirmRequest
import com.ourspots.api.dto.PhotoPresignRequest
import com.ourspots.api.dto.PhotoPresignResponse
import com.ourspots.api.dto.PhotoResponse
import com.ourspots.api.dto.PhotoVisibilityUpdateRequest
import com.ourspots.common.response.ApiResponse
import com.ourspots.domain.photo.service.PhotoService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 전부 관리자 전용(AdminOnlyInterceptor, WebMvcConfig 참고) — 사진 조회는 별도 GET 없이 PlaceResponse/ScheduleEventResponse에
// 이미 포함돼서 내려가므로(공개 조회는 그쪽 엔드포인트가 처리), 이 컨트롤러는 업로드/삭제 관리만 담당
@RestController
@RequestMapping("/api/photos")
class PhotoController(
    private val photoService: PhotoService
) {
    @PostMapping("/presign")
    fun presign(@Valid @RequestBody request: PhotoPresignRequest): ApiResponse<PhotoPresignResponse> =
        ApiResponse.success(photoService.presign(request))

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.CREATED)
    fun confirm(@Valid @RequestBody request: PhotoConfirmRequest): ApiResponse<PhotoResponse> =
        ApiResponse.success(photoService.confirm(request))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) {
        photoService.delete(id)
    }

    // "등록 사진 이력" 화면에서 공개/비공개 전환용
    @PatchMapping("/{id}")
    fun updateVisibility(
        @PathVariable id: Long,
        @Valid @RequestBody request: PhotoVisibilityUpdateRequest
    ): ApiResponse<PhotoResponse> =
        // @field:NotNull 검증을 이미 통과했으므로 이 시점엔 항상 non-null
        ApiResponse.success(photoService.updateVisibility(id, request.isPublic!!))
}
