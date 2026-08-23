package com.ourspots.domain.auth.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "access_denied_logs")
class AccessDeniedLog(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 255)
    val ipAddress: String,

    @Column(nullable = false, length = 10)
    val method: String,

    @Column(nullable = false, length = 255)
    val path: String,

    @Column(length = 255)
    val message: String? = null,

    @Column(length = 500)
    val userAgent: String? = null,

    @Column(nullable = false, updatable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
)
