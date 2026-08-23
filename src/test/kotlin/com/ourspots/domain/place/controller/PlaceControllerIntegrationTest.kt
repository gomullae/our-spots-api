package com.ourspots.domain.place.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ourspots.api.dto.PlaceCreateRequest
import com.ourspots.api.dto.PlaceUpdateRequest
import com.ourspots.domain.auth.controller.LoginRequest
import com.ourspots.domain.place.entity.Place
import com.ourspots.domain.place.entity.PlaceType
import com.ourspots.domain.place.repository.PlaceRepository
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PlaceControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var placeRepository: PlaceRepository

    private lateinit var authToken: String

    @BeforeAll
    fun setUpAuth() {
        val loginRequest = LoginRequest("test-admin-password")
        val result = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest))
        )
            .andExpect(status().isOk)
            .andReturn()

        val response = objectMapper.readTree(result.response.contentAsString)
        authToken = response.get("data").get("token").asText()
    }

    @BeforeEach
    fun setUp() {
        placeRepository.deleteAll()
    }

    @Nested
    @DisplayName("GET /api/places")
    inner class GetAllPlaces {

        @Test
        fun getAllPlaces_whenPlacesExist_shouldReturnAllPlaces() {
            // given
            createTestPlace("맛집1", PlaceType.RESTAURANT)
            createTestPlace("놀이터1", PlaceType.KIDS_PLAYGROUND)

            // when & then
            mockMvc.perform(get("/api/places"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2))
        }

        @Test
        fun getAllPlaces_whenTypeSpecified_shouldReturnFilteredPlaces() {
            // given
            createTestPlace("맛집1", PlaceType.RESTAURANT)
            createTestPlace("맛집2", PlaceType.RESTAURANT)
            createTestPlace("놀이터1", PlaceType.KIDS_PLAYGROUND)

            // when & then
            mockMvc.perform(get("/api/places").param("type", "RESTAURANT"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(2))
        }
    }

    @Nested
    @DisplayName("GET /api/places/recent")
    inner class GetRecentPlaces {

        @Test
        fun getRecentPlaces_whenNotAuthenticated_shouldReturn401() {
            createTestPlace("맛집1", PlaceType.RESTAURANT)

            mockMvc.perform(get("/api/places/recent"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun getRecentPlaces_whenAuthenticated_shouldReturnPagedResult() {
            // given
            repeat(12) { createTestPlace("맛집$it", PlaceType.RESTAURANT) }

            // when & then
            mockMvc.perform(
                get("/api/places/recent")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(12))
                .andExpect(jsonPath("$.data.totalPages").value(2))
        }

        @Test
        fun getRecentPlaces_whenPlaceOlderThanThreeMonths_shouldExcludeIt() {
            // given
            createTestPlace("최근 맛집", PlaceType.RESTAURANT)
            placeRepository.save(
                Place(
                    name = "오래된 맛집",
                    type = PlaceType.RESTAURANT,
                    address = "서울시 테스트구",
                    latitude = 37.5,
                    longitude = 127.0,
                    createdAt = LocalDateTime.now().minusMonths(4)
                )
            )

            // when & then
            mockMvc.perform(
                get("/api/places/recent")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("최근 맛집"))
        }

        @Test
        fun getRecentPlaces_whenDateRangeSpecified_shouldFilterByRange() {
            // given
            placeRepository.save(
                Place(
                    name = "범위 밖(이전) 맛집",
                    type = PlaceType.RESTAURANT,
                    address = "서울시 테스트구",
                    latitude = 37.5,
                    longitude = 127.0,
                    createdAt = LocalDateTime.of(2026, 5, 31, 23, 59)
                )
            )
            placeRepository.save(
                Place(
                    name = "범위 안 맛집",
                    type = PlaceType.RESTAURANT,
                    address = "서울시 테스트구",
                    latitude = 37.5,
                    longitude = 127.0,
                    createdAt = LocalDateTime.of(2026, 6, 15, 12, 0)
                )
            )
            placeRepository.save(
                Place(
                    name = "범위 밖(이후) 맛집",
                    type = PlaceType.RESTAURANT,
                    address = "서울시 테스트구",
                    latitude = 37.5,
                    longitude = 127.0,
                    createdAt = LocalDateTime.of(2026, 7, 1, 0, 0)
                )
            )

            // when & then
            mockMvc.perform(
                get("/api/places/recent")
                    .param("startDate", "2026-06-01")
                    .param("endDate", "2026-06-30")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("범위 안 맛집"))
        }

        @Test
        fun getRecentPlaces_whenPlaceSoftDeleted_shouldIncludeWithDeletedAt() {
            // given
            val place = createTestPlace("삭제될 맛집", PlaceType.RESTAURANT)
            mockMvc.perform(
                delete("/api/places/${place.id}")
                    .header("Authorization", "Bearer $authToken")
            ).andExpect(status().isNoContent)

            // when & then
            mockMvc.perform(
                get("/api/places/recent")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content[0].name").value("삭제될 맛집"))
                .andExpect(jsonPath("$.data.content[0].deletedAt").isNotEmpty)
        }

        @Test
        fun getRecentPlaces_whenIncludeDeletedFalse_shouldExcludeDeletedPlace() {
            // given
            val place = createTestPlace("삭제될 맛집", PlaceType.RESTAURANT)
            createTestPlace("정상 맛집", PlaceType.RESTAURANT)
            mockMvc.perform(
                delete("/api/places/${place.id}")
                    .header("Authorization", "Bearer $authToken")
            ).andExpect(status().isNoContent)

            // when & then
            mockMvc.perform(
                get("/api/places/recent")
                    .param("includeDeleted", "false")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("정상 맛집"))
        }

        @Test
        fun getRecentPlaces_whenKeywordSpecified_shouldFilterByNameOrAddress() {
            // given
            createTestPlace("스타벅스 강남점", PlaceType.RESTAURANT)
            createTestPlace("이디야 커피", PlaceType.RESTAURANT)

            // when & then
            mockMvc.perform(
                get("/api/places/recent")
                    .param("keyword", "스타벅스")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("스타벅스 강남점"))
        }

        @Test
        fun getRecentPlaces_whenTypeSpecified_shouldFilterByType() {
            // given
            createTestPlace("맛집1", PlaceType.RESTAURANT)
            createTestPlace("놀이터1", PlaceType.KIDS_PLAYGROUND)

            // when & then
            mockMvc.perform(
                get("/api/places/recent")
                    .param("type", "KIDS_PLAYGROUND")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("놀이터1"))
        }

        @Test
        fun getRecentPlaces_whenGradeSpecified_shouldFilterByGrade() {
            // given
            placeRepository.save(
                Place(
                    name = "1등급 맛집",
                    type = PlaceType.RESTAURANT,
                    address = "서울시 테스트구",
                    latitude = 37.5,
                    longitude = 127.0,
                    grade = 1
                )
            )
            placeRepository.save(
                Place(
                    name = "3등급 맛집",
                    type = PlaceType.RESTAURANT,
                    address = "서울시 테스트구",
                    latitude = 37.5,
                    longitude = 127.0,
                    grade = 3
                )
            )

            // when & then
            mockMvc.perform(
                get("/api/places/recent")
                    .param("grade", "1")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("1등급 맛집"))
        }

        @Test
        fun getRecentPlaces_whenGradeOutOfDomainRange_shouldReturnEmptyWithoutError() {
            // given
            createTestPlace("맛집", PlaceType.RESTAURANT)

            // when & then (grade는 원래 1~3이지만, 잘못된 값이 와도 그냥 0건으로 처리되어야지 500이 나면 안 됨)
            mockMvc.perform(
                get("/api/places/recent")
                    .param("grade", "9")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(0))
        }

        @Test
        fun getRecentPlaces_whenKeywordContainsWildcardChars_shouldNotMatchEverything() {
            // given: 이스케이프가 안 되면 "_"가 임의의 한 글자 와일드카드로 동작해 전부 매치돼버림
            createTestPlace("특_이맛집", PlaceType.RESTAURANT)
            createTestPlace("일반맛집", PlaceType.RESTAURANT)

            // when & then
            mockMvc.perform(
                get("/api/places/recent")
                    .param("keyword", "_")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("특_이맛집"))
        }

        @Test
        fun getRecentPlaces_whenMultipleFiltersCombined_shouldApplyAllTogether() {
            // given
            createTestPlace("강남 스타벅스", PlaceType.RESTAURANT, "서울시 강남구")
            createTestPlace("강남 이디야", PlaceType.RESTAURANT, "서울시 강남구")
            createTestPlace("서초 스타벅스", PlaceType.RESTAURANT, "서울시 서초구")

            // when & then
            mockMvc.perform(
                get("/api/places/recent")
                    .param("keyword", "강남")
                    .param("type", "RESTAURANT")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(2))
        }

        @Test
        fun getRecentPlaces_whenSizeIsExcessivelyLarge_shouldNotErrorAndStayCapped() {
            // given
            repeat(3) { createTestPlace("맛집$it", PlaceType.RESTAURANT) }

            // when & then (내부적으로 최대 100으로 캡되므로 에러 없이 정상 응답해야 함)
            mockMvc.perform(
                get("/api/places/recent")
                    .param("size", "999999")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content.length()").value(3))
        }
    }

    @Nested
    @DisplayName("POST /api/places/{id}/restore")
    inner class RestorePlace {

        @Test
        fun restorePlace_whenAuthenticated_shouldClearDeletedAt() {
            // given
            val place = createTestPlace("복구될 맛집", PlaceType.RESTAURANT)
            mockMvc.perform(
                delete("/api/places/${place.id}")
                    .header("Authorization", "Bearer $authToken")
            ).andExpect(status().isNoContent)

            // when & then
            mockMvc.perform(
                post("/api/places/${place.id}/restore")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.name").value("복구될 맛집"))
                .andExpect(jsonPath("$.data.deletedAt").value(org.hamcrest.Matchers.nullValue()))

            mockMvc.perform(get("/api/places/${place.id}"))
                .andExpect(status().isOk)
        }

        @Test
        fun restorePlace_whenNotAuthenticated_shouldReturn401() {
            val place = createTestPlace("맛집", PlaceType.RESTAURANT)

            mockMvc.perform(post("/api/places/${place.id}/restore"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun restorePlace_whenIdNotExists_shouldReturn404() {
            mockMvc.perform(
                post("/api/places/99999/restore")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /api/places/{id}/sync-google")
    inner class SyncGoogleRating {

        @Test
        fun syncGoogleRating_whenNotAuthenticated_shouldReturn401() {
            val place = createTestPlace("맛집", PlaceType.RESTAURANT)

            mockMvc.perform(post("/api/places/${place.id}/sync-google"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun syncGoogleRating_whenApiKeyNotConfigured_shouldReturn503() {
            // given: 테스트 환경에는 GOOGLE_API_KEY가 설정되어 있지 않음
            val place = createTestPlace("맛집", PlaceType.RESTAURANT)

            // when & then
            mockMvc.perform(
                post("/api/places/${place.id}/sync-google")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isServiceUnavailable)
                .andExpect(jsonPath("$.success").value(false))
        }
    }

    @Nested
    @DisplayName("GET /api/places/{id}")
    inner class GetPlace {

        @Test
        fun getPlace_whenIdExists_shouldReturnPlace() {
            // given
            val place = createTestPlace("테스트 맛집", PlaceType.RESTAURANT)

            // when & then
            mockMvc.perform(get("/api/places/${place.id}"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.name").value("테스트 맛집"))
                .andExpect(jsonPath("$.data.type").value("RESTAURANT"))
        }

        @Test
        fun getPlace_whenIdNotExists_shouldReturn404() {
            mockMvc.perform(get("/api/places/99999"))
                .andExpect(status().isNotFound)
                .andExpect(jsonPath("$.success").value(false))
        }
    }

    @Nested
    @DisplayName("POST /api/places")
    inner class CreatePlace {

        @Test
        fun createPlace_whenAuthenticated_shouldReturnCreatedPlace() {
            // given
            val request = PlaceCreateRequest(
                name = "새 맛집",
                type = PlaceType.RESTAURANT,
                address = "서울시 강남구",
                latitude = 37.5,
                longitude = 127.0,
                grade = 1
            )

            // when & then
            mockMvc.perform(
                post("/api/places")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.name").value("새 맛집"))
        }

        @Test
        fun createPlace_whenNotAuthenticated_shouldReturn401() {
            val request = PlaceCreateRequest(
                name = "새 맛집",
                type = PlaceType.RESTAURANT,
                address = "서울시 강남구",
                latitude = 37.5,
                longitude = 127.0
            )

            mockMvc.perform(
                post("/api/places")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun createPlace_whenLatitudeOutOfRange_shouldReturn400() {
            val request = PlaceCreateRequest(
                name = "맛집",
                type = PlaceType.RESTAURANT,
                address = "서울시 강남구",
                latitude = 91.0,
                longitude = 127.0
            )

            mockMvc.perform(
                post("/api/places")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun createPlace_whenGradeOutOfRange_shouldReturn400() {
            val request = PlaceCreateRequest(
                name = "맛집",
                type = PlaceType.RESTAURANT,
                address = "서울시 강남구",
                latitude = 37.5,
                longitude = 127.0,
                grade = 5
            )

            mockMvc.perform(
                post("/api/places")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun createPlace_whenDuplicateNameAndAddress_shouldReturn409() {
            // given
            createTestPlace("기존 맛집", PlaceType.RESTAURANT, "서울시 강남구")

            val request = PlaceCreateRequest(
                name = "기존 맛집",
                type = PlaceType.RESTAURANT,
                address = "서울시 강남구",
                latitude = 37.5,
                longitude = 127.0
            )

            // when & then
            mockMvc.perform(
                post("/api/places")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isConflict)
        }
    }

    @Nested
    @DisplayName("PUT /api/places/{id}")
    inner class UpdatePlace {

        @Test
        fun updatePlace_whenAuthenticated_shouldReturnUpdatedPlace() {
            // given
            val place = createTestPlace("기존 맛집", PlaceType.RESTAURANT)
            val request = PlaceUpdateRequest(name = "수정된 맛집")

            // when & then
            mockMvc.perform(
                put("/api/places/${place.id}")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.name").value("수정된 맛집"))
        }

        @Test
        fun updatePlace_whenEmptyName_shouldReturn400() {
            val place = createTestPlace("맛집", PlaceType.RESTAURANT)
            val request = PlaceUpdateRequest(name = "")

            mockMvc.perform(
                put("/api/places/${place.id}")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun updatePlace_whenLatitudeOutOfRange_shouldReturn400() {
            val place = createTestPlace("맛집", PlaceType.RESTAURANT)
            val request = PlaceUpdateRequest(latitude = -91.0)

            mockMvc.perform(
                put("/api/places/${place.id}")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun updatePlace_whenNotAuthenticated_shouldReturn401() {
            val place = createTestPlace("맛집", PlaceType.RESTAURANT)
            val request = PlaceUpdateRequest(name = "수정")

            mockMvc.perform(
                put("/api/places/${place.id}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("DELETE /api/places/{id}")
    inner class DeletePlace {

        @Test
        fun deletePlace_whenAuthenticated_shouldSoftDeletePlace() {
            // given
            val place = createTestPlace("삭제할 맛집", PlaceType.RESTAURANT)

            // when & then
            mockMvc.perform(
                delete("/api/places/${place.id}")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNoContent)

            mockMvc.perform(get("/api/places/${place.id}"))
                .andExpect(status().isNotFound)
        }

        @Test
        fun deletePlace_whenNotAuthenticated_shouldReturn401() {
            val place = createTestPlace("맛집", PlaceType.RESTAURANT)

            mockMvc.perform(delete("/api/places/${place.id}"))
                .andExpect(status().isUnauthorized)
        }
    }

    private fun createTestPlace(
        name: String,
        type: PlaceType,
        address: String = "서울시 테스트구"
    ): Place {
        return placeRepository.save(
            Place(
                name = name,
                type = type,
                address = address,
                latitude = 37.5,
                longitude = 127.0,
                grade = 1
            )
        )
    }
}
