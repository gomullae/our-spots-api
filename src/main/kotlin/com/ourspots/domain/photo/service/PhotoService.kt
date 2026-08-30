package com.ourspots.domain.photo.service

import com.ourspots.api.dto.PhotoConfirmRequest
import com.ourspots.api.dto.PhotoPresignRequest
import com.ourspots.api.dto.PhotoPresignResponse
import com.ourspots.api.dto.PhotoResponse
import com.ourspots.common.util.findByIdOrThrow
import com.ourspots.domain.photo.entity.Photo
import com.ourspots.domain.photo.entity.PhotoEntityType
import com.ourspots.domain.photo.repository.PhotoRepository
import com.ourspots.domain.schedule.repository.ScheduleEventRepository
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.util.UUID

@Service
@Transactional(readOnly = true)
class PhotoService(
    private val photoRepository: PhotoRepository,
    // 사진 추가/삭제 시 딸린 일정의 updatedAt을 같이 갱신하기 위함 — 아래 touchParentIfNeeded() 참고
    private val scheduleEventRepository: ScheduleEventRepository,
    private val s3Presigner: S3Presigner,
    @Value("\${app.r2.bucket-name}") private val bucketName: String,
    // 끝에 슬래시가 붙어 들어와도(설정 실수) 안전하게 처리하기 위해 trimEnd
    @Value("\${app.r2.public-url}") private val publicUrlProperty: String
) {
    private val publicUrl: String get() = publicUrlProperty.trimEnd('/')

    companion object {
        // presigned PUT URL 유효 시간 — 업로드는 보통 몇 초 안에 끝나므로 넉넉히 잡음
        private val PRESIGN_EXPIRY: Duration = Duration.ofMinutes(10)

        // PhotoPresignRequest의 @Pattern 화이트리스트와 동일한 목록
        private val EXTENSION_BY_CONTENT_TYPE = mapOf(
            "image/jpeg" to "jpg",
            "image/png" to "png",
            "image/webp" to "webp",
            "image/gif" to "gif"
        )
    }

    // 아직 어느 장소/일정에 속할지 확정되기 전(신규 등록 폼에서 저장 전 미리 붙여넣기)이므로 entityId 없이 R2에만 먼저 업로드 —
    // DB 기록은 저장이 실제로 성공한 뒤 confirm()에서 이뤄짐. NOT_SUPPORTED: R2 presign 자체는 DB 작업이 없어 트랜잭션이 필요 없음
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun presign(request: PhotoPresignRequest): PhotoPresignResponse {
        val extension = EXTENSION_BY_CONTENT_TYPE[request.contentType] ?: "jpg"
        val objectKey = "${request.entityType.name.lowercase()}/${UUID.randomUUID()}.$extension"

        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(PRESIGN_EXPIRY)
            .putObjectRequest(
                PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(objectKey)
                    .contentType(request.contentType)
                    .build()
            )
            .build()

        val presignedUrl = s3Presigner.presignPutObject(presignRequest).url().toString()
        return PhotoPresignResponse(
            uploadUrl = presignedUrl,
            objectKey = objectKey,
            publicUrl = "$publicUrl/$objectKey"
        )
    }

    @Transactional
    fun confirm(request: PhotoConfirmRequest): PhotoResponse {
        val nextOrder = photoRepository.findByEntityTypeAndEntityIdOrderByDisplayOrderAscIdAsc(request.entityType, request.entityId).size
        val photo = Photo(
            entityType = request.entityType,
            entityId = request.entityId,
            objectKey = request.objectKey,
            url = "$publicUrl/${request.objectKey}",
            thumbnailObjectKey = request.thumbnailObjectKey,
            thumbnailUrl = "$publicUrl/${request.thumbnailObjectKey}",
            displayOrder = nextOrder,
            // 새 사진은 항상 비공개로 시작 — 클라이언트가 요청 바디로 공개 여부를 지정할 수 없게
            // PhotoConfirmRequest에 아예 그 필드를 안 두고 여기서 고정값으로 넣음
            isPublic = false
        )
        val saved = photoRepository.save(photo)
        touchParentIfNeeded(request.entityType, request.entityId)
        return PhotoResponse.from(saved)
    }

    // 관리자가 "등록 사진 이력"에서 공개/비공개를 전환 — 사진 유무 자체를 바꾸는 게 아니라 노출 범위만 바꾸는
    // 것이므로 R2 파일이나 URL은 손대지 않음
    @Transactional
    fun updateVisibility(id: Long, isPublic: Boolean): PhotoResponse {
        val photo = photoRepository.findByIdOrThrow(id, "Photo")
        photo.isPublic = isPublic
        val saved = photoRepository.save(photo)
        touchParentIfNeeded(photo.entityType, photo.entityId)
        return PhotoResponse.from(saved)
    }

    fun listByEntity(entityType: PhotoEntityType, entityId: Long): List<PhotoResponse> =
        photoRepository.findByEntityTypeAndEntityIdOrderByDisplayOrderAscIdAsc(entityType, entityId).map { PhotoResponse.from(it) }

    // 장소 목록처럼 여러 건을 한 번에 응답할 때 건당 쿼리를 피하기 위한 벌크 조회 — entityId별로 묶어서 반환
    fun listByEntities(entityType: PhotoEntityType, entityIds: Collection<Long>): Map<Long, List<PhotoResponse>> {
        if (entityIds.isEmpty()) return emptyMap()
        return photoRepository.findByEntityTypeAndEntityIdInOrderByDisplayOrderAscIdAsc(entityType, entityIds)
            .groupBy({ it.entityId }, { PhotoResponse.from(it) })
    }

    // 마커처럼 사진 유무만 필요한 경우용 — listByEntities와 달리 URL 등을 안 실어 나르는 가벼운 벌크 조회
    fun findEntityIdsWithPhotos(entityType: PhotoEntityType, entityIds: Collection<Long>): Set<Long> {
        if (entityIds.isEmpty()) return emptySet()
        return photoRepository.findDistinctEntityIdByEntityTypeAndEntityIdIn(entityType, entityIds)
    }

    // 마커 배지를 "사진은 있지만 전부 비공개"(회색)와 "공개 사진 있음"(진하게)으로 구분하기 위한 벌크 조회
    fun findEntityIdsWithPublicPhotos(entityType: PhotoEntityType, entityIds: Collection<Long>): Set<Long> {
        if (entityIds.isEmpty()) return emptySet()
        return photoRepository.findDistinctEntityIdByEntityTypeAndEntityIdInAndIsPublicTrue(entityType, entityIds)
    }

    // 관리자 "등록 사진 이력" 화면 전용 — 장소 사진만 대상(PhotoEntityType.PLACE 고정), 장소명은 이 서비스가
    // Place를 몰라도 되게 호출부(PlaceService)가 별도로 채워 넣음
    fun findAdminPlacePhotos(isPublic: Boolean?, pageable: Pageable): Page<Photo> =
        photoRepository.findByEntityTypeAndIsPublicOptional(PhotoEntityType.PLACE.name, isPublic, pageable)

    // 소프트 삭제라 R2 파일은 안 지움(Photo 엔티티의 @SQLDelete가 deleteById()를 UPDATE deleted_at으로 바꿔치기함) —
    // 실수/버그로 삭제돼도 파일이 그대로 남아있어 데이터 유실이 안 생김. 외부 API 호출이 없어졌으니 NOT_SUPPORTED도 불필요
    @Transactional
    fun delete(id: Long) {
        val photo = photoRepository.findByIdOrThrow(id, "Photo")
        photoRepository.deleteById(photo.id)
        touchParentIfNeeded(photo.entityType, photo.entityId)
    }

    // Photo는 소유 엔티티와 FK 없이 entityType+entityId로만 느슨하게 연결돼있어서, 사진만 추가/삭제되면
    // 그 엔티티 자체의 updatedAt은 자동으로 안 바뀜 — 일정은 프론트가 /api/schedules/meta(findMaxUpdatedAt)로
    // "뭔가 바뀌었는지"만 보고 로컬 캐시를 쓸지 말지 정하기 때문에, 이걸 갱신 안 해주면 사진이 지워진 뒤에도
    // 캐시에는 계속 남아있는 상태가 됨. 장소는 이런 로컬 캐시가 없어서 대상에서 제외
    private fun touchParentIfNeeded(entityType: PhotoEntityType, entityId: Long) {
        if (entityType == PhotoEntityType.SCHEDULE_EVENT) {
            scheduleEventRepository.touchUpdatedAt(entityId)
        }
    }
}
