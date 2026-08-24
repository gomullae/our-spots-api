package com.ourspots.domain.place.service

import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.http.HttpEntity
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GooglePlaceSyncServiceTest {

    private val restTemplate: RestTemplate = mockk()

    @Nested
    @DisplayName("isConfigured")
    inner class IsConfigured {

        @Test
        fun isConfigured_whenApiKeyBlank_shouldReturnFalse() {
            val service = GooglePlaceSyncService("", restTemplate)

            assertFalse(service.isConfigured())
        }

        @Test
        fun isConfigured_whenApiKeySet_shouldReturnTrue() {
            val service = GooglePlaceSyncService("test-key", restTemplate)

            assertTrue(service.isConfigured())
        }
    }

    @Nested
    @DisplayName("search")
    inner class Search {

        private val service = GooglePlaceSyncService("test-key", restTemplate)

        @Test
        fun search_whenPlaceFound_shouldReturnParsedData() {
            val response = mapOf(
                "places" to listOf(
                    mapOf("id" to "place-123", "rating" to 4.5, "userRatingCount" to 87)
                )
            )
            every {
                restTemplate.postForObject(any<String>(), any<HttpEntity<*>>(), Map::class.java)
            } returns response

            val result = service.search("맛집", "서울시 종로구", 37.5, 127.0)

            assertEquals("place-123", result?.placeId)
            assertEquals(4.5, result?.rating)
            assertEquals(87, result?.ratingsTotal)
        }

        @Test
        fun search_whenNoPlacesInResponse_shouldReturnNull() {
            every {
                restTemplate.postForObject(any<String>(), any<HttpEntity<*>>(), Map::class.java)
            } returns mapOf("places" to emptyList<Map<String, Any>>())

            val result = service.search("맛집", "서울시 종로구", 37.5, 127.0)

            assertNull(result)
        }

        @Test
        fun search_whenResponseNull_shouldReturnNull() {
            every {
                restTemplate.postForObject(any<String>(), any<HttpEntity<*>>(), Map::class.java)
            } returns null

            val result = service.search("맛집", "서울시 종로구", 37.5, 127.0)

            assertNull(result)
        }

        @Test
        fun search_whenRestTemplateThrows_shouldReturnNullWithoutPropagating() {
            every {
                restTemplate.postForObject(any<String>(), any<HttpEntity<*>>(), Map::class.java)
            } throws RuntimeException("network error")

            val result = service.search("맛집", "서울시 종로구", 37.5, 127.0)

            assertNull(result)
        }
    }
}
