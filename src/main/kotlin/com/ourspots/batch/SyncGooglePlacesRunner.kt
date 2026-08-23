package com.ourspots.batch

import com.ourspots.domain.place.repository.PlaceRepository
import com.ourspots.domain.place.service.GooglePlaceSyncService
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
@Profile("batch")
@ConditionalOnProperty(name = ["batch.job"], havingValue = "sync-google")
class SyncGooglePlacesRunner(
    private val placeRepository: PlaceRepository,
    private val googlePlaceSyncService: GooglePlaceSyncService
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private const val MAX_FAIL_COUNT = 3
        private const val REFRESH_MONTHS = 6L
        private const val API_DELAY_MS = 200L
    }

    override fun run(args: ApplicationArguments) {
        if (!googlePlaceSyncService.isConfigured()) {
            log.error("GOOGLE_API_KEY 환경변수가 설정되지 않았습니다.")
            return
        }

        val limitStr = args.getOptionValues("limit")?.firstOrNull() ?: "100"
        val limit = (limitStr.toIntOrNull() ?: 100).coerceIn(1, 500)

        log.info("Google Places 동기화 시작")
        log.info("처리 제한: ${limit}개")

        val cutoffDate = LocalDateTime.now().minusMonths(REFRESH_MONTHS)
        val places = placeRepository.findPlacesEligibleForGoogleSync(MAX_FAIL_COUNT, cutoffDate, PageRequest.of(0, limit))

        val newCount = places.count { it.googleRating == null }
        val renewalCount = places.size - newCount
        log.info("${places.size}개 처리 예정 (신규: ${newCount}, 갱신: ${renewalCount})")

        if (places.isEmpty()) {
            log.info("동기화 대상 장소가 없습니다.")
            return
        }

        var successNew = 0
        var successRenewal = 0
        var notFound = 0
        var failed = 0

        for (place in places) {
            val isRenewal = place.googleRating != null
            log.info("[${place.id}] ${place.name} 처리 중...${if (isRenewal) " (갱신)" else ""}")

            try {
                val googleData = googlePlaceSyncService.search(place.name, place.address, place.latitude, place.longitude)

                if (googleData != null) {
                    place.googlePlaceId = googleData.placeId
                    place.googleRating = googleData.rating
                    place.googleRatingsTotal = googleData.ratingsTotal
                    place.googleRatingFailCount = 0
                    place.googleRatingUpdatedAt = LocalDateTime.now()
                    placeRepository.save(place)
                    log.info("  성공: ${googleData.rating ?: "N/A"}점 (${googleData.ratingsTotal ?: 0}개 리뷰)")
                    if (isRenewal) successRenewal++ else successNew++
                } else {
                    place.googleRatingFailCount++
                    placeRepository.save(place)
                    log.warn("  Google에서 찾을 수 없음: ${place.name} (실패 ${place.googleRatingFailCount}/${MAX_FAIL_COUNT}회)")
                    notFound++
                }

                Thread.sleep(API_DELAY_MS)
            } catch (e: Exception) {
                place.googleRatingFailCount++
                placeRepository.save(place)
                log.error("  에러: ${e.message} (실패 ${place.googleRatingFailCount}/${MAX_FAIL_COUNT}회)")
                failed++
            }
        }

        log.info("========== 결과 ==========")
        log.info("성공: ${successNew + successRenewal}개 (신규: ${successNew}, 갱신: ${successRenewal})")
        log.info("미발견: ${notFound}개")
        log.info("실패: ${failed}개")
        log.info("==========================")
    }
}
