package com.ourspots.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ourspots.domain.auth.controller.LoginRequest
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

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AdminLogControllerIntegrationTest {

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
        jdbcTemplate.update("DELETE FROM error_logs")
    }

    @Test
    fun getLogs_whenNotAuthenticated_shouldReturn401() {
        mockMvc.perform(get("/api/admin/logs?table=ERROR_LOGS"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun getLogs_whenAuthenticated_shouldReturnHeadersAndRows() {
        jdbcTemplate.update(
            "INSERT INTO error_logs (exception_type, message, method, path, stack_trace, created_at) VALUES (?, ?, ?, ?, ?, NOW())",
            "RuntimeException", "실패", "GET", "/api/places", "..."
        )

        mockMvc.perform(
            get("/api/admin/logs?table=ERROR_LOGS&period=ALL")
                .header("Authorization", "Bearer $authToken")
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.data.headers").isArray)
            .andExpect(jsonPath("$.data.rows.length()").value(1))
            .andExpect(jsonPath("$.data.rows[0][1]").value("RuntimeException"))
    }

    @Test
    fun getLogs_whenPeriodParamInvalid_shouldReturn400() {
        mockMvc.perform(
            get("/api/admin/logs?table=ERROR_LOGS&period=NOT_A_PERIOD")
                .header("Authorization", "Bearer $authToken")
        )
            .andExpect(status().isBadRequest)
    }
}
