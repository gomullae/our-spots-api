package com.ourspots.domain.photo.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ourspots.api.dto.PhotoVisibilityUpdateRequest
import com.ourspots.domain.auth.controller.LoginRequest
import com.ourspots.domain.photo.entity.Photo
import com.ourspots.domain.photo.entity.PhotoEntityType
import com.ourspots.domain.photo.repository.PhotoRepository
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PhotoControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var photoRepository: PhotoRepository

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
        jdbcTemplate.update("DELETE FROM photos")
    }

    @Nested
    @DisplayName("PATCH /api/photos/{id}")
    inner class UpdateVisibility {

        @Test
        fun updateVisibility_whenAuthenticated_shouldChangeIsPublic() {
            // given
            val photo = createTestPhoto(isPublic = false)

            // when & then
            mockMvc.perform(
                patch("/api/photos/${photo.id}")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(PhotoVisibilityUpdateRequest(isPublic = true)))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.isPublic").value(true))
        }

        @Test
        fun updateVisibility_whenNotAuthenticated_shouldReturn401() {
            // given
            val photo = createTestPhoto(isPublic = false)

            // when & then
            mockMvc.perform(
                patch("/api/photos/${photo.id}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(PhotoVisibilityUpdateRequest(isPublic = true)))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun updateVisibility_whenPhotoNotExists_shouldReturn404() {
            mockMvc.perform(
                patch("/api/photos/99999")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(PhotoVisibilityUpdateRequest(isPublic = true)))
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun updateVisibility_whenIsPublicMissing_shouldReturn400() {
            val photo = createTestPhoto(isPublic = false)

            mockMvc.perform(
                patch("/api/photos/${photo.id}")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}")
            )
                .andExpect(status().isBadRequest)
        }
    }

    private fun createTestPhoto(isPublic: Boolean): Photo {
        return photoRepository.save(
            Photo(
                entityType = PhotoEntityType.PLACE,
                entityId = 1L,
                objectKey = "place/test.jpg",
                url = "https://pub-test.r2.dev/place/test.jpg",
                thumbnailObjectKey = "place/test_thumb.jpg",
                thumbnailUrl = "https://pub-test.r2.dev/place/test_thumb.jpg",
                isPublic = isPublic
            )
        )
    }
}
