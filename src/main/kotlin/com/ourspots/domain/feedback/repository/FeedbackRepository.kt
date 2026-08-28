package com.ourspots.domain.feedback.repository

import com.ourspots.domain.feedback.entity.Feedback
import org.springframework.data.jpa.repository.JpaRepository

interface FeedbackRepository : JpaRepository<Feedback, Long>
