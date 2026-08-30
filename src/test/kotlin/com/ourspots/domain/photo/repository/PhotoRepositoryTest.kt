package com.ourspots.domain.photo.repository

import com.ourspots.domain.photo.entity.Photo
import com.ourspots.domain.photo.entity.PhotoEntityType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@DataJpaTest
@ActiveProfiles("test")
class PhotoRepositoryTest {

    @Autowired
    private lateinit var photoRepository: PhotoRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @BeforeEach
    fun setUp() {
        photoRepository.deleteAll()
        entityManager.flush()
        entityManager.clear()
    }

    @Nested
    @DisplayName("findDistinctEntityIdByEntityTypeAndEntityIdIn")
    inner class FindDistinctEntityIdByEntityTypeAndEntityIdIn {

        // 마커 hasPhotos 배지용 쿼리 — entity_type+entity_id 인덱스가 그대로 타는지, 같은 entityId에 사진이
        // 여러 장이어도 중복 없이 한 번만 나오는지, 다른 entityType은 섞여 들어오지 않는지 함께 검증
        @Test
        fun findDistinctEntityIdByEntityTypeAndEntityIdIn_shouldReturnDistinctIdsOnlyForMatchingType() {
            createPhoto(PhotoEntityType.PLACE, 1L)
            createPhoto(PhotoEntityType.PLACE, 1L) // 같은 장소에 사진 2장 — 결과는 1L 한 번만
            createPhoto(PhotoEntityType.PLACE, 2L)
            createPhoto(PhotoEntityType.SCHEDULE_EVENT, 1L) // 같은 id=1이지만 다른 entityType — 섞이면 안 됨
            entityManager.flush()
            entityManager.clear()

            val result = photoRepository.findDistinctEntityIdByEntityTypeAndEntityIdIn(
                PhotoEntityType.PLACE,
                listOf(1L, 2L, 3L) // 3L은 사진이 아예 없는 장소
            )

            assertEquals(setOf(1L, 2L), result)
        }

        @Test
        fun findDistinctEntityIdByEntityTypeAndEntityIdIn_whenSoftDeleted_shouldExclude() {
            val photo = createPhoto(PhotoEntityType.PLACE, 1L)
            entityManager.flush()
            photoRepository.deleteById(photo.id) // @SQLDelete → deletedAt UPDATE
            entityManager.flush()
            entityManager.clear()

            val result = photoRepository.findDistinctEntityIdByEntityTypeAndEntityIdIn(PhotoEntityType.PLACE, listOf(1L))

            assertEquals(emptySet(), result)
        }

        @Test
        fun findDistinctEntityIdByEntityTypeAndEntityIdIn_whenEmptyIds_shouldReturnEmpty() {
            createPhoto(PhotoEntityType.PLACE, 1L)
            entityManager.flush()
            entityManager.clear()

            val result = photoRepository.findDistinctEntityIdByEntityTypeAndEntityIdIn(PhotoEntityType.PLACE, emptyList())

            assertEquals(emptySet(), result)
        }
    }

    @Nested
    @DisplayName("findDistinctEntityIdByEntityTypeAndEntityIdInAndIsPublicTrue")
    inner class FindDistinctEntityIdByEntityTypeAndEntityIdInAndIsPublicTrue {

        // 마커 배지를 "사진 있음(회색)"/"공개 사진 있음(진하게)"으로 나누는 핵심 쿼리 —
        // 비공개 사진만 있는 장소는 결과에서 빠지는지가 검증 포인트
        @Test
        fun findDistinctEntityIdByEntityTypeAndEntityIdInAndIsPublicTrue_shouldExcludePrivateOnlyPlaces() {
            createPhoto(PhotoEntityType.PLACE, 1L, isPublic = true)
            createPhoto(PhotoEntityType.PLACE, 2L, isPublic = false) // 비공개만 있는 장소 — 제외되어야 함
            createPhoto(PhotoEntityType.PLACE, 3L, isPublic = false)
            createPhoto(PhotoEntityType.PLACE, 3L, isPublic = true) // 같은 장소에 비공개+공개 섞임 — 포함되어야 함
            entityManager.flush()
            entityManager.clear()

            val result = photoRepository.findDistinctEntityIdByEntityTypeAndEntityIdInAndIsPublicTrue(
                PhotoEntityType.PLACE,
                listOf(1L, 2L, 3L)
            )

            assertEquals(setOf(1L, 3L), result)
        }

        @Test
        fun findDistinctEntityIdByEntityTypeAndEntityIdInAndIsPublicTrue_whenEmptyIds_shouldReturnEmpty() {
            createPhoto(PhotoEntityType.PLACE, 1L, isPublic = true)
            entityManager.flush()
            entityManager.clear()

            val result = photoRepository.findDistinctEntityIdByEntityTypeAndEntityIdInAndIsPublicTrue(PhotoEntityType.PLACE, emptyList())

            assertEquals(emptySet(), result)
        }
    }

