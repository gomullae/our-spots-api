package com.ourspots.api.controller

import com.ourspots.common.response.ApiResponse
import com.ourspots.domain.backup.BackupPeriod
import com.ourspots.domain.backup.BackupTable
import com.ourspots.domain.backup.service.BackupExportService
import com.ourspots.domain.backup.service.TableData
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/logs")
class AdminLogController(
    private val backupExportService: BackupExportService
) {

    @GetMapping
    fun getLogs(
        @RequestParam table: BackupTable,
        @RequestParam(defaultValue = "RECENT_3_MONTHS") period: BackupPeriod
    ): ApiResponse<TableData> {
        return ApiResponse.success(backupExportService.fetchTableData(table, period))
    }
}
