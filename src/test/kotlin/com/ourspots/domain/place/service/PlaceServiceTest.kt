package com.ourspots.domain.place.service

import com.ourspots.api.dto.PlaceCreateRequest
import com.ourspots.api.dto.PlaceUpdateRequest
import com.ourspots.api.dto.RecentPlacesFilter
import com.ourspots.common.exception.DuplicateException
import com.ourspots.common.exception.NotFoundException
import com.ourspots.common.exception.ServiceUnavailableException
import com.ourspots.domain.photo.service.PhotoService
import com.ourspots.domain.place.entity.Place
import com.ourspots.domain.place.entity.PlaceType
import com.ourspots.domain.place.repository.PlaceRepository
import io.mockk.*
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.data.domain.PageImpl
import org.springframework.data.domain.PageRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PlaceServiceTest {

    @MockK
    private lateinit var placeRepository: PlaceRepository

    @MockK
    private lateinit var googlePlaceSyncService: GooglePlaceSyncService

    @MockK(relaxed = true)
    private lateinit var photoService: PhotoService

    @InjectMockKs
    private lateinit var placeService: PlaceService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        // 사진 기능은 별도 테스트에서 검증 — 여기선 항상 빈 목록을 반환하게 해서 기존 테스트 로직에 영향 없게 함
        every { photoService.listByEntity(any(), any()) } returns emptyList()
        every { photoService.listByEntities(any(), any()) } returns emptyMap()
        every { photoService.findEntityIdsWithPhotos(any(), any()) } returns emptySet()
    }

    @Nested
    @DisplayName("getPlace")
    inner class GetPlace {

        @Test
        fun getPlace_whenIdExists_shouldReturnPlace() {
            // given
            val place = createPlace(1L, "맛집1", PlaceType.RESTAURANT)
            every { placeRepository.findById(1L) } returns Optional.of(place)

            // when
            val result = placeService.getPlace(1L, true)

            // then
            assertEquals("맛집1", result.name)
            assertEquals(PlaceType.RESTAURANT, result.type)
        }

        @Test
        fun getPlace_whenIdNotExists_shouldThrowNotFoundException() {
            // given
            every { placeRepository.findById(999L) } returns Optional.empty()

            // when & then
            assertThrows<NotFoundException> {
                placeService.getPlace(999L, true)
            }
        }
    }

    @Nested
    @DisplayName("createPlace")
    inner class CreatePlace {

        @Test
        fun createPlace_whenValidRequest_shouldReturnCreatedPlace() {
            // given
            val request = PlaceCreateRequest(
                name = "새 맛집",
                type = PlaceType.RESTAURANT,
                address = "서울시 강남구",
                latitude = 37.5,
                longitude = 127.0,
                description = "맛있는 집",
                grade = 1
            )
            val savedPlace = createPlace(1L, "새 맛집", PlaceType.RESTAURANT)

            every { placeRepository.existsByNameAndAddress("새 맛집", "서울시 강남구") } returns false
            every { placeRepository.save(any()) } returns savedPlace

            // when
            val result = placeService.createPlace(request)

            // then
            assertEquals("새 맛집", result.name)
            verify { placeRepository.save(any()) }
        }

        @Test
        fun createPlace_whenDuplicateNameAndAddress_shouldThrowDuplicateException() {
            // given
            val request = PlaceCreateRequest(
                name = "기존 맛집",
                type = PlaceType.RESTAURANT,
                address = "서울시 강남구",
                latitude = 37.5,
                longitude = 127.0
            )
            every { placeRepository.existsByNameAndAddress("기존 맛집", "서울시 강남구") } returns true

            // when & then
            assertThrows<DuplicateException> {
                placeService.createPlace(request)
            }
            verify(exactly = 0) { placeRepository.save(any()) }
        }

        @Test
        fun createPlace_whenSameAddressDifferentName_shouldSucceed() {
            // given
            val request = PlaceCreateRequest(
                name = "다른 맛집",
                type = PlaceType.RESTAURANT,
                address = "서울시 강남구",
                latitude = 37.5,
                longitude = 127.0
            )
            val savedPlace = createPlace(1L, "다른 맛집", PlaceType.RESTAURANT)

            every { placeRepository.existsByNameAndAddress("다른 맛집", "서울시 강남구") } returns false
            every { placeRepository.save(any()) } returns savedPlace

            // when
            val result = placeService.createPlace(request)

            // then
            assertNotNull(result)
            verify { placeRepository.save(any()) }
        }
    }

    @Nested
    @DisplayName("updatePlace")
    inner class UpdatePlace {

        @Test
        fun updatePlace_whenIdExists_shouldReturnUpdatedPlace() {
            // given
            val existingPlace = createPlace(1L, "기존 맛집", PlaceType.RESTAURANT)
            val request = PlaceUpdateRequest(
                name = "수정된 맛집",
                grade = 2
            )

            every { placeRepository.findById(1L) } returns Optional.of(existingPlace)
            every { placeRepository.save(any()) } answers { firstArg() }

            // when
            val result = placeService.updatePlace(1L, request)

            // then
            assertEquals("수정된 맛집", result.name)
            verify { placeRepository.save(any()) }
        }

        @Test
        fun updatePlace_whenIdNotExists_shouldThrowNotFoundException() {
            // given
            val request = PlaceUpdateRequest(name = "수정")
            every { placeRepository.findById(999L) } returns Optional.empty()

            // when & then
            assertThrows<NotFoundException> {
                placeService.updatePlace(999L, request)
            }
        }

        @Test
        fun updatePlace_whenPartialUpdate_shouldKeepNullFieldsUnchanged() {
            // given
            val existingPlace = createPlace(1L, "맛집", PlaceType.RESTAURANT).apply {
                description = "기존 설명"
                grade = 1
            }
            val request = PlaceUpdateRequest(name = "새 이름")  // description, grade는 null

            every { placeRepository.findById(1L) } returns Optional.of(existingPlace)
            every { placeRepository.save(any()) } answers { firstArg() }

            // when
            val result = placeService.updatePlace(1L, request)

            // then
            assertEquals("새 이름", result.name)
            assertEquals("기존 설명", existingPlace.description)
            assertEquals(1, existingPlace.grade)
        }
    }

    @Nested
    @DisplayName("deletePlace")
    inner class DeletePlace {

        @Test
        fun deletePlace_whenIdExists_shouldSoftDelete() {
            // given
            val place = createPlace(1L, "삭제할 맛집", PlaceType.RESTAURANT)
            every { placeRepository.findById(1L) } returns Optional.of(place)
            every { placeRepository.delete(place) } just runs

            // when
            placeService.deletePlace(1L)

            // then
            verify { placeRepository.delete(place) }
        }

        @Test
        fun deletePlace_whenIdNotExists_shouldThrowNotFoundException() {
            // given
            every { placeRepository.findById(999L) } returns Optional.empty()

            // when & then
            assertThrows<NotFoundException> {
                placeService.deletePlace(999L)
            }
        }
    }

    @Nested
    @DisplayName("getMarkers")
    inner class GetMarkers {

        @Test
        fun getMarkers_whenNoFilters_shouldReturnAllMarkers() {
            // given
            val places = listOf(
                createPlace(1L, "맛집1", PlaceType.RESTAURANT),
                createPlace(2L, "놀이터1", PlaceType.KIDS_PLAYGROUND)
            )
            every { placeRepository.findAll() } returns places

            // when
            val result = placeService.getMarkers(null, null, null, null, null, true)

            // then
            assertEquals(2, result.size)
        }

        @Test
        fun getMarkers_whenTypeSpecified_shouldReturnFilteredMarkers() {
            // given
            val restaurants = listOf(createPlace(1L, "맛집1", PlaceType.RESTAURANT))
            every { placeRepository.findByType(PlaceType.RESTAURANT) } returns restaurants

            // when
            val result = placeService.getMarkers(PlaceType.RESTAURANT, null, null, null, null, true)

            // then
            assertEquals(1, result.size)
        }

        @Test
        fun getMarkers_whenBoundsSpecified_shouldReturnMarkersWithinBounds() {
            // given
            val places = listOf(createPlace(1L, "맛집1", PlaceType.RESTAURANT))
            every { placeRepository.findWithinBounds(37.0, 126.0, 38.0, 128.0) } returns places

            // when
            val result = placeService.getMarkers(null, 37.0, 126.0, 38.0, 128.0, true)

            // then
            assertEquals(1, result.size)
            verify { placeRepository.findWithinBounds(37.0, 126.0, 38.0, 128.0) }
        }

        @Test
        fun getMarkers_whenTypeAndBoundsSpecified_shouldApplyBothFilters() {
            // given
            val restaurants = listOf(createPlace(1L, "맛집1", PlaceType.RESTAURANT))
            every {
                placeRepository.findByTypeWithinBounds(PlaceType.RESTAURANT, 37.0, 126.0, 38.0, 128.0)
            } returns restaurants

            // when
            val result = placeService.getMarkers(PlaceType.RESTAURANT, 37.0, 126.0, 38.0, 128.0, true)

            // then
            assertEquals(1, result.size)
            verify { placeRepository.findByTypeWithinBounds(PlaceType.RESTAURANT, 37.0, 126.0, 38.0, 128.0) }
        }

        // 비로그인 조회는 findAll/findByType/findWithinBounds류가 아니라 findPublicMarkers 하나로만 가야
        // 함(개인 카테고리 제외 + 맛집 1등급 제한을 DB 쿼리 자체에 담아두는 지점이라 다른 메서드로 새면
        // 그 규칙이 통째로 빠짐) — 아래 테스트들은 항상 findPublicMarkers 호출 여부까지 같이 검증

        @Test
        fun getMarkers_whenUnauthenticatedAndNoFilters_shouldQueryPublicMarkersWithPersonalTypesExcluded() {
            // given
            val grade1Restaurant = createPlace(1L, "찐맛집", PlaceType.RESTAURANT, grade = 1)
            every {
                placeRepository.findPublicMarkers(PlaceType.PERSONAL_TYPES.map { it.name }, null, null, null, null, null)
            } returns listOf(grade1Restaurant)

            // when
            val result = placeService.getMarkers(null, null, null, null, null, false)

            // then
            assertEquals(1, result.size)
            verify { placeRepository.findPublicMarkers(PlaceType.PERSONAL_TYPES.map { it.name }, null, null, null, null, null) }
            verify(exactly = 0) { placeRepository.findAll() }
        }

        @Test
        fun getMarkers_whenUnauthenticatedAndRestaurantTypeSpecified_shouldStillQueryPublicMarkers() {
            // given — 타입을 직접 지정해도(findByType 경로가 아니라) findPublicMarkers로 가야 등급 제한이 적용됨
            val grade1Restaurant = createPlace(1L, "찐맛집", PlaceType.RESTAURANT, grade = 1)
            every {
                placeRepository.findPublicMarkers(PlaceType.PERSONAL_TYPES.map { it.name }, "RESTAURANT", null, null, null, null)
            } returns listOf(grade1Restaurant)

            // when
            val result = placeService.getMarkers(PlaceType.RESTAURANT, null, null, null, null, false)

            // then
            assertEquals(1, result.size)
            verify { placeRepository.findPublicMarkers(PlaceType.PERSONAL_TYPES.map { it.name }, "RESTAURANT", null, null, null, null) }
            verify(exactly = 0) { placeRepository.findByType(any()) }
        }

        @Test
        fun getMarkers_whenUnauthenticatedAndBoundsSpecified_shouldPassBoundsToPublicMarkers() {
            // given
            val places = listOf(createPlace(1L, "아지트", PlaceType.RELAXATION))
            every {
                placeRepository.findPublicMarkers(PlaceType.PERSONAL_TYPES.map { it.name }, null, 37.0, 126.0, 38.0, 128.0)
            } returns places

            // when
            val result = placeService.getMarkers(null, 37.0, 126.0, 38.0, 128.0, false)

            // then
            assertEquals(1, result.size)
            verify { placeRepository.findPublicMarkers(PlaceType.PERSONAL_TYPES.map { it.name }, null, 37.0, 126.0, 38.0, 128.0) }
            verify(exactly = 0) { placeRepository.findWithinBounds(any(), any(), any(), any()) }
        }

        @Test
        fun getMarkers_whenPersonalTypeAndUnauthenticated_shouldReturnEmptyWithoutQuerying() {
            // when
            val result = placeService.getMarkers(PlaceType.MY_FOOTPRINT, null, null, null, null, false)

            // then
            assertEquals(0, result.size)
            verify(exactly = 0) { placeRepository.findPublicMarkers(any(), any(), any(), any(), any(), any()) }
        }
    }

    @Nested
    @DisplayName("getRecentPlaces")
    inner class GetRecentPlaces {

        @Test
        fun getRecentPlaces_whenNoDatesProvided_shouldDefaultToLastThreeMonths() {
            // given
            val places = listOf(
                createPlace(1L, "최근 맛집1", PlaceType.RESTAURANT),
                createPlace(2L, "최근 맛집2", PlaceType.RESTAURANT)
            )
            val startSlot = slot<LocalDateTime>()
            val endSlot = slot<LocalDateTime>()
            every {
                placeRepository.searchRecentPlaces(capture(startSlot), capture(endSlot), null, null, null, true, PageRequest.of(0, 10))
            } returns PageImpl(places, PageRequest.of(0, 10), 2)

            // when
            val result = placeService.getRecentPlaces(RecentPlacesFilter(), 0, 10)

            // then
            assertEquals(2, result.totalElements)
            assertEquals(listOf("최근 맛집1", "최근 맛집2"), result.content.map { it.name })
            val expectedStart = LocalDate.now().minusMonths(3).atStartOfDay()
            val expectedEnd = LocalDate.now().plusDays(1).atStartOfDay()
            assertEquals(expectedStart, startSlot.captured)
            assertEquals(expectedEnd, endSlot.captured)
        }

        @Test
        fun getRecentPlaces_whenDatesProvided_shouldUseThemAsInclusiveRange() {
            // given
            val startDate = LocalDate.of(2026, 1, 1)
            val endDate = LocalDate.of(2026, 1, 31)
            every {
                placeRepository.searchRecentPlaces(
                    startDate.atStartOfDay(),
                    endDate.plusDays(1).atStartOfDay(),
                    null, null, null, true,
                    PageRequest.of(0, 10)
                )
            } returns PageImpl(emptyList(), PageRequest.of(0, 10), 0)

            // when
            placeService.getRecentPlaces(RecentPlacesFilter(startDate = startDate, endDate = endDate), 0, 10)

            // then
            verify {
                placeRepository.searchRecentPlaces(
                    startDate.atStartOfDay(),
                    endDate.plusDays(1).atStartOfDay(),
                    null, null, null, true,
                    PageRequest.of(0, 10)
                )
            }
        }

        @Test
        fun getRecentPlaces_whenKeywordBlank_shouldPassNullToRepository() {
            // given
            every {
                placeRepository.searchRecentPlaces(any(), any(), null, null, null, true, PageRequest.of(0, 10))
            } returns PageImpl(emptyList(), PageRequest.of(0, 10), 0)

            // when
            placeService.getRecentPlaces(RecentPlacesFilter(keyword = "   "), 0, 10)

            // then (trim 후 빈 문자열이면 필터링 안 하도록 null로 정규화)
            verify { placeRepository.searchRecentPlaces(any(), any(), null, null, null, true, PageRequest.of(0, 10)) }
        }

        @Test
        fun getRecentPlaces_whenKeywordProvided_shouldTrimAndPassThrough() {
            // given
            every {
                placeRepository.searchRecentPlaces(any(), any(), "스타벅스", null, null, true, PageRequest.of(0, 10))
            } returns PageImpl(emptyList(), PageRequest.of(0, 10), 0)

            // when
            placeService.getRecentPlaces(RecentPlacesFilter(keyword = "  스타벅스  "), 0, 10)

            // then
            verify { placeRepository.searchRecentPlaces(any(), any(), "스타벅스", null, null, true, PageRequest.of(0, 10)) }
        }

        @Test
        fun getRecentPlaces_whenKeywordContainsPercent_shouldEscapeBeforePassingToRepository() {
            // given (LIKE 패턴에서 %는 와일드카드이므로 이스케이프 없이 넘기면 의도치 않게 넓게 매치됨)
            every {
                placeRepository.searchRecentPlaces(any(), any(), "50\\%", null, null, true, PageRequest.of(0, 10))
            } returns PageImpl(emptyList(), PageRequest.of(0, 10), 0)

            // when
            placeService.getRecentPlaces(RecentPlacesFilter(keyword = "50%"), 0, 10)

            // then
            verify { placeRepository.searchRecentPlaces(any(), any(), "50\\%", null, null, true, PageRequest.of(0, 10)) }
        }

        @Test
        fun getRecentPlaces_whenKeywordContainsUnderscore_shouldEscapeBeforePassingToRepository() {
            // given (LIKE 패턴에서 _는 임의의 한 글자를 뜻하는 와일드카드)
            every {
                placeRepository.searchRecentPlaces(any(), any(), "\\_test", null, null, true, PageRequest.of(0, 10))
            } returns PageImpl(emptyList(), PageRequest.of(0, 10), 0)

            // when
            placeService.getRecentPlaces(RecentPlacesFilter(keyword = "_test"), 0, 10)

            // then
            verify { placeRepository.searchRecentPlaces(any(), any(), "\\_test", null, null, true, PageRequest.of(0, 10)) }
        }

        @Test
        fun getRecentPlaces_whenTypeProvided_shouldPassTypeNameToRepository() {
            // given
            every {
                placeRepository.searchRecentPlaces(any(), any(), null, "KIDS_PLAYGROUND", null, true, PageRequest.of(0, 10))
            } returns PageImpl(emptyList(), PageRequest.of(0, 10), 0)

            // when
            placeService.getRecentPlaces(RecentPlacesFilter(type = PlaceType.KIDS_PLAYGROUND), 0, 10)

            // then
            verify { placeRepository.searchRecentPlaces(any(), any(), null, "KIDS_PLAYGROUND", null, true, PageRequest.of(0, 10)) }
        }

        @Test
        fun getRecentPlaces_whenIncludeDeletedFalse_shouldPassThrough() {
            // given
            every {
                placeRepository.searchRecentPlaces(any(), any(), null, null, null, false, PageRequest.of(0, 10))
            } returns PageImpl(emptyList(), PageRequest.of(0, 10), 0)

            // when
            placeService.getRecentPlaces(RecentPlacesFilter(includeDeleted = false), 0, 10)

            // then
            verify { placeRepository.searchRecentPlaces(any(), any(), null, null, null, false, PageRequest.of(0, 10)) }
        }

        @Test
        fun getRecentPlaces_whenPageRequested_shouldPassPageAndSizeThrough() {
            // given
            every {
                placeRepository.searchRecentPlaces(any(), any(), null, null, null, true, PageRequest.of(1, 5))
            } returns PageImpl(emptyList(), PageRequest.of(1, 5), 0)

            // when
            val result = placeService.getRecentPlaces(RecentPlacesFilter(), 1, 5)

            // then
            assertEquals(0, result.totalElements)
            verify { placeRepository.searchRecentPlaces(any(), any(), null, null, null, true, PageRequest.of(1, 5)) }
        }

        @Test
        fun getRecentPlaces_whenSizeExceedsMax_shouldCapAt100() {
            // given (관리자 전용이라 위협도는 낮지만, 실수로 큰 값을 보내 대량 조회를 유발하는 걸 방지)
            every {
                placeRepository.searchRecentPlaces(any(), any(), null, null, null, true, PageRequest.of(0, 100))
            } returns PageImpl(emptyList(), PageRequest.of(0, 100), 0)

            // when
            placeService.getRecentPlaces(RecentPlacesFilter(), 0, 100000)

            // then
            verify { placeRepository.searchRecentPlaces(any(), any(), null, null, null, true, PageRequest.of(0, 100)) }
        }

        @Test
        fun getRecentPlaces_whenSizeIsZeroOrNegative_shouldCoerceToAtLeastOne() {
            // given
            every {
                placeRepository.searchRecentPlaces(any(), any(), null, null, null, true, PageRequest.of(0, 1))
            } returns PageImpl(emptyList(), PageRequest.of(0, 1), 0)

            // when
            placeService.getRecentPlaces(RecentPlacesFilter(), 0, 0)

            // then
            verify { placeRepository.searchRecentPlaces(any(), any(), null, null, null, true, PageRequest.of(0, 1)) }
        }
    }

    @Nested
    @DisplayName("restorePlace")
    inner class RestorePlace {

        @Test
        fun restorePlace_whenPlaceSoftDeleted_shouldClearDeletedAt() {
            // given
            val place = createPlace(1L, "삭제됐던 맛집", PlaceType.RESTAURANT).apply {
                deletedAt = LocalDateTime.now()
            }
            every { placeRepository.findByIdIncludingDeleted(1L) } returns place
            every { placeRepository.save(any()) } answers { firstArg() }

            // when
            val result = placeService.restorePlace(1L)

            // then
            assertEquals(null, result.deletedAt)
            verify { placeRepository.save(place) }
        }

        @Test
        fun restorePlace_whenIdNotExists_shouldThrowNotFoundException() {
            // given
            every { placeRepository.findByIdIncludingDeleted(999L) } returns null

            // when & then
            assertThrows<NotFoundException> {
                placeService.restorePlace(999L)
            }
            verify(exactly = 0) { placeRepository.save(any()) }
        }

        @Test
        fun restorePlace_whenAlreadyNotDeleted_shouldSucceedIdempotently() {
            // given (이미 deletedAt이 null인 장소를 다시 복구해도 에러 없이 그대로 성공해야 함)
            val place = createPlace(1L, "정상 맛집", PlaceType.RESTAURANT)
            every { placeRepository.findByIdIncludingDeleted(1L) } returns place
            every { placeRepository.save(any()) } answers { firstArg() }

            // when
            val result = placeService.restorePlace(1L)

            // then
            assertEquals(null, result.deletedAt)
        }
    }

    @Nested
    @DisplayName("syncGoogleRating")
    inner class SyncGoogleRating {

        @Test
        fun syncGoogleRating_whenNotConfigured_shouldThrowServiceUnavailableException() {
            // given
            every { googlePlaceSyncService.isConfigured() } returns false

            // when & then
            assertThrows<ServiceUnavailableException> {
                placeService.syncGoogleRating(1L)
            }
            verify(exactly = 0) { placeRepository.findById(any()) }
        }

        @Test
        fun syncGoogleRating_whenPlaceFoundOnGoogle_shouldUpdateRatingAndResetFailCount() {
            // given
            val place = createPlace(1L, "맛집", PlaceType.RESTAURANT).apply {
                googleRatingFailCount = 2
            }
            every { googlePlaceSyncService.isConfigured() } returns true
            every { placeRepository.findById(1L) } returns Optional.of(place)
            every {
                googlePlaceSyncService.search("맛집", "서울시 테스트구", 37.5, 127.0)
            } returns GooglePlaceSyncService.GooglePlaceData(placeId = "abc123", rating = 4.5, ratingsTotal = 120)
            every { placeRepository.save(any()) } answers { firstArg() }

            // when
            val result = placeService.syncGoogleRating(1L)

            // then
            assertEquals(4.5, result.googleRating)
            assertEquals(120, result.googleRatingsTotal)
            assertEquals(0, place.googleRatingFailCount)
        }

        @Test
        fun syncGoogleRating_whenNotFoundOnGoogle_shouldIncrementFailCountWithoutChangingRating() {
            // given
            val place = createPlace(1L, "맛집", PlaceType.RESTAURANT).apply {
                googleRatingFailCount = 0
            }
            every { googlePlaceSyncService.isConfigured() } returns true
            every { placeRepository.findById(1L) } returns Optional.of(place)
            every {
                googlePlaceSyncService.search("맛집", "서울시 테스트구", 37.5, 127.0)
            } returns null
            every { placeRepository.save(any()) } answers { firstArg() }

            // when
            val result = placeService.syncGoogleRating(1L)

            // then
            assertEquals(null, result.googleRating)
            assertEquals(1, place.googleRatingFailCount)
        }

        @Test
        fun syncGoogleRating_whenIdNotExists_shouldThrowNotFoundException() {
            // given
            every { googlePlaceSyncService.isConfigured() } returns true
            every { placeRepository.findById(999L) } returns Optional.empty()

            // when & then
            assertThrows<NotFoundException> {
                placeService.syncGoogleRating(999L)
            }
        }
    }

    private fun createPlace(id: Long, name: String, type: PlaceType, grade: Int = 1): Place {
        return Place(
            id = id,
            name = name,
            type = type,
            address = "서울시 테스트구",
            latitude = 37.5,
            longitude = 127.0,
            grade = grade
        )
    }
}
