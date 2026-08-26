package com.ourspots.domain.expense.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ourspots.api.dto.ExpenseRecordRequest
import com.ourspots.domain.auth.controller.LoginRequest
import com.ourspots.domain.expense.entity.ExpenseCategory
import com.ourspots.domain.expense.entity.ExpenseRecord
import com.ourspots.domain.expense.entity.PaymentMethod
import com.ourspots.domain.expense.repository.ExpenseRecordRepository
import org.junit.jupiter.api.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*
import java.time.LocalDate
import kotlin.test.assertTrue

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpenseControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var expenseRecordRepository: ExpenseRecordRepository

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
        jdbcTemplate.update("DELETE FROM expense_records")
    }

    @Nested
    @DisplayName("GET /api/expenses")
    inner class GetRecords {

        @Test
        fun getRecords_whenNotAuthenticated_shouldReturn401() {
            mockMvc.perform(get("/api/expenses?startDate=2026-08-01&endDate=2026-08-31"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun getRecords_whenAuthenticated_shouldReturnRecordsWithinRange() {
            createTestRecord(LocalDate.of(2026, 7, 31))
            createTestRecord(LocalDate.of(2026, 8, 19))

            mockMvc.perform(
                get("/api/expenses?startDate=2026-08-01&endDate=2026-08-31")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].expenseDate").value("2026-08-19"))
        }

        @Test
        fun getRecords_byDefault_shouldExcludeSoftDeletedRecords() {
            val record = createTestRecord(LocalDate.of(2026, 8, 19))
            expenseRecordRepository.delete(record)

            mockMvc.perform(
                get("/api/expenses?startDate=2026-08-01&endDate=2026-08-31")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(0))
        }

        @Test
        fun getRecords_whenIncludeDeletedTrue_shouldIncludeSoftDeletedRecords() {
            val record = createTestRecord(LocalDate.of(2026, 8, 19))
            expenseRecordRepository.delete(record)

            mockMvc.perform(
                get("/api/expenses?startDate=2026-08-01&endDate=2026-08-31&includeDeleted=true")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].deletedAt").exists())
        }
    }

    @Nested
    @DisplayName("GET /api/expenses/meta")
    inner class GetMeta {

        @Test
        fun getMeta_whenNotAuthenticated_shouldReturn401() {
            mockMvc.perform(get("/api/expenses/meta"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun getMeta_whenNoRecords_shouldReturnZeroCountAndNullLastModified() {
            mockMvc.perform(
                get("/api/expenses/meta").header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.lastModified").doesNotExist())
        }

        @Test
        fun getMeta_whenAuthenticated_shouldReturnCountAndLastModified() {
            createTestRecord(LocalDate.of(2026, 8, 19))

            mockMvc.perform(
                get("/api/expenses/meta").header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.lastModified").exists())
        }

        @Test
        fun getMeta_afterDelete_shouldReflectDecreasedCount() {
            val record = createTestRecord(LocalDate.of(2026, 8, 19))
            expenseRecordRepository.delete(record)

            mockMvc.perform(
                get("/api/expenses/meta").header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.count").value(0))
        }
    }

    @Nested
    @DisplayName("POST /api/expenses")
    inner class CreateRecord {

        @Test
        fun createRecord_whenNotAuthenticated_shouldReturn401() {
            val request = validRequest()

            mockMvc.perform(
                post("/api/expenses")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun createRecord_whenAuthenticated_shouldCreateAndReturnRecord() {
            val request = validRequest()

            mockMvc.perform(
                post("/api/expenses")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.merchant").value("이마트"))
                .andExpect(jsonPath("$.data.amount").value(30000))
        }

        @Test
        fun createRecord_whenAmountIsZero_shouldReturn400() {
            val request = validRequest().copy(amount = 0)

            mockMvc.perform(
                post("/api/expenses")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun createRecord_whenMerchantBlank_shouldReturn400() {
            val request = validRequest().copy(merchant = "")

            mockMvc.perform(
                post("/api/expenses")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun createRecord_whenAmountNegative_shouldReturn400() {
            val request = validRequest().copy(amount = -1000)

            mockMvc.perform(
                post("/api/expenses")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun createRecord_whenMerchantOver100Chars_shouldReturn400() {
            val request = validRequest().copy(merchant = "이".repeat(101))

            mockMvc.perform(
                post("/api/expenses")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun createRecord_whenExpenseDateInFuture_shouldReturn400() {
            val request = validRequest().copy(expenseDate = LocalDate.now().plusDays(1))

            mockMvc.perform(
                post("/api/expenses")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("PUT /api/expenses/{id}")
    inner class UpdateRecord {

        @Test
        fun updateRecord_whenAuthenticated_shouldUpdateRecord() {
            val record = createTestRecord(LocalDate.of(2026, 8, 19))
            val request = validRequest().copy(merchant = "코스트코", amount = 80000)

            mockMvc.perform(
                put("/api/expenses/${record.id}")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.merchant").value("코스트코"))
                .andExpect(jsonPath("$.data.amount").value(80000))
        }

        @Test
        fun updateRecord_whenNotFound_shouldReturn404() {
            val request = validRequest()

            mockMvc.perform(
                put("/api/expenses/99999")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun updateRecord_whenNotAuthenticated_shouldReturn401() {
            val record = createTestRecord(LocalDate.of(2026, 8, 19))
            val request = validRequest()

            mockMvc.perform(
                put("/api/expenses/${record.id}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun updateRecord_shouldAdvanceUpdatedAtPastPreviousValue() {
            val record = createTestRecord(LocalDate.of(2026, 8, 19))
            val originalUpdatedAt = expenseRecordRepository.findById(record.id).get().updatedAt
            val request = validRequest().copy(merchant = "다이소")

            mockMvc.perform(
                put("/api/expenses/${record.id}")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)

            val afterUpdate = expenseRecordRepository.findById(record.id).get().updatedAt
            assertTrue(afterUpdate.isAfter(originalUpdatedAt))
        }
    }

    @Nested
    @DisplayName("DELETE /api/expenses/{id}")
    inner class DeleteRecord {

        @Test
        fun deleteRecord_whenAuthenticated_shouldSoftDeleteRecord() {
            val record = createTestRecord(LocalDate.of(2026, 8, 19))

            mockMvc.perform(
                delete("/api/expenses/${record.id}")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNoContent)

            mockMvc.perform(
                get("/api/expenses?startDate=2026-08-01&endDate=2026-08-31")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(jsonPath("$.data.length()").value(0))
        }

        @Test
        fun deleteRecord_whenNotAuthenticated_shouldReturn401() {
            val record = createTestRecord(LocalDate.of(2026, 8, 19))

            mockMvc.perform(delete("/api/expenses/${record.id}"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun deleteRecord_whenNotFound_shouldReturn404() {
            mockMvc.perform(
                delete("/api/expenses/99999")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun deleteRecord_whenAlreadyDeleted_shouldReturn404() {
            val record = createTestRecord(LocalDate.of(2026, 8, 19))
            expenseRecordRepository.delete(record)

            mockMvc.perform(
                delete("/api/expenses/${record.id}")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /api/expenses/{id}/restore")
    inner class RestoreRecord {

        @Test
        fun restoreRecord_whenAuthenticated_shouldRestoreSoftDeletedRecord() {
            val record = createTestRecord(LocalDate.of(2026, 8, 19))
            expenseRecordRepository.delete(record)

            mockMvc.perform(
                post("/api/expenses/${record.id}/restore")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.deletedAt").doesNotExist())

            mockMvc.perform(
                get("/api/expenses?startDate=2026-08-01&endDate=2026-08-31")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(jsonPath("$.data.length()").value(1))
        }

        @Test
        fun restoreRecord_whenNotFound_shouldReturn404() {
            mockMvc.perform(
                post("/api/expenses/99999/restore")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun restoreRecord_whenNotAuthenticated_shouldReturn401() {
            val record = createTestRecord(LocalDate.of(2026, 8, 19))
            expenseRecordRepository.delete(record)

            mockMvc.perform(post("/api/expenses/${record.id}/restore"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun restoreRecord_whenNotActuallyDeleted_shouldBeNoOpAndReturnRecord() {
            val record = createTestRecord(LocalDate.of(2026, 8, 19))

            mockMvc.perform(
                post("/api/expenses/${record.id}/restore")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.deletedAt").doesNotExist())
                .andExpect(jsonPath("$.data.merchant").value("이마트"))
        }
    }

    private fun validRequest() = ExpenseRecordRequest(
        expenseDate = LocalDate.of(2026, 8, 19),
        paymentMethod = PaymentMethod.WOORI_CARD,
        category = ExpenseCategory.FOOD,
        merchant = "이마트",
        amount = 30000
    )

    private fun createTestRecord(date: LocalDate): ExpenseRecord {
        return expenseRecordRepository.save(
            ExpenseRecord(
                expenseDate = date,
                paymentMethod = PaymentMethod.WOORI_CARD,
                category = ExpenseCategory.FOOD,
                merchant = "이마트",
                amount = 30000
            )
        )
    }
}
