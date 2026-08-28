package com.ourspots.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ourspots.domain.auth.controller.LoginRequest
import com.ourspots.domain.place.entity.Place
import com.ourspots.domain.place.entity.PlaceType
import com.ourspots.domain.place.repository.PlaceRepository
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.cache.CacheManager
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MapControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var placeRepository: PlaceRepository

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

    @Autowired
    private lateinit var cacheManager: CacheManager

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
        // deleteAll()은 @SQLDelete 때문에 소프트 삭제(UPDATE)로 바뀌고, deleteAllInBatch()도 @SQLRestriction이 적용돼
        // "deleted_at IS NULL"인 행만 지워짐(이미 소프트 삭제된 행은 안 지워짐) → JDBC로 직접 물리 삭제
        jdbcTemplate.update("DELETE FROM places")
        // getMarkers()가 @Cacheable이라 이전 테스트에서 채운 캐시가 남아있으면 새로 저장한 데이터가 안 보임
        cacheManager.getCache("markers")?.clear()
    }

    @Nested
    @DisplayName("GET /api/map/markers")
    inner class GetMarkers {

        @Test
        fun getMarkers_whenNotAuthenticated_shouldExcludePersonalTypes() {
            createTestPlace("맛집", PlaceType.RESTAURANT)
            createTestPlace("나의 발자취", PlaceType.MY_FOOTPRINT)

            mockMvc.perform(get("/api/map/markers"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].type").value("RESTAURANT"))
        }

        @Test
        fun getMarkers_whenAuthenticated_shouldIncludePersonalTypes() {
            createTestPlace("맛집", PlaceType.RESTAURANT)
            createTestPlace("나의 발자취", PlaceType.MY_FOOTPRINT)

            mockMvc.perform(get("/api/map/markers").header("Authorization", "Bearer $authToken"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(2))
        }

        @Test
        fun getMarkers_withTypeFilter_shouldReturnOnlyMatchingType() {
            createTestPlace("맛집1", PlaceType.RESTAURANT)
            createTestPlace("아이 놀이터", PlaceType.KIDS_PLAYGROUND)

            mockMvc.perform(get("/api/map/markers?type=RESTAURANT"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].name").value("맛집1"))
        }

        @Test
        fun getMarkers_whenNotAuthenticated_shouldOnlyIncludeGrade1Restaurants() {
            createTestPlace("찐맛집", PlaceType.RESTAURANT, grade = 1)
            createTestPlace("괜찮은맛집", PlaceType.RESTAURANT, grade = 2)
            createTestPlace("무난한맛집", PlaceType.RESTAURANT, grade = 3)
            createTestPlace("놀이터 3등급", PlaceType.KIDS_PLAYGROUND, grade = 3)

            mockMvc.perform(get("/api/map/markers"))
                .andExpect(status().isOk)
                // 맛집은 1등급만 남고, 다른 공개 타입(아이 놀이터)은 등급 무관하게 그대로 노출
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[*].name").value(org.hamcrest.Matchers.containsInAnyOrder("찐맛집", "놀이터 3등급")))
        }

        @Test
        fun getMarkers_whenAuthenticated_shouldIncludeAllRestaurantGrades() {
            createTestPlace("찐맛집", PlaceType.RESTAURANT, grade = 1)
            createTestPlace("무난한맛집", PlaceType.RESTAURANT, grade = 3)

            mockMvc.perform(get("/api/map/markers").header("Authorization", "Bearer $authToken"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(2))
        }
    }

    @Nested
    @DisplayName("POST /api/map/markers/refresh")
    inner class RefreshMarkers {

        @Test
        fun refreshMarkers_whenNotAuthenticated_shouldReturn401() {
            mockMvc.perform(post("/api/map/markers/refresh"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun refreshMarkers_whenAuthenticated_shouldEvictCacheAndReturnLatestMarkers() {
            createTestPlace("맛집", PlaceType.RESTAURANT)
            // 캐시를 채워둠
            mockMvc.perform(get("/api/map/markers").header("Authorization", "Bearer $authToken"))
                .andExpect(jsonPath("$.data.length()").value(1))

            createTestPlace("추가 장소", PlaceType.RESTAURANT)

            mockMvc.perform(post("/api/map/markers/refresh").header("Authorization", "Bearer $authToken"))
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(2))
        }
    }

    // grade 기본값을 1로 둠 — 실제 데이터는 보통 등급이 있고, 비로그인 시 "맛집은 1등급만" 규칙이
    // DB 쿼리에 생겨서(PlaceRepository.findPublicMarkers) grade가 null이면 이 테스트들의 의도(타입 필터링/
    // 개인 카테고리 제외 검증)와 무관하게 걸러져버림 — 등급 자체를 검증하는 테스트는 별도로 grade를 명시함
    private fun createTestPlace(name: String, type: PlaceType, grade: Int? = 1): Place {
        return placeRepository.save(
            Place(name = name, type = type, address = "서울시 종로구", latitude = 37.5, longitude = 127.0, grade = grade)
        )
    }
}
