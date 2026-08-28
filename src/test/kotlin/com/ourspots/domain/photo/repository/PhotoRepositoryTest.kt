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
import org.springframework.test.context.ActiveProfiles
import kotlin.test.assertEquals

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

    private fun createPhoto(entityType: PhotoEntityType, entityId: Long): Photo {
        val photo = Photo(
            entityType = entityType,
            entityId = entityId,
            objectKey = "place/${entityType}_$entityId.jpg",
            url = "https://pub-test.r2.dev/place/${entityType}_$entityId.jpg",
            thumbnailObjectKey = "place/${entityType}_${entityId}_thumb.jpg",
            thumbnailUrl = "https://pub-test.r2.dev/place/${entityType}_${entityId}_thumb.jpg"
        )
        return entityManager.persist(photo)
    }
}
