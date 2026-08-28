package com.ourspots.domain.auth.repository

import com.ourspots.domain.auth.entity.LoginAttempt
import org.springframework.data.jpa.repository.JpaRepository

interface LoginAttemptRepository : JpaRepository<LoginAttempt, Long>
