package com.ourspots.domain.household.controller

import com.ourspots.api.dto.HouseholdBudgetItemRequest
import com.ourspots.api.dto.HouseholdBudgetItemResponse
import com.ourspots.api.dto.HouseholdBudgetMetaResponse
import com.ourspots.api.dto.HouseholdBudgetOverviewResponse
import com.ourspots.api.dto.HouseholdHistoryResponse
import com.ourspots.api.dto.HouseholdIncomeRequest
import com.ourspots.api.dto.HouseholdIncomeResponse
import com.ourspots.common.response.ApiResponse
import com.ourspots.domain.household.service.HouseholdBudgetService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

// 전부 관리자 전용(AdminOnlyInterceptor, WebMvcConfig 참고) — 가계부보다도 민감한 데이터(순자산/급여)라
// 동일한 보안 경계 안에 두되, 금액 자체는 DB에 암호화 저장(EncryptedLongConverter)까지 추가함
@RestController
@RequestMapping("/api/household-budget")
class HouseholdBudgetController(
    private val householdBudgetService: HouseholdBudgetService
) {

    @GetMapping
    fun getOverview(@RequestParam(defaultValue = "false") includeDeleted: Boolean): ApiResponse<HouseholdBudgetOverviewResponse> =
        ApiResponse.success(householdBudgetService.getOverview(includeDeleted))

    // 프론트 localStorage 캐시 검증용 — WeightController의 /meta와 동일 패턴
    @GetMapping("/meta")
    fun getMeta(): ApiResponse<HouseholdBudgetMetaResponse> =
        ApiResponse.success(householdBudgetService.getMeta())

    @PostMapping("/incomes")
    @ResponseStatus(HttpStatus.CREATED)
    fun createIncome(@Valid @RequestBody request: HouseholdIncomeRequest): ApiResponse<HouseholdIncomeResponse> =
        ApiResponse.success(householdBudgetService.createIncome(request))

    @PutMapping("/incomes/{id}")
    fun updateIncome(@PathVariable id: Long, @Valid @RequestBody request: HouseholdIncomeRequest): ApiResponse<HouseholdIncomeResponse> =
        ApiResponse.success(householdBudgetService.updateIncome(id, request))

    @DeleteMapping("/incomes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteIncome(@PathVariable id: Long) {
        householdBudgetService.deleteIncome(id)
    }

    @PostMapping("/incomes/{id}/restore")
    fun restoreIncome(@PathVariable id: Long): ApiResponse<HouseholdIncomeResponse> =
        ApiResponse.success(householdBudgetService.restoreIncome(id))

    @GetMapping("/incomes/{id}/history")
    fun getIncomeHistory(@PathVariable id: Long): ApiResponse<List<HouseholdHistoryResponse>> =
        ApiResponse.success(householdBudgetService.getIncomeHistory(id))

    @PostMapping("/items")
    @ResponseStatus(HttpStatus.CREATED)
    fun createItem(@Valid @RequestBody request: HouseholdBudgetItemRequest): ApiResponse<HouseholdBudgetItemResponse> =
        ApiResponse.success(householdBudgetService.createItem(request))

    @PutMapping("/items/{id}")
    fun updateItem(@PathVariable id: Long, @Valid @RequestBody request: HouseholdBudgetItemRequest): ApiResponse<HouseholdBudgetItemResponse> =
        ApiResponse.success(householdBudgetService.updateItem(id, request))

    @DeleteMapping("/items/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteItem(@PathVariable id: Long) {
        householdBudgetService.deleteItem(id)
    }

    @PostMapping("/items/{id}/restore")
    fun restoreItem(@PathVariable id: Long): ApiResponse<HouseholdBudgetItemResponse> =
        ApiResponse.success(householdBudgetService.restoreItem(id))

    @GetMapping("/items/{id}/history")
    fun getItemHistory(@PathVariable id: Long): ApiResponse<List<HouseholdHistoryResponse>> =
        ApiResponse.success(householdBudgetService.getItemHistory(id))
}
