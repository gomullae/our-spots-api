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
import org.springframework.http.MediaType
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
        weightRecordRepository.deleteAll()
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
