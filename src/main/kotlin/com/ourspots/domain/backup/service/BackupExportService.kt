package com.ourspots.domain.backup.service

import com.ourspots.common.errorlog.ErrorLogRepository
import com.ourspots.domain.auth.repository.AccessDeniedLogRepository
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
import java.time.format.DateTimeFormatter

data class BackupFile(val filename: String, val bytes: ByteArray)
data class TableData(val headers: List<String>, val rows: List<List<Any?>>)

@Service
class BackupExportService(
    private val placeRepository: PlaceRepository,
    private val expenseRecordRepository: ExpenseRecordRepository,
    private val weightRecordRepository: WeightRecordRepository,
    private val loginAttemptRepository: LoginAttemptRepository,
    private val feedbackRepository: FeedbackRepository,
    private val errorLogRepository: ErrorLogRepository,
    private val accessDeniedLogRepository: AccessDeniedLogRepository
) {
    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    // period == RECENT_3_MONTHS일 때만 cutoff 이전 행을 걸러냄 — 7개 테이블 분기 모두가 공유하는 필터 조건
    private fun <T> List<T>.filterByPeriod(period: BackupPeriod, cutoff: LocalDate, createdAtOf: (T) -> LocalDate): List<T> =
        if (period == BackupPeriod.ALL) this else filter { !createdAtOf(it).isBefore(cutoff) }

    // 조회용(로그 이력 화면)과 엑셀 백업이 같은 데이터를 공유 — 날짜/시각 값은 여기서 문자열로 미리 변환해
    // JSON 직렬화든 엑셀 셀 기록이든 후속 소비자가 타입 분기 없이 그대로 쓸 수 있게 함
    fun fetchTableData(table: BackupTable, period: BackupPeriod): TableData {
        val cutoff = LocalDate.now().minusMonths(3)

        return when (table) {
            BackupTable.PLACES -> TableData(
                listOf("id", "name", "type", "address", "latitude", "longitude", "description", "grade", "googlePlaceId", "googleRating", "googleRatingsTotal", "createdAt", "updatedAt", "deletedAt"),
                placeRepository.findAllIncludingDeleted()
                    .filterByPeriod(period, cutoff) { it.createdAt.toLocalDate() }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(
                            it.id, it.name, it.type.name, it.address, it.latitude, it.longitude,
                            it.description, it.grade, it.googlePlaceId, it.googleRating, it.googleRatingsTotal,
                            it.createdAt.toString(), it.updatedAt.toString(), it.deletedAt?.toString()
                        )
                    }
            )

            BackupTable.EXPENSE_RECORDS -> TableData(
                listOf("id", "expenseDate", "paymentMethod", "category", "merchant", "amount", "createdAt", "updatedAt", "deletedAt"),
                expenseRecordRepository.findAllIncludingDeleted()
                    .filterByPeriod(period, cutoff) { it.createdAt.toLocalDate() }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(
                            it.id, it.expenseDate.toString(), it.paymentMethod.name, it.category.name, it.merchant, it.amount,
                            it.createdAt.toString(), it.updatedAt.toString(), it.deletedAt?.toString()
                        )
                    }
            )

            BackupTable.WEIGHT_RECORDS -> TableData(
                listOf("id", "recordedDate", "weightKg", "memo", "createdAt", "updatedAt", "deletedAt"),
                weightRecordRepository.findAllIncludingDeleted()
                    .filterByPeriod(period, cutoff) { it.createdAt.toLocalDate() }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(it.id, it.recordedDate.toString(), it.weightKg, it.memo, it.createdAt.toString(), it.updatedAt.toString(), it.deletedAt?.toString())
                    }
            )

            BackupTable.LOGIN_ATTEMPTS -> TableData(
                listOf("id", "ipAddress", "userAgent", "endpoint", "attemptCount", "blocked", "createdAt"),
                loginAttemptRepository.findAll()
                    .filterByPeriod(period, cutoff) { it.createdAt.toLocalDate() }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(it.id, it.ipAddress, it.userAgent, it.endpoint, it.attemptCount, it.blocked, it.createdAt.toString())
                    }
            )

            BackupTable.FEEDBACKS -> TableData(
                listOf("id", "content", "ipAddress", "source", "createdAt"),
                feedbackRepository.findAll()
                    .filterByPeriod(period, cutoff) { it.createdAt.toLocalDate() }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(it.id, it.content, it.ipAddress, it.source, it.createdAt.toString())
                    }
            )

            BackupTable.ERROR_LOGS -> TableData(
                listOf("id", "exceptionType", "message", "method", "path", "createdAt"),
                errorLogRepository.findAll()
                    .filterByPeriod(period, cutoff) { it.createdAt.toLocalDate() }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(it.id, it.exceptionType, it.message, it.method, it.path, it.createdAt.toString())
                    }
            )

            BackupTable.ACCESS_DENIED_LOGS -> TableData(
                listOf("id", "ipAddress", "method", "path", "message", "userAgent", "createdAt"),
                accessDeniedLogRepository.findAll()
                    .filterByPeriod(period, cutoff) { it.createdAt.toLocalDate() }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(it.id, it.ipAddress, it.method, it.path, it.message, it.userAgent, it.createdAt.toString())
                    }
            )
        }
    }

    fun export(table: BackupTable, period: BackupPeriod): BackupFile {
        val data = fetchTableData(table, period)

        val workbook = XSSFWorkbook()
        writeSheet(workbook, table.tableName, data.headers, data.rows)

        val output = ByteArrayOutputStream()
        workbook.use { it.write(output) }

        val today = LocalDate.now().format(DATE_FORMAT)
        val cutoff = LocalDate.now().minusMonths(3)
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
                    else -> cell.setCellValue(sanitizeCell(value.toString()))
                }
            }
        }
    }

    // 셀 값이 =, +, @, -로 시작하면 Excel/스프레드시트에서 수식으로 해석될 수 있어 앞에 '를 붙여 무력화
    private fun sanitizeCell(value: String): String =
        if (value.isNotEmpty() && value[0] in listOf('=', '+', '@', '-')) "'$value" else value
}
