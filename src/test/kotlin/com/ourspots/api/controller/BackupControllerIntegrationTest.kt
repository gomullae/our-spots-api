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
class BackupControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var placeRepository: PlaceRepository

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
        jdbcTemplate.update("DELETE FROM places")
    }

    @Test
    fun download_whenNotAuthenticated_shouldReturn401() {
        mockMvc.perform(get("/api/admin/backup?table=PLACES"))
            .andExpect(status().isUnauthorized)
    }

    @Test
    fun download_whenAuthenticated_shouldReturnXlsxFileWithContentDisposition() {
        placeRepository.save(
            Place(name = "장소", type = PlaceType.RESTAURANT, address = "서울시 종로구", latitude = 37.5, longitude = 127.0)
        )

        val result = mockMvc.perform(
            get("/api/admin/backup?table=PLACES&period=ALL")
                .header("Authorization", "Bearer $authToken")
        )
            .andExpect(status().isOk)
            .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .andReturn()

        val disposition = result.response.getHeader("Content-Disposition")
        Assertions.assertTrue(disposition!!.contains("places_"))
        Assertions.assertTrue(disposition.contains("_all.xlsx"))
        Assertions.assertTrue(result.response.contentAsByteArray.isNotEmpty())
    }

    @Test
    fun download_whenTableParamInvalid_shouldReturn400() {
        mockMvc.perform(
            get("/api/admin/backup?table=NOT_A_TABLE")
                .header("Authorization", "Bearer $authToken")
        )
            .andExpect(status().isBadRequest)
    }
}
