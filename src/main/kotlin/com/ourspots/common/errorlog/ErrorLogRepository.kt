package com.ourspots.common.errorlog

import org.springframework.data.jpa.repository.JpaRepository

interface ErrorLogRepository : JpaRepository<ErrorLog, Long>
