package com.ourspots.batch

import com.ourspots.domain.place.repository.PlaceRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Component
@Profile("batch")
@ConditionalOnProperty(name = ["batch.job"], havingValue = "export-places")
class ExportPlacesRunner(
    private val placeRepository: PlaceRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    companion object {
        private val DEFAULT_DIR = System.getProperty("user.home") + "/dev/IdeaProjects/config/OurSpots/DB Backup"
        private val CSV_HEADER = listOf(
            "id", "name", "type", "address", "latitude", "longitude",
            "description", "grade", "googlePlaceId", "googleRating",
            "googleRatingsTotal", "createdAt", "updatedAt", "deletedAt"
        ).joinToString(",")
    }

    override fun run(args: ApplicationArguments) {
        val today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
        val outputPath = args.getOptionValues("output")?.firstOrNull()
            ?: "$DEFAULT_DIR/places-$today.csv"

        val outputFile = File(outputPath)
        outputFile.parentFile?.mkdirs()

        log.info("Places 백업 시작")

        val places = placeRepository.findAllIncludingDeleted()
        log.info("총 ${places.size}개 장소 (삭제 포함)")

        outputFile.bufferedWriter().use { writer ->
            writer.appendLine(CSV_HEADER)

            places.forEach { place ->
                writer.appendLine(
                    listOf(
                        place.id.toString(),
                        escapeCsv(place.name),
                        place.type.name,
                        escapeCsv(place.address),
                        place.latitude.toString(),
                        place.longitude.toString(),
                        escapeCsv(place.description ?: ""),
                        place.grade?.toString() ?: "",
                        place.googlePlaceId ?: "",
                        place.googleRating?.toString() ?: "",
                        place.googleRatingsTotal?.toString() ?: "",
                        place.createdAt.toString(),
                        place.updatedAt.toString(),
                        place.deletedAt?.toString() ?: ""
                    ).joinToString(",")
                )
            }
        }

        log.info("백업 완료: $outputPath (${places.size}건)")
    }

    private fun escapeCsv(value: String): String {
        val escaped = if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"${value.replace("\"", "\"\"")}\""
        } else {
            value
        }
        return if (escaped.isNotEmpty() && escaped[0] in listOf('=', '+', '@', '-')) {
            "'$escaped"
        } else {
            escaped
        }
    }
}
