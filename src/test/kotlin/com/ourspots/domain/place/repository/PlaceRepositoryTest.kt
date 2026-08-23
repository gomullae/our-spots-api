package com.ourspots.domain.place.repository

import com.ourspots.domain.place.entity.Place
import com.ourspots.domain.place.entity.PlaceType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager
import org.springframework.data.domain.PageRequest
import org.springframework.test.context.ActiveProfiles
import java.time.LocalDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@DataJpaTest
@ActiveProfiles("test")
class PlaceRepositoryTest {

    @Autowired
    private lateinit var placeRepository: PlaceRepository

    @Autowired
    private lateinit var entityManager: TestEntityManager

    @BeforeEach
    fun setUp() {
        placeRepository.deleteAll()
        entityManager.flush()
        entityManager.clear()
    }

    @Nested
    @DisplayName("findByType")
    inner class FindByType {

        @Test
        fun findByType_whenTypeExists_shouldReturnFilteredPlaces() {
            // given
            createPlace("맛집1", PlaceType.RESTAURANT)
            createPlace("맛집2", PlaceType.RESTAURANT)
            createPlace("놀이터1", PlaceType.KIDS_PLAYGROUND)

            // when
            val restaurants = placeRepository.findByType(PlaceType.RESTAURANT)

            // then
            assertEquals(2, restaurants.size)
            assertTrue(restaurants.all { it.type == PlaceType.RESTAURANT })
        }
    }

    @Nested
    @DisplayName("existsByNameAndAddress")
    inner class ExistsByNameAndAddress {

        @Test
        fun existsByNameAndAddress_whenBothMatch_shouldReturnTrue() {
            // given
            createPlace("맛집", PlaceType.RESTAURANT, "서울시 강남구")

            // when & then
            assertTrue(placeRepository.existsByNameAndAddress("맛집", "서울시 강남구"))
        }

        @Test
        fun existsByNameAndAddress_whenSameAddressDifferentName_shouldReturnFalse() {
            // given
            createPlace("맛집A", PlaceType.RESTAURANT, "서울시 강남구")

            // when & then
            assertFalse(placeRepository.existsByNameAndAddress("맛집B", "서울시 강남구"))
        }

        @Test
        fun existsByNameAndAddress_whenSameNameDifferentAddress_shouldReturnFalse() {
            // given
            createPlace("맛집", PlaceType.RESTAURANT, "서울시 강남구")

            // when & then
            assertFalse(placeRepository.existsByNameAndAddress("맛집", "서울시 서초구"))
        }
    }

    @Nested
    @DisplayName("findWithinBounds")
    inner class FindWithinBounds {

        @Test
        fun findWithinBounds_whenPlacesInsideBounds_shouldReturnThem() {
            // given
            createPlace("강남 맛집", PlaceType.RESTAURANT, lat = 37.5, lng = 127.0)
            createPlace("강북 맛집", PlaceType.RESTAURANT, lat = 37.6, lng = 127.0)
            createPlace("부산 맛집", PlaceType.RESTAURANT, lat = 35.1, lng = 129.0)

            // when
            val result = placeRepository.findWithinBounds(37.4, 126.8, 37.7, 127.2)

            // then
            assertEquals(2, result.size)
        }

        @Test
        fun findWithinBounds_whenPlaceOnBoundary_shouldIncludeIt() {
            // given
            createPlace("경계 맛집", PlaceType.RESTAURANT, lat = 37.4, lng = 126.8)

            // when
            val result = placeRepository.findWithinBounds(37.4, 126.8, 37.7, 127.2)

            // then
            assertEquals(1, result.size)
        }
    }

    @Nested
    @DisplayName("findByTypeWithinBounds")
    inner class FindByTypeWithinBounds {

        @Test
        fun findByTypeWithinBounds_whenBothConditionsMatch_shouldReturnFilteredPlaces() {
            // given
            createPlace("강남 맛집", PlaceType.RESTAURANT, lat = 37.5, lng = 127.0)
            createPlace("강남 놀이터", PlaceType.KIDS_PLAYGROUND, lat = 37.5, lng = 127.0)
            createPlace("부산 맛집", PlaceType.RESTAURANT, lat = 35.1, lng = 129.0)

            // when
            val result = placeRepository.findByTypeWithinBounds(
                PlaceType.RESTAURANT, 37.4, 126.8, 37.7, 127.2
            )

            // then
            assertEquals(1, result.size)
            assertEquals("강남 맛집", result[0].name)
        }
    }

