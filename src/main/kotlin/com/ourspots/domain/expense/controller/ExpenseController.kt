package com.ourspots.domain.expense.controller

import com.ourspots.api.dto.ExpenseMetaResponse
import com.ourspots.api.dto.ExpenseRecordRequest
import com.ourspots.api.dto.ExpenseRecordResponse
import com.ourspots.common.response.ApiResponse
import com.ourspots.domain.expense.service.ExpenseService
import jakarta.validation.Valid
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/expenses")
@Validated
class ExpenseController(
    private val expenseService: ExpenseService
) {

    @GetMapping
    fun getRecords(
        @RequestParam startDate: LocalDate,
        @RequestParam endDate: LocalDate,
        @RequestParam(defaultValue = "false") includeDeleted: Boolean
    ): ApiResponse<List<ExpenseRecordResponse>> =
        ApiResponse.success(expenseService.getRecords(startDate, endDate, includeDeleted))

    // 프론트가 로컬(localStorage) 캐시를 그대로 써도 되는지 확인하는 가벼운 엔드포인트 — 전체 목록 대신 count/lastModified만 반환
    @GetMapping("/meta")
    fun getMeta(): ApiResponse<ExpenseMetaResponse> =
        ApiResponse.success(expenseService.getMeta())

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun createRecord(
        @Valid @RequestBody request: ExpenseRecordRequest
    ): ApiResponse<ExpenseRecordResponse> =
        ApiResponse.success(expenseService.createRecord(request))

    @PutMapping("/{id}")
    fun updateRecord(
        @PathVariable id: Long,
        @Valid @RequestBody request: ExpenseRecordRequest
    ): ApiResponse<ExpenseRecordResponse> =
        ApiResponse.success(expenseService.updateRecord(id, request))

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteRecord(@PathVariable id: Long) {
        expenseService.deleteRecord(id)
    }

    @PostMapping("/{id}/restore")
    fun restoreRecord(@PathVariable id: Long): ApiResponse<ExpenseRecordResponse> =
        ApiResponse.success(expenseService.restoreRecord(id))

    @PostMapping("/weekly-summary")
    fun sendWeeklySummary(
        @RequestParam startDate: LocalDate,
        @RequestParam endDate: LocalDate,
        @RequestParam @Positive budget: Long
    ): ApiResponse<Unit> {
        expenseService.sendWeeklySummary(startDate, endDate, budget)
        return ApiResponse.success(Unit)
    }
}
