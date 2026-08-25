package com.ourspots.domain.place.service

import com.ourspots.common.util.RestTemplateFactory
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate

// 배치(SyncGooglePlacesRunner)와 관리자 페이지 수동 동기화가 공유하는 Google Places API 호출 로직
@Service
class GooglePlaceSyncService(
    @Value("\${app.google-api-key}") private val googleApiKey: String,
    // 기본값을 생성자 파라미터로 열어둬서 테스트에서 mock RestTemplate을 주입할 수 있게 함 (컨텍스트에 RestTemplate 빈이 없으면 Spring이 이 기본값을 그대로 사용)
    private val restTemplate: RestTemplate = RestTemplateFactory.create(readTimeoutSeconds = 10)
) {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val PLACES_SEARCH_URL = "https://places.googleapis.com/v1/places:searchText"
    }

    fun isConfigured(): Boolean = googleApiKey.isNotBlank()

    @Suppress("UNCHECKED_CAST")
    fun search(name: String, address: String, lat: Double, lng: Double): GooglePlaceData? {
        return try {
            val headers = HttpHeaders().apply {
                contentType = MediaType.APPLICATION_JSON
                set("X-Goog-Api-Key", googleApiKey)
                set("X-Goog-FieldMask", "places.id,places.rating,places.userRatingCount")
            }
            val body = mapOf(
                "textQuery" to "$name $address",
                "maxResultCount" to 1,
                "locationBias" to mapOf(
                    "circle" to mapOf(
                        "center" to mapOf("latitude" to lat, "longitude" to lng),
                        "radius" to 500.0
                    )
                )
            )

            val response = restTemplate.postForObject(
                PLACES_SEARCH_URL,
                HttpEntity(body, headers),
                Map::class.java
            )

            val places = response?.get("places") as? List<Map<String, Any>>
            val place = places?.firstOrNull() ?: return null

            GooglePlaceData(
                placeId = place["id"] as? String,
                rating = (place["rating"] as? Number)?.toDouble(),
                ratingsTotal = (place["userRatingCount"] as? Number)?.toInt()
            )
        } catch (e: Exception) {
            log.warn("Google Places 검색 실패: ${e.message}")
            null
        }
    }

    data class GooglePlaceData(
        val placeId: String?,
        val rating: Double?,
        val ratingsTotal: Int?
    )
}
