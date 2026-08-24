package com.ourspots.domain.backup.service

import com.ourspots.common.errorlog.ErrorLog
import com.ourspots.common.errorlog.ErrorLogRepository
import com.ourspots.domain.auth.entity.AccessDeniedLog
import com.ourspots.domain.auth.entity.LoginAttempt
import com.ourspots.domain.auth.repository.AccessDeniedLogRepository
import com.ourspots.domain.auth.repository.LoginAttemptRepository
import com.ourspots.domain.backup.BackupPeriod
import com.ourspots.domain.backup.BackupTable
import com.ourspots.domain.expense.entity.ExpenseCategory
import com.ourspots.domain.expense.entity.ExpenseRecord
import com.ourspots.domain.expense.entity.PaymentMethod
import com.ourspots.domain.expense.repository.ExpenseRecordRepository
import com.ourspots.domain.feedback.entity.Feedback
import com.ourspots.domain.feedback.repository.FeedbackRepository
import com.ourspots.domain.place.entity.Place
import com.ourspots.domain.place.entity.PlaceType
import com.ourspots.domain.place.repository.PlaceRepository
import com.ourspots.domain.weight.entity.WeightRecord
import com.ourspots.domain.weight.repository.WeightRecordRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BackupExportServiceTest {

    @MockK
    private lateinit var placeRepository: PlaceRepository

    @MockK
    private lateinit var expenseRecordRepository: ExpenseRecordRepository

    @MockK
    private lateinit var weightRecordRepository: WeightRecordRepository

    @MockK
    private lateinit var loginAttemptRepository: LoginAttemptRepository

    @MockK
    private lateinit var feedbackRepository: FeedbackRepository

    @MockK
    private lateinit var errorLogRepository: ErrorLogRepository

    @MockK
    private lateinit var accessDeniedLogRepository: AccessDeniedLogRepository

    @InjectMockKs
    private lateinit var backupExportService: BackupExportService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
    }

    @Nested
    @DisplayName("fetchTableData")
    inner class FetchTableData {

        @Test
        fun fetchTableData_whenPlacesAndPeriodAll_shouldReturnAllSortedByCreatedAtDesc() {
            val old = Place(
                id = 1, name = "오래된 곳", type = PlaceType.RESTAURANT, address = "주소1",
                latitude = 37.0, longitude = 127.0, createdAt = LocalDateTime.now().minusMonths(6)
            )
            val recent = Place(
                id = 2, name = "최근 등록", type = PlaceType.RESTAURANT, address = "주소2",
                latitude = 37.1, longitude = 127.1, createdAt = LocalDateTime.now()
            )
            every { placeRepository.findAllIncludingDeleted() } returns listOf(old, recent)

            val result = backupExportService.fetchTableData(BackupTable.PLACES, BackupPeriod.ALL)

            assertEquals(listOf("id", "name", "type", "address", "latitude", "longitude", "description", "grade", "googlePlaceId", "googleRating", "googleRatingsTotal", "createdAt", "updatedAt", "deletedAt"), result.headers)
            assertEquals(2, result.rows.size)
            // 최신순 정렬 확인 — 첫 행이 recent(id=2)
            assertEquals(2L, result.rows[0][0])
            assertEquals(1L, result.rows[1][0])
        }

        @Test
        fun fetchTableData_whenPlacesAndPeriodRecent3Months_shouldExcludeOlderRows() {
            val old = Place(
                id = 1, name = "오래된 곳", type = PlaceType.RESTAURANT, address = "주소1",
                latitude = 37.0, longitude = 127.0, createdAt = LocalDateTime.now().minusMonths(6)
            )
            val recent = Place(
                id = 2, name = "최근 등록", type = PlaceType.RESTAURANT, address = "주소2",
                latitude = 37.1, longitude = 127.1, createdAt = LocalDateTime.now()
            )
            every { placeRepository.findAllIncludingDeleted() } returns listOf(old, recent)

            val result = backupExportService.fetchTableData(BackupTable.PLACES, BackupPeriod.RECENT_3_MONTHS)

            assertEquals(1, result.rows.size)
            assertEquals(2L, result.rows[0][0])
        }

        @Test
        fun fetchTableData_whenExpenseRecords_shouldMapAllFields() {
            val record = ExpenseRecord(
                id = 10, expenseDate = LocalDate.of(2026, 8, 19), paymentMethod = PaymentMethod.KB_CARD,
                category = ExpenseCategory.FOOD, merchant = "이마트", amount = 30000
            )
            every { expenseRecordRepository.findAllIncludingDeleted() } returns listOf(record)

            val result = backupExportService.fetchTableData(BackupTable.EXPENSE_RECORDS, BackupPeriod.ALL)

            assertEquals(listOf("id", "expenseDate", "paymentMethod", "category", "merchant", "amount", "createdAt", "updatedAt", "deletedAt"), result.headers)
            assertEquals(listOf(10L, "2026-08-19", "KB_CARD", "FOOD", "이마트", 30000L), result.rows[0].take(6))
        }

        @Test
        fun fetchTableData_whenWeightRecords_shouldMapAllFields() {
            val record = WeightRecord(id = 5, recordedDate = LocalDate.of(2026, 8, 19), weightKg = 70.5, memo = "아침")
            every { weightRecordRepository.findAllIncludingDeleted() } returns listOf(record)

            val result = backupExportService.fetchTableData(BackupTable.WEIGHT_RECORDS, BackupPeriod.ALL)

            assertEquals(listOf("id", "recordedDate", "weightKg", "memo", "createdAt", "updatedAt", "deletedAt"), result.headers)
            assertEquals(listOf(5L, "2026-08-19", 70.5, "아침"), result.rows[0].take(4))
        }

        @Test
        fun fetchTableData_whenLoginAttempts_shouldMapAllFields() {
            val attempt = LoginAttempt(id = 1, ipAddress = "1.2.3.4", userAgent = "curl", endpoint = "/api/auth/login", attemptCount = 3, blocked = true)
            every { loginAttemptRepository.findAll() } returns listOf(attempt)

            val result = backupExportService.fetchTableData(BackupTable.LOGIN_ATTEMPTS, BackupPeriod.ALL)

            assertEquals(listOf("id", "ipAddress", "userAgent", "endpoint", "attemptCount", "blocked", "createdAt"), result.headers)
            assertEquals(listOf(1L, "1.2.3.4", "curl", "/api/auth/login", 3, true), result.rows[0].take(6))
        }

        @Test
        fun fetchTableData_whenFeedbacks_shouldMapAllFields() {
            val feedback = Feedback(id = 1, content = "좋아요", ipAddress = "1.2.3.4", createdAt = OffsetDateTime.now(ZoneOffset.UTC), source = "our-spots")
            every { feedbackRepository.findAll() } returns listOf(feedback)

            val result = backupExportService.fetchTableData(BackupTable.FEEDBACKS, BackupPeriod.ALL)

            assertEquals(listOf("id", "content", "ipAddress", "source", "createdAt"), result.headers)
            assertEquals(listOf(1L, "좋아요", "1.2.3.4", "our-spots"), result.rows[0].take(4))
        }

        @Test
        fun fetchTableData_whenErrorLogs_shouldMapAllFields() {
            val errorLog = ErrorLog(id = 1, exceptionType = "RuntimeException", message = "실패", method = "GET", path = "/api/places", stackTrace = "...")
            every { errorLogRepository.findAll() } returns listOf(errorLog)

            val result = backupExportService.fetchTableData(BackupTable.ERROR_LOGS, BackupPeriod.ALL)

            assertEquals(listOf("id", "exceptionType", "message", "method", "path", "createdAt"), result.headers)
            assertEquals(listOf(1L, "RuntimeException", "실패", "GET", "/api/places"), result.rows[0].take(5))
        }

        @Test
        fun fetchTableData_whenAccessDeniedLogs_shouldMapAllFields() {
            val log = AccessDeniedLog(id = 1, ipAddress = "1.2.3.4", method = "GET", path = "/api/weights", message = "Unauthorized", userAgent = "curl")
            every { accessDeniedLogRepository.findAll() } returns listOf(log)

            val result = backupExportService.fetchTableData(BackupTable.ACCESS_DENIED_LOGS, BackupPeriod.ALL)

            assertEquals(listOf("id", "ipAddress", "method", "path", "message", "userAgent", "createdAt"), result.headers)
            assertEquals(listOf(1L, "1.2.3.4", "GET", "/api/weights", "Unauthorized", "curl"), result.rows[0].take(6))
        }
    }

    @Nested
    @DisplayName("export")
    inner class Export {

        @Test
        fun export_shouldProduceNonEmptyXlsxWithExpectedFilename() {
            val place = Place(id = 1, name = "장소", type = PlaceType.RESTAURANT, address = "주소", latitude = 37.0, longitude = 127.0)
            every { placeRepository.findAllIncludingDeleted() } returns listOf(place)

            val file = backupExportService.export(BackupTable.PLACES, BackupPeriod.ALL)

            assertTrue(file.filename.startsWith("places_"))
            assertTrue(file.filename.endsWith("_all.xlsx"))
            assertTrue(file.bytes.isNotEmpty())
        }

        @Test
        fun export_withRecentPeriod_shouldUseDateRangeInFilename() {
            every { expenseRecordRepository.findAllIncludingDeleted() } returns emptyList()

            val file = backupExportService.export(BackupTable.EXPENSE_RECORDS, BackupPeriod.RECENT_3_MONTHS)

            assertTrue(file.filename.startsWith("expense_records_"))
            assertTrue(Regex("expense_records_\\d{8}_\\d{8}-\\d{8}\\.xlsx").matches(file.filename))
        }

        @Test
        fun export_whenCellStartsWithFormulaChar_shouldPrefixWithQuoteToPreventInjection() {
            val place = Place(id = 1, name = "=SUM(A1)", type = PlaceType.RESTAURANT, address = "주소", latitude = 37.0, longitude = 127.0)
            every { placeRepository.findAllIncludingDeleted() } returns listOf(place)

            val file = backupExportService.export(BackupTable.PLACES, BackupPeriod.ALL)

            org.apache.poi.xssf.usermodel.XSSFWorkbook(file.bytes.inputStream()).use { workbook ->
                val sheet = workbook.getSheet("places")
                val nameCell = sheet.getRow(1).getCell(1)
                assertEquals("'=SUM(A1)", nameCell.stringCellValue)
            }
        }
    }
}
