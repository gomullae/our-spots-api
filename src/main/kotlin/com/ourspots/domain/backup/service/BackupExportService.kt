package com.ourspots.domain.backup.service

import com.ourspots.common.crypto.EncryptedLongConverter
import com.ourspots.common.errorlog.ErrorLogRepository
import com.ourspots.domain.auth.repository.AccessDeniedLogRepository
import com.ourspots.domain.auth.repository.LoginAttemptRepository
import com.ourspots.domain.backup.BackupPeriod
import com.ourspots.domain.backup.BackupTable
import com.ourspots.domain.expense.repository.ExpenseRecordRepository
import com.ourspots.domain.feedback.repository.FeedbackRepository
import com.ourspots.domain.household.repository.HouseholdBudgetItemRepository
import com.ourspots.domain.household.repository.HouseholdHistoryRepository
import com.ourspots.domain.household.repository.HouseholdIncomeRepository
import com.ourspots.domain.place.repository.PlaceRepository
import com.ourspots.domain.schedule.repository.ScheduleEventRepository
import com.ourspots.domain.weight.repository.WeightRecordRepository
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.springframework.stereotype.Service
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
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
    private val accessDeniedLogRepository: AccessDeniedLogRepository,
    private val scheduleEventRepository: ScheduleEventRepository,
    private val householdIncomeRepository: HouseholdIncomeRepository,
    private val householdBudgetItemRepository: HouseholdBudgetItemRepository,
    private val householdHistoryRepository: HouseholdHistoryRepository,
    private val encryptedLongConverter: EncryptedLongConverter
) {
    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd")
    }

    // period == ALL이면 기존 전체 조회를, RECENT_3_MONTHS면 DB 단에서 먼저 걸러진 조회를 씀 — login_attempts/
    // error_logs/access_denied_logs처럼 정리 배치 없이 계속 누적되는 테이블이 나중에 커져도 "최근 3개월"(기본값)을
    // 볼 때는 매번 테이블 전체를 메모리에 올리지 않도록 함(예전엔 항상 전체를 퍼온 뒤 코드에서 걸렀음)
    private fun <T> since(period: BackupPeriod, all: () -> List<T>, recent: () -> List<T>): List<T> =
        if (period == BackupPeriod.ALL) all() else recent()

    // 조회용(로그 이력 화면)과 엑셀 백업이 같은 데이터를 공유 — 날짜/시각 값은 여기서 문자열로 미리 변환해
    // JSON 직렬화든 엑셀 셀 기록이든 후속 소비자가 타입 분기 없이 그대로 쓸 수 있게 함
    fun fetchTableData(table: BackupTable, period: BackupPeriod): TableData {
        val cutoffDate = LocalDate.now().minusMonths(3)
        val cutoff: LocalDateTime = cutoffDate.atStartOfDay()
        // Feedback.createdAt만 OffsetDateTime(UTC 고정)이라 타입을 맞춰 별도로 준비
        val cutoffOffset: OffsetDateTime = cutoffDate.atStartOfDay(ZoneOffset.UTC).toOffsetDateTime()

        return when (table) {
            BackupTable.PLACES -> TableData(
                listOf("id", "name", "type", "address", "latitude", "longitude", "description", "grade", "googlePlaceId", "googleRating", "googleRatingsTotal", "createdAt", "updatedAt", "deletedAt"),
                since(period, placeRepository::findAllIncludingDeleted) { placeRepository.findAllIncludingDeletedSince(cutoff) }
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
                since(period, expenseRecordRepository::findAllIncludingDeleted) { expenseRecordRepository.findAllIncludingDeletedSince(cutoff) }
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
                since(period, weightRecordRepository::findAllIncludingDeleted) { weightRecordRepository.findAllIncludingDeletedSince(cutoff) }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(it.id, it.recordedDate.toString(), it.weightKg, it.memo, it.createdAt.toString(), it.updatedAt.toString(), it.deletedAt?.toString())
                    }
            )

            BackupTable.LOGIN_ATTEMPTS -> TableData(
                listOf("id", "ipAddress", "userAgent", "endpoint", "attemptCount", "blocked", "createdAt"),
                since(period, { loginAttemptRepository.findAll() }) { loginAttemptRepository.findAllSince(cutoff) }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(it.id, it.ipAddress, it.userAgent, it.endpoint, it.attemptCount, it.blocked, it.createdAt.toString())
                    }
            )

            BackupTable.FEEDBACKS -> TableData(
                listOf("id", "content", "ipAddress", "source", "createdAt"),
                since(period, { feedbackRepository.findAll() }) { feedbackRepository.findAllSince(cutoffOffset) }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(it.id, it.content, it.ipAddress, it.source, it.createdAt.toString())
                    }
            )

            BackupTable.ERROR_LOGS -> TableData(
                listOf("id", "exceptionType", "message", "method", "path", "createdAt"),
                since(period, { errorLogRepository.findAll() }) { errorLogRepository.findAllSince(cutoff) }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(it.id, it.exceptionType, it.message, it.method, it.path, it.createdAt.toString())
                    }
            )

            BackupTable.ACCESS_DENIED_LOGS -> TableData(
                listOf("id", "ipAddress", "method", "path", "message", "userAgent", "createdAt"),
                since(period, { accessDeniedLogRepository.findAll() }) { accessDeniedLogRepository.findAllSince(cutoff) }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(it.id, it.ipAddress, it.method, it.path, it.message, it.userAgent, it.createdAt.toString())
                    }
            )

            BackupTable.SCHEDULE_EVENTS -> TableData(
                listOf("id", "title", "category", "startAt", "endAt", "allDay", "memo", "createdAt", "updatedAt", "deletedAt"),
                since(period, scheduleEventRepository::findAllIncludingDeleted) { scheduleEventRepository.findAllIncludingDeletedSince(cutoff) }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(
                            it.id, it.title, it.category.name, it.startAt.toString(), it.endAt.toString(),
                            it.allDay, it.memo, it.createdAt.toString(), it.updatedAt.toString(), it.deletedAt?.toString()
                        )
                    }
            )

            // amount는 엔티티 로드 시점에 EncryptedLongConverter가 자동 복호화해서 평문 Long으로 들어오지만,
            // 백업 파일엔 그대로 내보내지 않고 이 컨버터로 다시 암호화한 값을 씀 — 복구 시 이 값을 그대로
            // amount 컬럼에 넣으면 되고(재암호화라 원본과 바이트는 다르지만 같은 키로 똑같이 복호화됨,
            // GCM은 매 암호화마다 IV가 달라져도 결과 평문은 동일), 백업 파일 자체엔 실제 금액이 평문으로
            // 안 남아서 파일이 유출돼도(다운로드 폴더, 이메일 첨부 등) 숫자가 그대로 노출되지 않음
            BackupTable.HOUSEHOLD_INCOMES -> TableData(
                listOf("id", "label", "amount", "memo", "createdAt", "updatedAt", "deletedAt"),
                since(period, { householdIncomeRepository.findAllForDashboard(true) }) { householdIncomeRepository.findAllIncludingDeletedSince(cutoff) }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(it.id, it.label, encryptedLongConverter.convertToDatabaseColumn(it.amount), it.memo, it.createdAt.toString(), it.updatedAt.toString(), it.deletedAt?.toString())
                    }
            )

            BackupTable.HOUSEHOLD_BUDGET_ITEMS -> TableData(
                listOf(
                    "id", "sectionType", "assetKind", "label", "vendor", "amount", "payer",
                    "autoDebitBank", "debitDay", "account", "plannedMonth", "memo", "createdAt", "updatedAt", "deletedAt"
                ),
                since(period, { householdBudgetItemRepository.findAllForDashboard(true) }) { householdBudgetItemRepository.findAllIncludingDeletedSince(cutoff) }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(
                            it.id, it.sectionType.name, it.assetKind?.name, it.label, it.vendor,
                            encryptedLongConverter.convertToDatabaseColumn(it.amount), it.payer?.name,
                            it.autoDebitBank?.name, it.debitDay, it.account?.name, it.plannedMonth, it.memo,
                            it.createdAt.toString(), it.updatedAt.toString(), it.deletedAt?.toString()
                        )
                    }
            )

            // append-only 로그라 소프트 삭제(deletedAt) 개념 자체가 없음
            BackupTable.HOUSEHOLD_HISTORY -> TableData(
                listOf(
                    "id", "itemType", "itemId", "action", "sectionType", "assetKind", "label", "vendor", "amount",
                    "payer", "autoDebitBank", "debitDay", "account", "plannedMonth", "memo", "createdAt"
                ),
                since(period, { householdHistoryRepository.findAll() }) { householdHistoryRepository.findAllSince(cutoff) }
                    .sortedByDescending { it.createdAt }
                    .map {
                        listOf(
                            it.id, it.itemType.name, it.itemId, it.action.name, it.sectionType?.name, it.assetKind?.name,
                            it.label, it.vendor, encryptedLongConverter.convertToDatabaseColumn(it.amount), it.payer?.name,
                            it.autoDebitBank?.name, it.debitDay, it.account?.name, it.plannedMonth, it.memo, it.createdAt.toString()
                        )
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
