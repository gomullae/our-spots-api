package com.ourspots.domain.backup.service

import com.ourspots.domain.auth.repository.LoginAttemptRepository
import com.ourspots.domain.backup.BackupPeriod
import com.ourspots.domain.backup.BackupTable
import com.ourspots.domain.expense.repository.ExpenseRecordRepository
import com.ourspots.domain.feedback.repository.FeedbackRepository
import com.ourspots.domain.place.repository.PlaceRepository
import com.ourspots.domain.weight.repository.WeightRecordRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

data class BackupFile(val filename: String, val bytes: ByteArray)

@Service
class BackupExportService(
    private val placeRepository: PlaceRepository,
    private val expenseRecordRepository: ExpenseRecordRepository,
    private val weightRecordRepository: WeightRecordRepository,
    private val loginAttemptRepository: LoginAttemptRepository,
    private val feedbackRepository: FeedbackRepository
) {
    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    fun export(table: BackupTable, period: BackupPeriod): BackupFile {
        val cutoff = LocalDate.now().minusMonths(3)

        val workbook = XSSFWorkbook()
        when (table) {
            BackupTable.PLACES -> writeSheet(
                workbook, table.tableName,
                listOf("id", "name", "type", "address", "latitude", "longitude", "description", "grade", "googlePlaceId", "googleRating", "googleRatingsTotal", "createdAt", "updatedAt", "deletedAt"),
                placeRepository.findAllIncludingDeleted()
                    .filter { period == BackupPeriod.ALL || !it.createdAt.toLocalDate().isBefore(cutoff) }
                    .map {
                        listOf(
                            it.id, it.name, it.type.name, it.address, it.latitude, it.longitude,
                            it.description, it.grade, it.googlePlaceId, it.googleRating, it.googleRatingsTotal,
                            it.createdAt, it.updatedAt, it.deletedAt
                        )
                    }
            )

            BackupTable.EXPENSE_RECORDS -> writeSheet(
                workbook, table.tableName,
                listOf("id", "expenseDate", "paymentMethod", "category", "merchant", "amount", "createdAt", "updatedAt", "deletedAt"),
                expenseRecordRepository.findAllIncludingDeleted()
                    .filter { period == BackupPeriod.ALL || !it.createdAt.toLocalDate().isBefore(cutoff) }
                    .map {
                        listOf(
                            it.id, it.expenseDate, it.paymentMethod.name, it.category.name, it.merchant, it.amount,
                            it.createdAt, it.updatedAt, it.deletedAt
                        )
                    }
            )

            BackupTable.WEIGHT_RECORDS -> writeSheet(
                workbook, table.tableName,
                listOf("id", "recordedDate", "weightKg", "memo", "createdAt", "updatedAt", "deletedAt"),
                weightRecordRepository.findAllIncludingDeleted()
                    .filter { period == BackupPeriod.ALL || !it.createdAt.toLocalDate().isBefore(cutoff) }
                    .map {
                        listOf(it.id, it.recordedDate, it.weightKg, it.memo, it.createdAt, it.updatedAt, it.deletedAt)
                    }
            )

            BackupTable.LOGIN_ATTEMPTS -> writeSheet(
                workbook, table.tableName,
                listOf("id", "ipAddress", "userAgent", "endpoint", "attemptCount", "blocked", "createdAt"),
                loginAttemptRepository.findAll()
                    .filter { period == BackupPeriod.ALL || !it.createdAt.toLocalDate().isBefore(cutoff) }
                    .map {
                        listOf(it.id, it.ipAddress, it.userAgent, it.endpoint, it.attemptCount, it.blocked, it.createdAt)
                    }
            )

            BackupTable.FEEDBACKS -> writeSheet(
                workbook, table.tableName,
                listOf("id", "content", "ipAddress", "source", "createdAt"),
                feedbackRepository.findAll()
                    .filter { period == BackupPeriod.ALL || !it.createdAt.toLocalDate().isBefore(cutoff) }
                    .map {
                        listOf(it.id, it.content, it.ipAddress, it.source, it.createdAt)
                    }
            )
        }

        val output = ByteArrayOutputStream()
        workbook.use { it.write(output) }

        val today = LocalDate.now().format(DATE_FORMAT)
        val periodLabel = if (period == BackupPeriod.ALL) "all" else "${cutoff.format(DATE_FORMAT)}-${LocalDate.now().format(DATE_FORMAT)}"
        return BackupFile("${table.tableName}_${today}_$periodLabel.xlsx", output.toByteArray())
    }

    private fun writeSheet(workbook: XSSFWorkbook, sheetName: String, headers: List<String>, rows: List<List<Any?>>) {
        val sheet = workbook.createSheet(sheetName)

        val headerRow = sheet.createRow(0)
        headers.forEachIndexed { i, header -> headerRow.createCell(i).setCellValue(header) }

        rows.forEachIndexed { rowIndex, row ->
            val sheetRow = sheet.createRow(rowIndex + 1)
            row.forEachIndexed { colIndex, value ->
                val cell = sheetRow.createCell(colIndex)
                when (value) {
                    null -> {}
                    is Number -> cell.setCellValue(value.toDouble())
                    is Boolean -> cell.setCellValue(value)
                    is LocalDate, is LocalDateTime, is OffsetDateTime -> cell.setCellValue(value.toString())
                    else -> cell.setCellValue(sanitizeCell(value.toString()))
                }
            }
        }
    }

    // 셀 값이 =, +, @, -로 시작하면 Excel/스프레드시트에서 수식으로 해석될 수 있어 앞에 '를 붙여 무력화
    private fun sanitizeCell(value: String): String =
        if (value.isNotEmpty() && value[0] in listOf('=', '+', '@', '-')) "'$value" else value
}
