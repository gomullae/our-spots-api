package com.ourspots.domain.weight.service

import com.ourspots.api.dto.WeightRecordResponse
import com.ourspots.api.dto.WeightRecordUpsertRequest
import com.ourspots.common.exception.NotFoundException
import com.ourspots.domain.weight.entity.WeightRecord
import com.ourspots.domain.weight.repository.WeightRecordRepository
import org.springframework.cache.annotation.CacheEvict
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
@Transactional(readOnly = true)
class WeightService(
    private val weightRecordRepository: WeightRecordRepository
) {

    companion object {
        private fun roundToOneDecimal(weightKg: Double): Double = Math.round(weightKg * 10) / 10.0
    }

    @Cacheable("weightRecords")
    fun getAllRecords(): List<WeightRecordResponse> =
        weightRecordRepository.findAllByOrderByRecordedDateDesc().map { WeightRecordResponse.from(it) }

    @Transactional
    @CacheEvict("weightRecords", allEntries = true)
    fun upsertRecord(request: WeightRecordUpsertRequest): WeightRecordResponse {
        val date = request.recordedDate ?: LocalDate.now()
        val existing = weightRecordRepository.findByRecordedDate(date)
        val weightKg = roundToOneDecimal(request.weightKg)

        val record = if (existing != null) {
            existing.weightKg = weightKg
            existing.memo = request.memo
            existing
        } else {
            WeightRecord(recordedDate = date, weightKg = weightKg, memo = request.memo)
        }

        return WeightRecordResponse.from(weightRecordRepository.save(record))
    }

    @Transactional
    @CacheEvict("weightRecords", allEntries = true)
    fun deleteRecord(id: Long) {
        val record = weightRecordRepository.findById(id)
            .orElseThrow { NotFoundException("Weight record not found: $id") }
        weightRecordRepository.delete(record)
    }
}
