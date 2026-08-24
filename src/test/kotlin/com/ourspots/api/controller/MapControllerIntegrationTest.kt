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

    private fun createTestPlace(name: String, type: PlaceType): Place {
        return placeRepository.save(
            Place(name = name, type = type, address = "서울시 종로구", latitude = 37.5, longitude = 127.0)
        )
    }
}
