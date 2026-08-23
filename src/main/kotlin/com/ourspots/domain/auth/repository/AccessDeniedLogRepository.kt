package com.ourspots.domain.auth.repository

import com.ourspots.domain.auth.entity.AccessDeniedLog
import org.springframework.data.jpa.repository.JpaRepository

interface AccessDeniedLogRepository : JpaRepository<AccessDeniedLog, Long>
