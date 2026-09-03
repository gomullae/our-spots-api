package com.ourspots.domain.schedule.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.ourspots.api.dto.ScheduleEventRequest
import com.ourspots.domain.auth.controller.LoginRequest
import com.ourspots.domain.schedule.entity.ScheduleCategory
import com.ourspots.domain.schedule.entity.ScheduleEvent
import com.ourspots.domain.schedule.repository.ScheduleEventRepository
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
import java.time.LocalDateTime

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ScheduleControllerIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var scheduleEventRepository: ScheduleEventRepository

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
        // schedule_memos가 schedule_events를 FK로 참조하진 않지만, 이전 테스트의 메모가 남아있으면
        // 개수 상한 테스트 등이 오염되므로 먼저 정리
        jdbcTemplate.update("DELETE FROM schedule_memos")
        jdbcTemplate.update("DELETE FROM schedule_events")
    }

    @Nested
    @DisplayName("GET /api/schedules")
    inner class GetEvents {

        @Test
        fun getEvents_whenNotAuthenticated_shouldReturn401() {
            mockMvc.perform(get("/api/schedules?start=2026-08-01T00:00:00&end=2026-08-31T23:59:59"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun getEvents_whenAuthenticated_shouldReturnOverlappingEvents() {
            createTestEvent(LocalDateTime.of(2026, 7, 1, 10, 0))
            createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(
                get("/api/schedules?start=2026-08-01T00:00:00&end=2026-08-31T23:59:59")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].title").value("일정"))
        }
    }

    @Nested
    @DisplayName("GET /api/schedules/meta")
    inner class GetMeta {

        @Test
        fun getMeta_whenNotAuthenticated_shouldReturn401() {
            mockMvc.perform(get("/api/schedules/meta"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun getMeta_whenNoEvents_shouldReturnZeroCountAndNullLastModified() {
            mockMvc.perform(
                get("/api/schedules/meta")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.lastModified").doesNotExist())
        }

        @Test
        fun getMeta_whenAuthenticated_shouldReturnCountAndLastModified() {
            createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(
                get("/api/schedules/meta")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.count").value(1))
                .andExpect(jsonPath("$.data.lastModified").exists())
        }

        @Test
        fun getMeta_afterDelete_shouldReflectDecreasedCount() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))
            scheduleEventRepository.delete(event)

            mockMvc.perform(
                get("/api/schedules/meta")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.count").value(0))
        }
    }

    @Nested
    @DisplayName("POST /api/schedules")
    inner class CreateEvent {

        @Test
        fun createEvent_whenNotAuthenticated_shouldReturn401() {
            mockMvc.perform(
                post("/api/schedules")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest()))
            )
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun createEvent_whenAuthenticated_shouldCreateAndReturnEvent() {
            mockMvc.perform(
                post("/api/schedules")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest()))
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.title").value("커피약속"))
                .andExpect(jsonPath("$.data.category").value("JINWOO"))
        }

        @Test
        fun createEvent_whenTitleBlank_shouldReturn400() {
            val request = validRequest().copy(title = "")

            mockMvc.perform(
                post("/api/schedules")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun createEvent_whenEndAtBeforeStartAt_shouldReturn400() {
            val request = validRequest().copy(
                startAt = LocalDateTime.of(2026, 8, 10, 12, 0),
                endAt = LocalDateTime.of(2026, 8, 10, 11, 0)
            )

            mockMvc.perform(
                post("/api/schedules")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isBadRequest)
        }

        // Boolean(원시 타입)이면 JSON에 키가 없을 때 Jackson이 조용히 false로 채워서 @field:NotNull이
        // 무력화되는 함정이 있었음(PhotoVisibilityUpdateRequest.isPublic과 동일 패턴) — 회귀 방지
        @Test
        fun createEvent_whenAllDayMissing_shouldReturn400() {
            mockMvc.perform(
                post("/api/schedules")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"title":"커피약속","category":"JINWOO","startAt":"2026-08-10T10:00:00","endAt":"2026-08-10T11:30:00"}""")
            )
                .andExpect(status().isBadRequest)
        }
    }

    @Nested
    @DisplayName("PUT /api/schedules/{id}")
    inner class UpdateEvent {

        @Test
        fun updateEvent_whenAuthenticated_shouldUpdateEvent() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))
            val request = validRequest().copy(title = "변경된 제목")

            mockMvc.perform(
                put("/api/schedules/${event.id}")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(request))
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.title").value("변경된 제목"))
        }

        @Test
        fun updateEvent_whenNotFound_shouldReturn404() {
            mockMvc.perform(
                put("/api/schedules/99999")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest()))
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun updateEvent_whenNotAuthenticated_shouldReturn401() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(
                put("/api/schedules/${event.id}")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(validRequest()))
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("DELETE /api/schedules/{id}")
    inner class DeleteEvent {

        @Test
        fun deleteEvent_whenAuthenticated_shouldSoftDeleteEvent() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(
                delete("/api/schedules/${event.id}")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNoContent)

            mockMvc.perform(
                get("/api/schedules?start=2026-08-01T00:00:00&end=2026-08-31T23:59:59")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(jsonPath("$.data.length()").value(0))
        }

        @Test
        fun deleteEvent_whenNotAuthenticated_shouldReturn401() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(delete("/api/schedules/${event.id}"))
                .andExpect(status().isUnauthorized)
        }

        @Test
        fun deleteEvent_whenNotFound_shouldReturn404() {
            mockMvc.perform(
                delete("/api/schedules/99999")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /api/schedules/{id}/restore")
    inner class RestoreEvent {

        @Test
        fun restoreEvent_whenAuthenticated_shouldRestoreSoftDeletedEvent() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))
            scheduleEventRepository.delete(event)

            mockMvc.perform(
                post("/api/schedules/${event.id}/restore")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.deletedAt").doesNotExist())
        }

        @Test
        fun restoreEvent_whenNotFound_shouldReturn404() {
            mockMvc.perform(
                post("/api/schedules/99999/restore")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    @Nested
    @DisplayName("POST /api/schedules/{id}/memos")
    inner class AddMemo {

        @Test
        fun addMemo_whenAuthenticated_shouldCreateMemoAndAppearInEvent() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(
                post("/api/schedules/${event.id}/memos")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"주차는 지하 2층"}""")
            )
                .andExpect(status().isCreated)
                .andExpect(jsonPath("$.data.content").value("주차는 지하 2층"))

            mockMvc.perform(
                get("/api/schedules?start=2026-08-01T00:00:00&end=2026-08-31T23:59:59")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(jsonPath("$.data[0].memos.length()").value(1))
        }

        @Test
        fun addMemo_whenContentBlank_shouldReturn400() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(
                post("/api/schedules/${event.id}/memos")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":""}""")
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun addMemo_whenAtLimit_shouldReturn400() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))
            repeat(10) {
                mockMvc.perform(
                    post("/api/schedules/${event.id}/memos")
                        .header("Authorization", "Bearer $authToken")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""{"content":"메모 $it"}""")
                )
                    .andExpect(status().isCreated)
            }

            mockMvc.perform(
                post("/api/schedules/${event.id}/memos")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"11번째 메모"}""")
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun addMemo_whenNotAuthenticated_shouldReturn401() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(
                post("/api/schedules/${event.id}/memos")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"메모"}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("POST /api/schedules/{id}/notify-photos-added")
    inner class NotifyPhotosAdded {

        @Test
        fun notifyPhotosAdded_whenAuthenticated_shouldReturn204() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(
                post("/api/schedules/${event.id}/notify-photos-added")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"count":2}""")
            )
                .andExpect(status().isNoContent)
        }

        @Test
        fun notifyPhotosAdded_whenCountBelowOne_shouldReturn400() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(
                post("/api/schedules/${event.id}/notify-photos-added")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"count":0}""")
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun notifyPhotosAdded_whenEventNotFound_shouldReturn404() {
            mockMvc.perform(
                post("/api/schedules/99999/notify-photos-added")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"count":1}""")
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun notifyPhotosAdded_whenNotAuthenticated_shouldReturn401() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(
                post("/api/schedules/${event.id}/notify-photos-added")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"count":1}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("PUT /api/schedules/{id}/memos/{memoId}")
    inner class UpdateMemo {

        @Test
        fun updateMemo_whenExists_shouldUpdateContentAndReflectInEvent() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))
            val createResult = mockMvc.perform(
                post("/api/schedules/${event.id}/memos")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"원본 메모"}""")
            ).andReturn()
            val memoId = objectMapper.readTree(createResult.response.contentAsString).get("data").get("id").asLong()

            mockMvc.perform(
                put("/api/schedules/${event.id}/memos/$memoId")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"수정된 메모"}""")
            )
                .andExpect(status().isOk)
                .andExpect(jsonPath("$.data.content").value("수정된 메모"))

            mockMvc.perform(
                get("/api/schedules?start=2026-08-01T00:00:00&end=2026-08-31T23:59:59")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(jsonPath("$.data[0].memos[0].content").value("수정된 메모"))
        }

        @Test
        fun updateMemo_whenContentBlank_shouldReturn400() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))
            val createResult = mockMvc.perform(
                post("/api/schedules/${event.id}/memos")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"원본 메모"}""")
            ).andReturn()
            val memoId = objectMapper.readTree(createResult.response.contentAsString).get("data").get("id").asLong()

            mockMvc.perform(
                put("/api/schedules/${event.id}/memos/$memoId")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":""}""")
            )
                .andExpect(status().isBadRequest)
        }

        @Test
        fun updateMemo_whenNotFound_shouldReturn404() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(
                put("/api/schedules/${event.id}/memos/99999")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"수정"}""")
            )
                .andExpect(status().isNotFound)
        }

        @Test
        fun updateMemo_whenNotAuthenticated_shouldReturn401() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(
                put("/api/schedules/${event.id}/memos/1")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"수정"}""")
            )
                .andExpect(status().isUnauthorized)
        }
    }

    @Nested
    @DisplayName("DELETE /api/schedules/{id}/memos/{memoId}")
    inner class DeleteMemo {

        @Test
        fun deleteMemo_whenExists_shouldSoftDeleteAndDisappearFromEvent() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))
            val createResult = mockMvc.perform(
                post("/api/schedules/${event.id}/memos")
                    .header("Authorization", "Bearer $authToken")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"content":"삭제될 메모"}""")
            ).andReturn()
            val memoId = objectMapper.readTree(createResult.response.contentAsString).get("data").get("id").asLong()

            mockMvc.perform(
                delete("/api/schedules/${event.id}/memos/$memoId")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNoContent)

            mockMvc.perform(
                get("/api/schedules?start=2026-08-01T00:00:00&end=2026-08-31T23:59:59")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(jsonPath("$.data[0].memos.length()").value(0))
        }

        @Test
        fun deleteMemo_whenNotFound_shouldReturn404() {
            val event = createTestEvent(LocalDateTime.of(2026, 8, 19, 10, 0))

            mockMvc.perform(
                delete("/api/schedules/${event.id}/memos/99999")
                    .header("Authorization", "Bearer $authToken")
            )
                .andExpect(status().isNotFound)
        }
    }

    private fun validRequest() = ScheduleEventRequest(
        title = "커피약속",
        category = ScheduleCategory.JINWOO,
        startAt = LocalDateTime.of(2026, 8, 10, 10, 0),
        endAt = LocalDateTime.of(2026, 8, 10, 11, 30),
        allDay = false
    )

    private fun createTestEvent(startAt: LocalDateTime): ScheduleEvent {
        return scheduleEventRepository.save(
            ScheduleEvent(
                title = "일정",
                category = ScheduleCategory.SHARED,
                startAt = startAt,
                endAt = startAt,
                allDay = false
            )
        )
    }
}
