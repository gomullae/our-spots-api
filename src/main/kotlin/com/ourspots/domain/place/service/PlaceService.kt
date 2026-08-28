package com.ourspots.domain.place.service

import com.ourspots.api.dto.*
import com.ourspots.common.exception.DuplicateException
import com.ourspots.common.exception.NotFoundException
import com.ourspots.common.exception.ServiceUnavailableException
import com.ourspots.common.util.findByIdOrThrow
import com.ourspots.common.util.restoreSoftDeleted
import com.ourspots.domain.photo.entity.PhotoEntityType
import com.ourspots.domain.photo.service.PhotoService
import com.ourspots.domain.place.entity.Place
import com.ourspots.domain.place.entity.PlaceType
import com.ourspots.domain.place.repository.PlaceRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.LocalDateTime

@Service
@Transactional(readOnly = true)
class PlaceService(
    private val placeRepository: PlaceRepository,
    private val googlePlaceSyncService: GooglePlaceSyncService,
    private val photoService: PhotoService
) {

    companion object {
        // 관리자 화면이라 실제 악용 위협은 낮지만, 실수로 size=100000 같은 값을 보내 큰 쿼리를 유발하는 것을 방지
        private const val MAX_RECENT_PLACES_SIZE = 100
    }

    // 개인 카테고리(나의 발자취 등)는 비인증 사용자에게 노출되면 안 됨 — getPlace/getMarkers 공통 규칙
    private fun isHiddenFromUser(type: PlaceType?, authenticated: Boolean): Boolean =
        !authenticated && type in PlaceType.PERSONAL_TYPES

    // LIKE 패턴에서 %, _ 는 와일드카드로 해석되므로 검색어에 그대로 포함되면 의도치 않게 넓게/좁게 매치될 수 있음 → 이스케이프
    private fun escapeLikePattern(value: String): String =
        value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    fun getPlace(id: Long, authenticated: Boolean): PlaceResponse {
        val place = placeRepository.findByIdOrThrow(id, "Place")
        if (isHiddenFromUser(place.type, authenticated)) {
            throw NotFoundException("Place not found: $id")
        }
        return PlaceResponse.from(place, photoService.listByEntity(PhotoEntityType.PLACE, place.id))
    }

    @Transactional
    fun createPlace(request: PlaceCreateRequest): PlaceResponse {
        if (placeRepository.existsByNameAndAddress(request.name, request.address)) {
            throw DuplicateException("동일한 이름으로 이미 등록된 주소입니다: ${request.name}")
        }

        val place = Place(
            name = request.name,
            type = request.type,
            address = request.address,
            latitude = request.latitude,
            longitude = request.longitude,
            description = request.description,
            grade = request.grade
        )
        return PlaceResponse.from(placeRepository.save(place))
    }

    @Transactional
    fun updatePlace(id: Long, request: PlaceUpdateRequest): PlaceResponse {
        val place = placeRepository.findByIdOrThrow(id, "Place")

        request.name?.let { place.name = it }
        request.type?.let { place.type = it }
        request.address?.let { place.address = it }
        request.latitude?.let { place.latitude = it }
        request.longitude?.let { place.longitude = it }
        request.description?.let { place.description = it }
        request.grade?.let { place.grade = it }
        request.googlePlaceId?.let { place.googlePlaceId = it }
        request.googleRating?.let { place.googleRating = it }
        request.googleRatingsTotal?.let { place.googleRatingsTotal = it }

        val saved = placeRepository.save(place)
        return PlaceResponse.from(saved, photoService.listByEntity(PhotoEntityType.PLACE, saved.id))
    }

    @Transactional
    fun deletePlace(id: Long) {
        val place = placeRepository.findByIdOrThrow(id, "Place")
        // Soft Delete: @SQLDelete 어노테이션에 의해 deletedAt이 설정됨
        placeRepository.delete(place)
    }

    fun getRecentPlaces(filter: RecentPlacesFilter, page: Int, size: Int): Page<PlaceResponse> {
        val start = (filter.startDate ?: LocalDate.now().minusMonths(3)).atStartOfDay()
        val end = (filter.endDate ?: LocalDate.now()).plusDays(1).atStartOfDay()
        val keyword = filter.keyword?.trim()?.takeIf { it.isNotEmpty() }?.let { escapeLikePattern(it) }
        val cappedSize = size.coerceIn(1, MAX_RECENT_PLACES_SIZE)
        val placesPage = placeRepository
            .searchRecentPlaces(start, end, keyword, filter.type?.name, filter.grade, filter.includeDeleted, PageRequest.of(page, cappedSize))
        val photosByPlaceId = photoService.listByEntities(PhotoEntityType.PLACE, placesPage.content.map { it.id })
        return placesPage.map { PlaceResponse.from(it, photosByPlaceId[it.id] ?: emptyList()) }
    }

    @Transactional
    fun restorePlace(id: Long): PlaceResponse {
        val saved = restoreSoftDeleted(id, "Place", placeRepository::findByIdIncludingDeleted) { placeRepository.save(it) }
        return PlaceResponse.from(saved, photoService.listByEntity(PhotoEntityType.PLACE, saved.id))
    }

    // 배치와 달리 관리자가 명시적으로 요청한 것이므로 googleRatingFailCount 상한(3회) 상관없이 항상 재시도함
    // NOT_SUPPORTED: Google API 호출(최대 10초)이 DB 트랜잭션 안에 들어있으면 그 시간 내내 커넥션 풀(운영 5개,
    // 앱 전체 공유)의 커넥션 하나를 붙잡고 있게 됨 — findById/save 각각 리포지토리 자체 트랜잭션으로 원자적으로
    // 처리되므로, 조회한 detached 엔티티를 메모리에서 수정 후 save()에 넘겨도 정상 동작함(ScheduleService와 동일 패턴)
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun syncGoogleRating(id: Long): PlaceResponse {
        if (!googlePlaceSyncService.isConfigured()) {
            throw ServiceUnavailableException("Google API 키가 설정되지 않았습니다.")
        }
        val place = placeRepository.findByIdOrThrow(id, "Place")

        val result = googlePlaceSyncService.search(place.name, place.address, place.latitude, place.longitude)
        if (result != null) {
            place.googlePlaceId = result.placeId
            place.googleRating = result.rating
            place.googleRatingsTotal = result.ratingsTotal
            place.googleRatingFailCount = 0
            place.googleRatingUpdatedAt = LocalDateTime.now()
        } else {
            place.googleRatingFailCount++
        }
        val saved = placeRepository.save(place)
        return PlaceResponse.from(saved, photoService.listByEntity(PhotoEntityType.PLACE, saved.id))
    }

    @CacheEvict(value = ["markers"], allEntries = true)
    fun evictMarkersCache() {
    }

    @Cacheable(value = ["markers"], key = "'markers:' + (#type != null ? #type.name() : 'ALL') + ':' + (#swLat ?: '_') + ':' + (#swLng ?: '_') + ':' + (#neLat ?: '_') + ':' + (#neLng ?: '_') + ':' + #authenticated")
    fun getMarkers(
        type: PlaceType?,
        swLat: Double?,
        swLng: Double?,
        neLat: Double?,
        neLng: Double?,
        authenticated: Boolean
    ): List<MarkerResponse> {
        if (isHiddenFromUser(type, authenticated)) {
            return emptyList()
        }

        val places = when {
            swLat != null && swLng != null && neLat != null && neLng != null -> {
                when {
                    type != null -> placeRepository.findByTypeWithinBounds(type, swLat, swLng, neLat, neLng)
                    authenticated -> placeRepository.findWithinBounds(swLat, swLng, neLat, neLng)
                    else -> placeRepository.findWithinBoundsExcludingTypes(PlaceType.PERSONAL_TYPES, swLat, swLng, neLat, neLng)
                }
            }
            type != null -> placeRepository.findByType(type)
            authenticated -> placeRepository.findAll()
            else -> placeRepository.findByTypeNotIn(PlaceType.PERSONAL_TYPES)
        }
        val placeIdsWithPhotos = photoService.findEntityIdsWithPhotos(PhotoEntityType.PLACE, places.map { it.id })
        return places.map { MarkerResponse.from(it, hasPhotos = it.id in placeIdsWithPhotos) }
    }
}