    @Nested
    @DisplayName("findByEntityTypeAndIsPublicOptional")
    inner class FindByEntityTypeAndIsPublicOptional {

        // 관리자 "등록 사진 이력" 화면 전용 쿼리 — isPublic=null(전체)/true(공개)/false(비공개) 세 갈래 필터링과
        // entityType 혼선 없는지, 등록일시 내림차순 정렬까지 함께 검증
        @Test
        fun findByEntityTypeAndIsPublicOptional_whenIsPublicNull_shouldReturnAllOrderedByCreatedAtDesc() {
            val older = createPhoto(PhotoEntityType.PLACE, 1L, isPublic = true)
            entityManager.flush()
            val newer = createPhoto(PhotoEntityType.PLACE, 2L, isPublic = false)
            createPhoto(PhotoEntityType.SCHEDULE_EVENT, 1L, isPublic = true) // 다른 entityType — 결과에서 제외돼야 함
            entityManager.flush()
            entityManager.clear()

            val result = photoRepository.findByEntityTypeAndIsPublicOptional(
                PhotoEntityType.PLACE.name, null, PageRequest.of(0, 10)
            )

            assertEquals(2, result.totalElements)
            assertEquals(listOf(newer.id, older.id), result.content.map { it.id }) // 최신 등록분이 먼저
        }

        @Test
        fun findByEntityTypeAndIsPublicOptional_whenIsPublicTrue_shouldReturnOnlyPublic() {
            createPhoto(PhotoEntityType.PLACE, 1L, isPublic = true)
            createPhoto(PhotoEntityType.PLACE, 2L, isPublic = false)
            entityManager.flush()
            entityManager.clear()

            val result = photoRepository.findByEntityTypeAndIsPublicOptional(
                PhotoEntityType.PLACE.name, true, PageRequest.of(0, 10)
            )

            assertEquals(1, result.totalElements)
            assertTrue(result.content.all { it.isPublic })
        }

        @Test
        fun findByEntityTypeAndIsPublicOptional_whenIsPublicFalse_shouldReturnOnlyPrivate() {
            createPhoto(PhotoEntityType.PLACE, 1L, isPublic = true)
            createPhoto(PhotoEntityType.PLACE, 2L, isPublic = false)
            entityManager.flush()
            entityManager.clear()

            val result = photoRepository.findByEntityTypeAndIsPublicOptional(
                PhotoEntityType.PLACE.name, false, PageRequest.of(0, 10)
            )

            assertEquals(1, result.totalElements)
            assertTrue(result.content.none { it.isPublic })
        }
    }

    private fun createPhoto(entityType: PhotoEntityType, entityId: Long, isPublic: Boolean = false): Photo {
        val photo = Photo(
            entityType = entityType,
            entityId = entityId,
            objectKey = "place/${entityType}_$entityId.jpg",
            url = "https://pub-test.r2.dev/place/${entityType}_$entityId.jpg",
            thumbnailObjectKey = "place/${entityType}_${entityId}_thumb.jpg",
            thumbnailUrl = "https://pub-test.r2.dev/place/${entityType}_${entityId}_thumb.jpg",
            isPublic = isPublic
        )
        return entityManager.persist(photo)
    }
}