    @Nested
    @DisplayName("Soft Delete")
    inner class SoftDelete {

        @Test
        fun delete_whenCalled_shouldExcludeFromFindById() {
            // given
            val place = createPlace("삭제할 맛집", PlaceType.RESTAURANT)

            // when
            placeRepository.delete(place)
            entityManager.flush()
            entityManager.clear()

            // then
            val result = placeRepository.findById(place.id)
            assertFalse(result.isPresent)
        }

        @Test
        fun delete_whenCalled_shouldExcludeFromFindAll() {
            // given
            val place1 = createPlace("맛집1", PlaceType.RESTAURANT)
            createPlace("맛집2", PlaceType.RESTAURANT)

            // when
            placeRepository.delete(place1)
            entityManager.flush()
            entityManager.clear()

            // then
            val result = placeRepository.findAll()
            assertEquals(1, result.size)
            assertEquals("맛집2", result[0].name)
        }
    }

    @Nested
    @DisplayName("findPlacesEligibleForGoogleSync")
    inner class FindPlacesEligibleForGoogleSync {

        private val maxFailCount = 3
        private val cutoffDate: LocalDateTime = LocalDateTime.now().minusMonths(6)

        @Test
        fun findPlacesEligibleForGoogleSync_whenRatingNullAndFailCountZero_shouldInclude() {
            // given
            createPlace("신규 맛집", PlaceType.RESTAURANT)

            // when
            val result = placeRepository.findPlacesEligibleForGoogleSync(maxFailCount, cutoffDate, PageRequest.of(0, 100))

            // then
            assertEquals(1, result.size)
            assertEquals("신규 맛집", result[0].name)
        }

        @Test
        fun findPlacesEligibleForGoogleSync_whenFailCountExceedsMax_shouldExclude() {
            // given
            createPlace("실패 맛집", PlaceType.RESTAURANT, googleRatingFailCount = 3)

            // when
            val result = placeRepository.findPlacesEligibleForGoogleSync(maxFailCount, cutoffDate, PageRequest.of(0, 100))

            // then
            assertEquals(0, result.size)
        }

        @Test
        fun findPlacesEligibleForGoogleSync_whenUpdatedAt7MonthsAgo_shouldInclude() {
            // given
            createPlace(
                "오래된 맛집", PlaceType.RESTAURANT,
                googleRating = 4.5,
                googleRatingUpdatedAt = LocalDateTime.now().minusMonths(7)
            )

            // when
            val result = placeRepository.findPlacesEligibleForGoogleSync(maxFailCount, cutoffDate, PageRequest.of(0, 100))

            // then
            assertEquals(1, result.size)
            assertEquals("오래된 맛집", result[0].name)
        }

        @Test
        fun findPlacesEligibleForGoogleSync_whenUpdatedAt3MonthsAgo_shouldExclude() {
            // given
            createPlace(
                "최신 맛집", PlaceType.RESTAURANT,
                googleRating = 4.5,
                googleRatingUpdatedAt = LocalDateTime.now().minusMonths(3)
            )

            // when
            val result = placeRepository.findPlacesEligibleForGoogleSync(maxFailCount, cutoffDate, PageRequest.of(0, 100))

            // then
            assertEquals(0, result.size)
        }

        @Test
        fun findPlacesEligibleForGoogleSync_whenRatingExistsButUpdatedAtNull_shouldInclude() {
            // given (마이그레이션 케이스: 기존 동기화됐지만 타임스탬프 없음)
            createPlace(
                "마이그레이션 맛집", PlaceType.RESTAURANT,
                googleRating = 4.0,
                googleRatingUpdatedAt = null
            )

            // when
            val result = placeRepository.findPlacesEligibleForGoogleSync(maxFailCount, cutoffDate, PageRequest.of(0, 100))

            // then
            assertEquals(1, result.size)
            assertEquals("마이그레이션 맛집", result[0].name)
        }
    }

