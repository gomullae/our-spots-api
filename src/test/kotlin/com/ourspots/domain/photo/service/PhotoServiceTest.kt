package com.ourspots.domain.photo.service

import com.ourspots.api.dto.PhotoConfirmRequest
import com.ourspots.common.exception.NotFoundException
import com.ourspots.domain.photo.entity.Photo
import com.ourspots.domain.photo.entity.PhotoEntityType
import com.ourspots.domain.photo.repository.PhotoRepository
import com.ourspots.domain.schedule.repository.ScheduleEventRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// @Value로 주입되는 bucketName/publicUrlProperty는 실제 Spring 컨텍스트 없이는 채워지지 않으므로
// InjectMockKs 대신 생성자를 직접 호출해서 테스트용 값을 넣음(PhotoService는 presign/confirm 외
// 나머지 메서드는 이 값들과 무관해서 대부분의 테스트에는 영향 없음)
class PhotoServiceTest {

    private val photoRepository: PhotoRepository = mockk()
    private val scheduleEventRepository: ScheduleEventRepository = mockk(relaxed = true)
    private val s3Presigner: S3Presigner = mockk()
    private val photoService = PhotoService(
        photoRepository = photoRepository,
        scheduleEventRepository = scheduleEventRepository,
        s3Presigner = s3Presigner,
        bucketName = "test-bucket",
        publicUrlProperty = "https://pub-test.r2.dev"
    )

    @Nested
    @DisplayName("confirm")
    inner class Confirm {

        // 새로 업로드되는 사진은 요청 바디에 isPublic 필드 자체가 없고, 서비스가 항상 false로 고정해서 저장함
        @Test
        fun confirm_shouldAlwaysStoreIsPublicFalse() {
            // given
            val request = PhotoConfirmRequest(
                entityType = PhotoEntityType.PLACE,
                entityId = 1L,
                objectKey = "place/a.jpg",
                thumbnailObjectKey = "place/a_thumb.jpg"
            )
            every {
                photoRepository.findByEntityTypeAndEntityIdOrderByDisplayOrderAscIdAsc(PhotoEntityType.PLACE, 1L)
            } returns emptyList()
            val savedSlot = slot<Photo>()
            every { photoRepository.save(capture(savedSlot)) } answers { firstArg() }

            // when
            val result = photoService.confirm(request)

            // then
            assertFalse(savedSlot.captured.isPublic)
            assertFalse(result.isPublic)
        }
    }

    @Nested
    @DisplayName("updateVisibility")
    inner class UpdateVisibility {

        @Test
        fun updateVisibility_whenPhotoExists_shouldUpdateIsPublicAndReturnResponse() {
            // given
            val photo = photo(1L, PhotoEntityType.PLACE, entityId = 10L, isPublic = false)
            every { photoRepository.findById(1L) } returns Optional.of(photo)
            every { photoRepository.save(any()) } answers { firstArg() }

            // when
            val result = photoService.updateVisibility(1L, true)

            // then
            assertTrue(result.isPublic)
            assertTrue(photo.isPublic)
            verify { photoRepository.save(photo) }
        }

        @Test
        fun updateVisibility_whenPhotoNotFound_shouldThrowNotFoundException() {
            // given
            every { photoRepository.findById(999L) } returns Optional.empty()

            // when & then
            assertThrows<NotFoundException> {
                photoService.updateVisibility(999L, true)
            }
        }

        // Photo는 Place/ScheduleEvent와 FK 없이 연결돼있어서, 일정 사진만 부모의 updatedAt을 직접 touch —
        // 장소는 이런 로컬 캐시가 없어서 대상에서 제외됨(touchParentIfNeeded 규칙)
        @Test
        fun updateVisibility_whenScheduleEventPhoto_shouldTouchParentUpdatedAt() {
            // given
            val photo = photo(1L, PhotoEntityType.SCHEDULE_EVENT, entityId = 20L, isPublic = false)
            every { photoRepository.findById(1L) } returns Optional.of(photo)
            every { photoRepository.save(any()) } answers { firstArg() }

            // when
            photoService.updateVisibility(1L, true)

            // then
            verify { scheduleEventRepository.touchUpdatedAt(20L) }
        }

        @Test
        fun updateVisibility_whenPlacePhoto_shouldNotTouchScheduleEventRepository() {
            // given
            val photo = photo(1L, PhotoEntityType.PLACE, entityId = 10L, isPublic = false)
            every { photoRepository.findById(1L) } returns Optional.of(photo)
            every { photoRepository.save(any()) } answers { firstArg() }

            // when
            photoService.updateVisibility(1L, true)

            // then
            verify(exactly = 0) { scheduleEventRepository.touchUpdatedAt(any()) }
        }
    }

    @Nested
    @DisplayName("findEntityIdsWithPublicPhotos")
    inner class FindEntityIdsWithPublicPhotos {

        @Test
        fun findEntityIdsWithPublicPhotos_whenEmptyIds_shouldReturnEmptyWithoutQuerying() {
            // when
            val result = photoService.findEntityIdsWithPublicPhotos(PhotoEntityType.PLACE, emptyList())

            // then
            assertEquals(emptySet(), result)
            verify(exactly = 0) { photoRepository.findDistinctEntityIdByEntityTypeAndEntityIdInAndIsPublicTrue(any(), any()) }
        }

        @Test
        fun findEntityIdsWithPublicPhotos_whenIdsProvided_shouldDelegateToRepository() {
            // given
            every {
                photoRepository.findDistinctEntityIdByEntityTypeAndEntityIdInAndIsPublicTrue(PhotoEntityType.PLACE, listOf(1L, 2L))
            } returns setOf(1L)

            // when
            val result = photoService.findEntityIdsWithPublicPhotos(PhotoEntityType.PLACE, listOf(1L, 2L))

            // then
            assertEquals(setOf(1L), result)
        }
    }

    @Nested
    @DisplayName("findAdminPlacePhotos")
    inner class FindAdminPlacePhotos {

        // 호출부(PlaceService)가 entityType을 몰라도 되게 PLACE로 고정해서 리포지토리에 넘기는지가 핵심
        @Test
        fun findAdminPlacePhotos_shouldFixEntityTypeToPlace() {
            // given
            val page: Page<Photo> = PageImpl(emptyList(), PageRequest.of(0, 20), 0)
            every {
                photoRepository.findByEntityTypeAndIsPublicOptional(PhotoEntityType.PLACE.name, true, PageRequest.of(0, 20))
            } returns page

            // when
            val result = photoService.findAdminPlacePhotos(true, PageRequest.of(0, 20))

            // then
            assertEquals(0, result.totalElements)
            verify { photoRepository.findByEntityTypeAndIsPublicOptional(PhotoEntityType.PLACE.name, true, PageRequest.of(0, 20)) }
        }
    }

    private fun photo(id: Long, entityType: PhotoEntityType, entityId: Long, isPublic: Boolean): Photo = Photo(
        id = id,
        entityType = entityType,
        entityId = entityId,
        objectKey = "key/$id.jpg",
        url = "https://pub-test.r2.dev/key/$id.jpg",
        thumbnailObjectKey = "key/${id}_thumb.jpg",
        thumbnailUrl = "https://pub-test.r2.dev/key/${id}_thumb.jpg",
        isPublic = isPublic
    )
}
