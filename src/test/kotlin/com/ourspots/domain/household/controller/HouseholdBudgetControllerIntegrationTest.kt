package com.ourspots.domain.household.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ourspots.api.dto.HouseholdBudgetItemRequest
import com.ourspots.api.dto.HouseholdIncomeRequest
import com.ourspots.domain.auth.controller.LoginRequest
import com.ourspots.domain.household.entity.HouseholdSectionType
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HouseholdBudgetControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

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
        jdbcTemplate.update("DELETE FROM household_history")
        jdbcTemplate.update("DELETE FROM household_budget_items")
        jdbcTemplate.update("DELETE FROM household_incomes")
    }

    @Nested
    @DisplayName("GET /api/household-budget")
    inner class GetOverview {

        @Test
        fun getOverview_whenNotAuthenticated_shouldReturn401() {
            mockMvc.perform(get("/api/household-budget"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun getOverview_whenAuthenticated_shouldReturnIncomesAndItems() {
            mockMvc.perform(
                post("/api/household-budget/incomes")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(HouseholdIncomeRequest(label = "급여", amount = 5_700_000L)))
            ).andExpect(status().isCreated)

            mockMvc.perform(
                get("/api/household-budget")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.incomes.length()").value(1))
                .andExpect(jsonPath("$.data.incomes[0].label").value("급여"))
                .andExpect(jsonPath("$.data.incomes[0].amount").value(5_700_000))
                .andExpect(jsonPath("$.data.items.length()").value(0))
        }
    }

    @Nested
    @DisplayName("GET /api/household-budget/meta")
    inner class GetMeta {

        @Test
        fun getMeta_whenNotAuthenticated_shouldReturn401() {
            mockMvc.perform(get("/api/household-budget/meta"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun getMeta_whenNoRecords_shouldReturnZeroCountAndNullLastModified() {
            mockMvc.perform(
                get("/api/household-budget/meta").header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.lastModified").doesNotExist())
        }

        @Test
        fun getMeta_shouldCombineIncomeAndItemCounts() {
            mockMvc.perform(
                post("/api/household-budget/incomes")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(HouseholdIncomeRequest(label = "급여", amount = 5_700_000L)))
            ).andExpect(status().isCreated)

            mockMvc.perform(
                post("/api/household-budget/items")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            HouseholdBudgetItemRequest(sectionType = HouseholdSectionType.FIXED_COST, label = "통신비", amount = 33_250L)
                        )
                    )
            ).andExpect(status().isCreated)

            mockMvc.perform(
                get("/api/household-budget/meta").header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.count").value(2))
                .andExpect(jsonPath("$.data.lastModified").exists())
        }
    }

    @Nested
    @DisplayName("예산 항목 CRUD + 이력")
    inner class ItemCrud {

        @Test
        fun createItem_whenNotAuthenticated_shouldReturn401() {
            mockMvc.perform(
                post("/api/household-budget/items")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(HouseholdBudgetItemRequest(sectionType = HouseholdSectionType.FIXED_COST, label = "통신비", amount = 33_250L)))
            ).andExpect(status().isUnauthorized)
        }

        @Test
        fun createUpdateDeleteRestore_shouldWorkEndToEndAndRecordHistory() {
            val createResult = mockMvc.perform(
                post("/api/household-budget/items")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            HouseholdBudgetItemRequest(
                                sectionType = HouseholdSectionType.FIXED_COST,
                                label = "통신비",
                                vendor = "SKT",
                                amount = 33_250L
                            )
                        )
                    )
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.amount").value(33250))
                .andReturn()

            val itemId = objectMapper.readTree(createResult.response.contentAsString).get("data").get("id").asLong()

            // 수정
            mockMvc.perform(
                put("/api/household-budget/items/$itemId")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            HouseholdBudgetItemRequest(
                                sectionType = HouseholdSectionType.FIXED_COST,
                                label = "통신비",
                                vendor = "SKT",
                                amount = 35_000L
                            )
                        )
                    )
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.amount").value(35000))

            // 이력 조회 — CREATE, UPDATE 두 건이 시간 역순으로 쌓여있어야 함
            mockMvc.perform(
                get("/api/household-budget/items/$itemId/history")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].action").value("UPDATE"))
                .andExpect(jsonPath("$.data[0].amount").value(35000))
                .andExpect(jsonPath("$.data[1].action").value("CREATE"))
                .andExpect(jsonPath("$.data[1].amount").value(33250))

            // 삭제(소프트) — 대시보드 조회에서 빠져야 함
            mockMvc.perform(
                delete("/api/household-budget/items/$itemId")
                    .header("Authorization", "Bearer $authToken")
            ).andExpect(status().isNoContent)

            mockMvc.perform(
                get("/api/household-budget")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items.length()").value(0))

            // includeDeleted=true로는 여전히 보임
            mockMvc.perform(
                get("/api/household-budget")
                    .param("includeDeleted", "true")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.items.length()").value(1))

            // 복구
            mockMvc.perform(
                post("/api/household-budget/items/$itemId/restore")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.deletedAt").value(org.hamcrest.Matchers.nullValue()))

            mockMvc.perform(
                get("/api/household-budget/items/$itemId/history")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].action").value("RESTORE"))
        }

        @Test
        fun createItem_whenLabelBlank_shouldReturn400() {
            mockMvc.perform(
                post("/api/household-budget/items")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        objectMapper.writeValueAsString(
                            HouseholdBudgetItemRequest(sectionType = HouseholdSectionType.FIXED_COST, label = "", amount = 1L)
                        )
                    )
            ).andExpect(status().isBadRequest)
        }
    }
}