    @Nested
    @DisplayName("searchRecentPlaces")
    inner class SearchRecentPlaces {

        private val start: LocalDateTime = LocalDateTime.now().minusMonths(3)
        private val end: LocalDateTime = LocalDateTime.now().plusDays(1)

        private fun search(
            keyword: String? = null,
            type: String? = null,
            grade: Int? = null,
            includeDeleted: Boolean = true,
            page: Int = 0,
            size: Int = 10
        ) = placeRepository.searchRecentPlaces(start, end, keyword, type, grade, includeDeleted, PageRequest.of(page, size))

        @Test
        fun searchRecentPlaces_whenWithinRange_shouldInclude() {
            // given
            createPlace("최근 맛집", PlaceType.RESTAURANT, createdAt = LocalDateTime.now().minusDays(1))

            // when
            val result = search()

            // then
            assertEquals(1, result.totalElements)
            assertEquals("최근 맛집", result.content[0].name)
        }

        @Test
        fun searchRecentPlaces_whenBeforeStart_shouldExclude() {
            // given
            createPlace("오래된 맛집", PlaceType.RESTAURANT, createdAt = LocalDateTime.now().minusMonths(4))

            // when & then
            assertEquals(0, search().totalElements)
        }

        @Test
        fun searchRecentPlaces_whenAfterEnd_shouldExclude() {
            // given
            createPlace("미래 맛집", PlaceType.RESTAURANT, createdAt = LocalDateTime.now().plusDays(2))

            // when & then
            assertEquals(0, search().totalElements)
        }

        @Test
        fun searchRecentPlaces_whenPlaceSoftDeletedAndIncludeDeletedTrue_shouldStillInclude() {
            // given (@SQLRestriction은 findAll/findById 등 일반 쿼리에만 적용, 네이티브 쿼리는 우회)
            val place = createPlace("삭제될 맛집", PlaceType.RESTAURANT, createdAt = LocalDateTime.now().minusDays(1))
            placeRepository.delete(place)
            entityManager.flush()
            entityManager.clear()

            // when
            val result = search(includeDeleted = true)

            // then
            assertEquals(1, result.totalElements)
            assertEquals("삭제될 맛집", result.content[0].name)
            assertTrue(result.content[0].deletedAt != null)
        }

        @Test
        fun searchRecentPlaces_whenPlaceSoftDeletedAndIncludeDeletedFalse_shouldExclude() {
            // given
            val place = createPlace("삭제될 맛집", PlaceType.RESTAURANT, createdAt = LocalDateTime.now().minusDays(1))
            createPlace("정상 맛집", PlaceType.RESTAURANT, createdAt = LocalDateTime.now().minusDays(1))
            placeRepository.delete(place)
            entityManager.flush()
            entityManager.clear()

            // when
            val result = search(includeDeleted = false)

            // then
            assertEquals(1, result.totalElements)
            assertEquals("정상 맛집", result.content[0].name)
        }

        @Test
        fun searchRecentPlaces_whenKeywordMatchesName_shouldFilter() {
            // given
            createPlace("스타벅스 강남점", PlaceType.RESTAURANT)
            createPlace("이디야 커피", PlaceType.RESTAURANT)

            // when
            val result = search(keyword = "스타벅스")

            // then
            assertEquals(1, result.totalElements)
            assertEquals("스타벅스 강남점", result.content[0].name)
        }

        @Test
        fun searchRecentPlaces_whenKeywordMatchesAddress_shouldFilter() {
            // given
            createPlace("맛집A", PlaceType.RESTAURANT, address = "서울시 강남구")
            createPlace("맛집B", PlaceType.RESTAURANT, address = "서울시 서초구")

            // when
            val result = search(keyword = "강남")

            // then
            assertEquals(1, result.totalElements)
            assertEquals("맛집A", result.content[0].name)
        }

        @Test
        fun searchRecentPlaces_whenKeywordIsCaseInsensitive_shouldMatch() {
            // given
            createPlace("Cafe Onion", PlaceType.RESTAURANT)

            // when
            val result = search(keyword = "onion")

            // then
            assertEquals(1, result.totalElements)
        }

        @Test
        fun searchRecentPlaces_whenTypeSpecified_shouldFilter() {
            // given
            createPlace("맛집1", PlaceType.RESTAURANT)
            createPlace("놀이터1", PlaceType.KIDS_PLAYGROUND)

            // when
            val result = search(type = PlaceType.KIDS_PLAYGROUND.name)

            // then
            assertEquals(1, result.totalElements)
            assertEquals("놀이터1", result.content[0].name)
        }

        @Test
        fun searchRecentPlaces_whenGradeSpecified_shouldFilter() {
            // given
            createPlace("1등급 맛집", PlaceType.RESTAURANT, grade = 1)
            createPlace("3등급 맛집", PlaceType.RESTAURANT, grade = 3)

            // when
            val result = search(grade = 1)

            // then
            assertEquals(1, result.totalElements)
            assertEquals("1등급 맛집", result.content[0].name)
        }

        @Test
        fun searchRecentPlaces_whenMultiplePlaces_shouldOrderByCreatedAtDescAndPaginate() {
            // given
            createPlace("첫번째", PlaceType.RESTAURANT, createdAt = LocalDateTime.now().minusDays(3))
            createPlace("두번째", PlaceType.RESTAURANT, createdAt = LocalDateTime.now().minusDays(2))
            createPlace("세번째", PlaceType.RESTAURANT, createdAt = LocalDateTime.now().minusDays(1))

            // when
            val firstPage = search(size = 2)
            val secondPage = search(page = 1, size = 2)

            // then
            assertEquals(3, firstPage.totalElements)
            assertEquals(2, firstPage.totalPages)
            assertEquals(listOf("세번째", "두번째"), firstPage.content.map { it.name })
            assertEquals(listOf("첫번째"), secondPage.content.map { it.name })
        }

        @Test
        fun searchRecentPlaces_whenKeywordContainsEscapedPercent_shouldMatchLiterally() {
            // given (서비스 계층이 %를 \%로 이스케이프해서 넘기는 것과 동일한 입력)
            createPlace("50% 할인 맛집", PlaceType.RESTAURANT)
            createPlace("일반 맛집", PlaceType.RESTAURANT)

            // when
            val result = search(keyword = "50\\%")

            // then
            assertEquals(1, result.totalElements)
            assertEquals("50% 할인 맛집", result.content[0].name)
        }

        @Test
        fun searchRecentPlaces_whenKeywordContainsEscapedUnderscore_shouldMatchLiteralUnderscoreOnly() {
            // given: 이스케이프 없이 "_"를 그대로 LIKE에 넣으면 임의의 한 글자와 매치돼 전부 걸림
            //       (\_로 이스케이프하면 리터럴 밑줄이 있는 것만 매치돼야 함)
            createPlace("특_이맛집", PlaceType.RESTAURANT)
            createPlace("일반맛집", PlaceType.RESTAURANT)

            // when
            val result = search(keyword = "\\_")

            // then
            assertEquals(1, result.totalElements)
            assertEquals("특_이맛집", result.content[0].name)
        }

        @Test
        fun searchRecentPlaces_whenMultipleFiltersCombined_shouldApplyAllTogether() {
            // given
            createPlace("강남 스타벅스", PlaceType.RESTAURANT, address = "서울시 강남구", grade = 1)
            createPlace("강남 이디야", PlaceType.RESTAURANT, address = "서울시 강남구", grade = 3)
            createPlace("서초 스타벅스", PlaceType.RESTAURANT, address = "서울시 서초구", grade = 1)
            createPlace("강남 놀이터", PlaceType.KIDS_PLAYGROUND, address = "서울시 강남구", grade = 1)

            // when: 키워드(주소)="강남" + 유형=RESTAURANT + 등급=1 을 동시에
            val result = search(keyword = "강남", type = PlaceType.RESTAURANT.name, grade = 1)

            // then
            assertEquals(1, result.totalElements)
            assertEquals("강남 스타벅스", result.content[0].name)
        }
    }

