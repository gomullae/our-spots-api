package com.ourspots.batch

import com.ourspots.domain.expense.entity.ExpenseRecord
import com.ourspots.domain.expense.repository.ExpenseRecordRepository
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationArguments
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.File

// 엑셀 헤더는 반드시 이 이름 그대로: 카드사 / 구분 / 사용처 / 금액 / 지출일자
// 행 검증/파싱 로직 자체는 ExpenseRowParser(순수 로직, 단위 테스트 대상)에 위임한다.
@Component
@Profile("batch")
@ConditionalOnProperty(name = ["batch.job"], havingValue = "import-expenses")
class ImportExpensesRunner(
    private val expenseRecordRepository: ExpenseRecordRepository
) : ApplicationRunner {

    private val log = LoggerFactory.getLogger(javaClass)

    override fun run(args: ApplicationArguments) {
        val filePath = args.getOptionValues("file")?.firstOrNull()
        if (filePath.isNullOrBlank()) {
            log.error("--file 파라미터가 필요합니다. 예: --file=/path/to/file.xlsx")
            return
        }

        val file = File(filePath)
        if (!file.exists()) {
            log.error("파일을 찾을 수 없습니다: $filePath")
            return
        }

        log.info("엑셀 생활비 일괄 등록 시작")
        log.info("파일: $filePath")

        val rows = readExcel(file)
        log.info("총 ${rows.size}행 발견")

        val valid = mutableListOf<ExpenseRecord>()
        val invalid = mutableListOf<String>()

        for ((index, row) in rows.withIndex()) {
            val excelRowNum = index + 2 // 1행은 헤더이므로 실제 데이터는 2행부터

            when (val result = ExpenseRowParser.parseRow(row)) {
                is ExpenseRowParser.RowResult.Valid -> {
                    val r = result.row
                    valid.add(
                        ExpenseRecord(
                            expenseDate = r.expenseDate,
                            paymentMethod = r.paymentMethod,
                            category = r.category,
                            merchant = r.merchant,
                            amount = r.amount
                        )
                    )
                }
                is ExpenseRowParser.RowResult.Invalid -> {
                    invalid.add("${excelRowNum}행 (${result.errors.joinToString(", ")})")
                }
            }
        }

        // 검증 실패 행이 하나라도 있으면 등록 자체를 하지 않음 — 절반만 들어간 상태가 되면
        // 어디까지 반영됐는지 헷갈리기 때문에, 엑셀을 전부 고친 뒤 다시 통째로 실행하는 걸 강제한다.
        if (invalid.isNotEmpty()) {
            log.warn("검증 실패 행이 있어 아무것도 등록하지 않았습니다.")
            log.warn("실패한 행 (엑셀을 이 행들만 수정해서 --file로 재실행하면 됨):")
            invalid.forEach { log.warn("  - $it") }
            log.info("========== 결과 ==========")
            log.info("등록: 0개 (검증 실패 ${invalid.size}개로 전체 취소)")
            log.info("==========================")
            return
        }

        expenseRecordRepository.saveAll(valid)

        log.info("========== 결과 ==========")
        log.info("성공: ${valid.size}개")
        log.info("==========================")
    }

    private fun readExcel(file: File): List<Map<String, String>> {
        val workbook = WorkbookFactory.create(file)
        val sheet = workbook.getSheetAt(0)

        val headerRow = sheet.getRow(0) ?: return emptyList()
        val headers = (0 until headerRow.lastCellNum).map { i ->
            headerRow.getCell(i)?.stringCellValue?.trim() ?: ""
        }

        val rows = (1..sheet.lastRowNum).mapNotNull { i ->
            val row = sheet.getRow(i) ?: return@mapNotNull null
            val map = headers.withIndex()
                .filter { (_, header) -> header.isNotBlank() }
                .associate { (j, header) -> header to extractCellValue(row.getCell(j)) }
            map.takeIf { it.values.any { v -> v.isNotBlank() } }
        }

        workbook.close()
        return rows
    }

    private fun extractCellValue(cell: Cell?): String {
        if (cell == null) return ""
        return when (cell.cellType) {
            CellType.STRING -> cell.stringCellValue.trim()
            CellType.NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    cell.localDateTimeCellValue.toLocalDate().toString()
                } else {
                    val num = cell.numericCellValue
                    if (num == num.toLong().toDouble()) num.toLong().toString() else num.toString()
                }
            }
            CellType.BOOLEAN -> cell.booleanCellValue.toString()
            else -> ""
        }
    }
}
