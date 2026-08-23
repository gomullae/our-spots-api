package com.ourspots.domain.expense.controller

import com.ourspots.api.dto.ExpenseRecordRequest
import com.ourspots.api.dto.ExpenseRecordResponse
import com.ourspots.common.response.ApiResponse
import com.ourspots.domain.expense.service.ExpenseService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/expenses")
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
}