    @Nested
    @DisplayName("findByIdIncludingDeleted")
    inner class FindByIdIncludingDeleted {

        @Test
        fun findByIdIncludingDeleted_whenPlaceSoftDeleted_shouldStillReturnIt() {
            // given
            val place = createPlace("삭제될 맛집", PlaceType.RESTAURANT)
            placeRepository.delete(place)
            entityManager.flush()
            entityManager.clear()

            // when
            val result = placeRepository.findByIdIncludingDeleted(place.id)

            // then
            assertEquals("삭제될 맛집", result?.name)
            assertTrue(result?.deletedAt != null)
        }

        @Test
        fun findByIdIncludingDeleted_whenIdNotExists_shouldReturnNull() {
            assertEquals(null, placeRepository.findByIdIncludingDeleted(999999L))
        }
    }

    private fun createPlace(
        name: String,
        type: PlaceType,
        address: String = "서울시 테스트구",
        lat: Double = 37.5,
        lng: Double = 127.0,
        grade: Int = 1,
        googleRating: Double? = null,
        googleRatingFailCount: Int = 0,
        googleRatingUpdatedAt: LocalDateTime? = null,
        createdAt: LocalDateTime = LocalDateTime.now()
    ): Place {
        val place = Place(
            name = name,
            type = type,
            address = address,
            latitude = lat,
            longitude = lng,
            grade = grade,
            googleRating = googleRating,
            googleRatingFailCount = googleRatingFailCount,
            googleRatingUpdatedAt = googleRatingUpdatedAt,
            createdAt = createdAt
        )
        entityManager.persist(place)
        entityManager.flush()
        return place
    }
}
