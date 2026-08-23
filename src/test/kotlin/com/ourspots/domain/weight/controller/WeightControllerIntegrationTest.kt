package com.ourspots.domain.weight.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ourspots.api.dto.WeightRecordUpsertRequest
import com.ourspots.domain.auth.controller.LoginRequest
import com.ourspots.domain.weight.entity.WeightRecord
import com.ourspots.domain.weight.repository.WeightRecordRepository
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
import java.time.LocalDate

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WeightControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var weightRecordRepository: WeightRecordRepository

    @Autowired
    private lateinit var cacheManager: CacheManager

    @Autowired
    private lateinit var jdbcTemplate: JdbcTemplate

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
        jdbcTemplate.update("DELETE FROM weight_records")
        // 테스트 데이터를 리포지토리에 직접 넣는 경우가 많아 서비스 캐시(@CacheEvict)를 못 타므로 매번 직접 비움
        cacheManager.getCache("weightRecords")?.clear()
    }

    @Nested
    @DisplayName("GET /api/weights")
    inner class GetAllRecords {

        @Test
        fun getAllRecords_whenNotAuthenticated_shouldReturn401() {
            mockMvc.perform(get("/api/weights"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun getAllRecords_whenAuthenticated_shouldReturnRecords() {
            createTestRecord(LocalDate.of(2026, 8, 18), 71.2)
            createTestRecord(LocalDate.of(2026, 8, 19), 70.9)

            mockMvc.perform(
                get("/api/weights").header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].weightKg").value(70.9))
        }
    }

    @Nested
    @DisplayName("POST /api/weights")
    inner class UpsertRecord {

        @Test
        fun upsertRecord_whenNotAuthenticated_shouldReturn401() {
            val request = WeightRecordUpsertRequest(recordedDate = LocalDate.now(), weightKg = 70.0)

            mockMvc.perform(
                post("/api/weights")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun upsertRecord_whenAuthenticated_shouldReturnSavedRecord() {
            val request = WeightRecordUpsertRequest(recordedDate = LocalDate.of(2026, 8, 19), weightKg = 70.9)

            mockMvc.perform(
                post("/api/weights")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.weightKg").value(70.9))
        }

        @Test
        fun upsertRecord_whenSameDateTwice_shouldUpdateExistingRecord() {
            val date = LocalDate.of(2026, 8, 19)
            val first = WeightRecordUpsertRequest(recordedDate = date, weightKg = 70.0)
            val second = WeightRecordUpsertRequest(recordedDate = date, weightKg = 71.5)

            mockMvc.perform(
                post("/api/weights")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(first))
            )
            mockMvc.perform(
                post("/api/weights")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(second))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.weightKg").value(71.5))

            mockMvc.perform(get("/api/weights").header("Authorization", "Bearer $authToken"))
                .andExpect(jsonPath("$.data.length()").value(1))
        }

        @Test
        fun upsertRecord_afterPriorGetCachedResult_shouldInvalidateCacheAndReflectNewRecord() {
            createTestRecord(LocalDate.of(2026, 8, 18), 70.0)
            // 이 호출로 캐시가 채워짐
            mockMvc.perform(get("/api/weights").header("Authorization", "Bearer $authToken"))
                .andExpect(jsonPath("$.data.length()").value(1))

            val request = WeightRecordUpsertRequest(recordedDate = LocalDate.of(2026, 8, 19), weightKg = 71.0)
            mockMvc.perform(
                post("/api/weights")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            ).andExpect(status().isOk)

            // 캐시가 제대로 비워졌다면 여기서 2건이 보여야 함
            mockMvc.perform(get("/api/weights").header("Authorization", "Bearer $authToken"))
                .andExpect(jsonPath("$.data.length()").value(2))
        }

        @Test
        fun upsertRecord_whenWeightOutOfRange_shouldReturn400() {
            val request = WeightRecordUpsertRequest(recordedDate = LocalDate.now(), weightKg = 500.0)

            mockMvc.perform(
                post("/api/weights")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("DELETE /api/weights/{id}")
    inner class DeleteRecord {

        @Test
        fun deleteRecord_whenAuthenticated_shouldSoftDeleteRecord() {
            val record = createTestRecord(LocalDate.now(), 70.0)

            mockMvc.perform(
                delete("/api/weights/${record.id}")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNoContent)

            mockMvc.perform(get("/api/weights").header("Authorization", "Bearer $authToken"))
                .andExpect(jsonPath("$.data.length()").value(0))
        }

        @Test
        fun deleteRecord_afterPriorGetCachedResult_shouldInvalidateCache() {
            val record = createTestRecord(LocalDate.now(), 70.0)
            // 이 호출로 캐시가 채워짐
            mockMvc.perform(get("/api/weights").header("Authorization", "Bearer $authToken"))
                .andExpect(jsonPath("$.data.length()").value(1))

            mockMvc.perform(
                delete("/api/weights/${record.id}")
                    .header("Authorization", "Bearer $authToken")
            ).andExpect(status().isNoContent)

            // 캐시가 제대로 비워졌다면 여기서 0건이 보여야 함
            mockMvc.perform(get("/api/weights").header("Authorization", "Bearer $authToken"))
                .andExpect(jsonPath("$.data.length()").value(0))
        }

        @Test
        fun deleteRecord_whenNotAuthenticated_shouldReturn401() {
            val record = createTestRecord(LocalDate.now(), 70.0)

            mockMvc.perform(delete("/api/weights/${record.id}"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun deleteRecord_whenNotFound_shouldReturn404() {
            mockMvc.perform(
                delete("/api/weights/99999")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    private fun createTestRecord(date: LocalDate, weightKg: Double): WeightRecord {
        return weightRecordRepository.save(
            WeightRecord(recordedDate = date, weightKg = weightKg)
        )
    }
}
