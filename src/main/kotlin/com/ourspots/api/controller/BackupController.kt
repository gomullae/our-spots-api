package com.ourspots.api.controller

import com.ourspots.domain.backup.BackupPeriod
import com.ourspots.domain.backup.BackupTable
import com.ourspots.domain.backup.service.BackupExportService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/admin/backup")
class BackupController(
    private val backupExportService: BackupExportService
) {

    @GetMapping
    fun download(
        @RequestParam table: BackupTable,
        @RequestParam(defaultValue = "ALL") period: BackupPeriod
    ): ResponseEntity<ByteArray> {
        val file = backupExportService.export(table, period)
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"${file.filename}\"")
            .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
            .body(file.bytes)
    }
}
