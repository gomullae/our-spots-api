package com.ourspots.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ourspots.api.dto.FeedbackCreateRequest
import com.ourspots.domain.feedback.repository.FeedbackRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class FeedbackControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var feedbackRepository: FeedbackRepository

    @BeforeEach
    fun setUp() {
        feedbackRepository.deleteAll()
    }

    @Nested
    @DisplayName("POST /api/feedbacks")
    inner class Create {

        @Test
        fun create_shouldReturnSuccess() {
            val request = FeedbackCreateRequest(content = "좋은 서비스입니다")

            mockMvc.perform(
                post("/api/feedbacks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.success").value(true))
        }

        @Test
        fun create_whenBlankContent_shouldReturn400() {
            val request = FeedbackCreateRequest(content = "   ")

            mockMvc.perform(
                post("/api/feedbacks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("$.success").value(false))
        }

        @Test
        fun create_whenContentExceeds500_shouldReturn400() {
            val request = FeedbackCreateRequest(content = "가".repeat(501))

            mockMvc.perform(
                post("/api/feedbacks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun create_whenInvalidJson_shouldReturn400() {
            mockMvc.perform(
                post("/api/feedbacks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{invalid json}")
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun create_shouldSaveToDatabase() {
            val request = FeedbackCreateRequest(content = "DB 저장 확인")

            mockMvc.perform(
                post("/api/feedbacks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)

            val saved = feedbackRepository.findAll()
            assert(saved.size == 1)
            assert(saved[0].content == "DB 저장 확인")
            assert(saved[0].source == "our-spots")
        }
    }
}
