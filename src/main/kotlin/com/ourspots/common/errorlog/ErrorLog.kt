package com.ourspots.common.errorlog

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "error_logs")
class ErrorLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 255)
    val exceptionType: String,

    @Column(length = 1000)
    val message: String? = null,

    @Column(length = 10)
    val method: String? = null,

    @Column(length = 255)
    val path: String? = null,

    @Column(columnDefinition = "TEXT")
    val stackTrace: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
