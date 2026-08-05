package com.ourspots.batch

import com.ourspots.domain.place.repository.PlaceRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.client.SimpleClientHttpRequestFactory
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import java.time.Duration

/**
 * AdministrativeReorganizations에 등록된 개편 대상 장소의 주소를 갱신하는 배치.
 * 텍스트 치환이 아니라 저장된 좌표를 카카오 좌표->주소 API로 재조회해 얻은
 * 최신 시/도·구시군 명칭으로 주소 접두어만 교체한다 (도로명/건물명/동 표기 등은 보존).
 */
@Component
@Profile("batch")
@ConditionalOnProperty(name = ["batch.job"], havingValue = "update-address")
class UpdateReorganizedAddressRunner(
    private val placeRepository: PlaceRepository,
    @Value("\${batch.kakao-rest-api-key}") private val kakaoApiKey: String
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)
    private val restTemplate = RestTemplate().apply {
        requestFactory = SimpleClientHttpRequestFactory().apply {
            setConnectTimeout(Duration.ofSeconds(5))
            setReadTimeout(Duration.ofSeconds(10))
        }
    }

    companion object {
        private const val KAKAO_COORD2ADDRESS_URL =
            "https://dapi.kakao.com/v2/local/geo/coord2address.json?x={x}&y={y}"
        private const val API_DELAY_MS = 200L
    }

    override fun run(args: ApplicationArguments) {
        if (kakaoApiKey.isBlank()) {
            log.error("KAKAO_REST_API_KEY 환경변수가 설정되지 않았습니다.")
            return
        }

        val dryRun = (args.getOptionValues("dry-run")?.firstOrNull() ?: "true").toBooleanStrictOrNull() ?: true
        val limit = args.getOptionValues("limit")?.firstOrNull()?.toIntOrNull()

        log.info("행정구역 개편 주소 갱신 시작")
        log.info("모드: ${if (dryRun) "DRY-RUN (DB 미반영)" else "실제 반영"}")

        val candidates = placeRepository.findAll()
            .filter { AdministrativeReorganizations.isTarget(it.address) }
            .sortedBy { it.id }
        val targets = if (limit != null) candidates.take(limit) else candidates
        log.info("대상: 전체 ${candidates.size}건 중 ${targets.size}건 처리 예정" + (limit?.let { " (--limit=$it)" } ?: ""))

        if (targets.isEmpty()) {
            log.info("처리할 장소가 없습니다.")
            return
        }

        var changed = 0
        val unchanged = mutableListOf<PlaceRef>()
        val notFound = mutableListOf<PlaceRef>()
        val skippedConflict = mutableListOf<PlaceRef>()
        val failed = mutableListOf<PlaceRef>()

        for ((index, place) in targets.withIndex()) {
            log.info("[${index + 1}/${targets.size}] id=${place.id} ${place.name}")
            log.info("  기존: ${place.address}")
            val ref = PlaceRef(place.id, place.name, place.address)

            try {
                val region = reverseGeocode(place.latitude, place.longitude)
                if (region == null) {
                    log.warn("  좌표->주소 조회 실패, 스킵")
                    notFound += ref
                    Thread.sleep(API_DELAY_MS)
                    continue
                }

                val newAddress = AdministrativeReorganizations.buildUpdatedAddress(place.address, region.region1, region.region2)
                if (newAddress == null) {
                    log.info("  변경 없음 (이미 최신이거나 접두어 패턴 불일치)")
                    unchanged += ref
                    Thread.sleep(API_DELAY_MS)
                    continue
                }

                log.info("  신규: $newAddress")

                if (placeRepository.existsByNameAndAddress(place.name, newAddress)) {
                    log.warn("  [SKIP] 동일 이름+주소가 이미 존재 — 수동 확인 필요")
                    skippedConflict += ref
                    Thread.sleep(API_DELAY_MS)
                    continue
                }

                if (!dryRun) {
                    place.address = newAddress
                    placeRepository.save(place)
                }
                changed++
            } catch (e: Exception) {
                log.error("  에러: ${e.message}")
                failed += ref
            }

            Thread.sleep(API_DELAY_MS)
        }

        log.info("========== 결과 ==========")
        log.info("변경${if (dryRun) " (dry-run, 미반영)" else ""}: ${changed}개")
        log.info("변경없음: ${unchanged.size}개")
        log.info("조회실패: ${notFound.size}개")
        log.info("중복충돌스킵: ${skippedConflict.size}개")
        log.info("에러: ${failed.size}개")
        log.info("==========================")
        logDetail("변경없음", unchanged)
        logDetail("조회실패", notFound)
        logDetail("중복충돌스킵", skippedConflict)
        logDetail("에러", failed)
        if (dryRun) {
            log.info("실제 반영하려면 --dry-run=false 옵션을 추가하세요.")
        }
    }

    private fun logDetail(label: String, refs: List<PlaceRef>) {
        if (refs.isEmpty()) return
        log.info("[$label 상세]")
        refs.forEach { log.info("  id=${it.id} ${it.name} (${it.address})") }
    }

    private data class PlaceRef(val id: Long, val name: String, val address: String)

    @Suppress("UNCHECKED_CAST")
    private fun reverseGeocode(lat: Double, lng: Double): RegionResult? {
        return try {
            val headers = HttpHeaders().apply { set("Authorization", "KakaoAK $kakaoApiKey") }
            val response = restTemplate.exchange(
                KAKAO_COORD2ADDRESS_URL, HttpMethod.GET, HttpEntity<Void>(headers),
                Map::class.java, lng, lat
            )
            val documents = response.body?.get("documents") as? List<Map<String, Any>>
            val doc = documents?.firstOrNull() ?: return null
            val roadAddress = doc["road_address"] as? Map<String, Any>
            val jibunAddress = doc["address"] as? Map<String, Any>
            val chosen = roadAddress ?: jibunAddress ?: return null
            val region1 = chosen["region_1depth_name"] as? String ?: return null
            val region2 = chosen["region_2depth_name"] as? String ?: return null
            RegionResult(region1, region2)
        } catch (e: Exception) {
            log.warn("좌표->주소 조회 실패 (lat=$lat, lng=$lng): ${e.message}")
            null
        }
    }

    private data class RegionResult(val region1: String, val region2: String)
}
