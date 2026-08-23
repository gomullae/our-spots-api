package com.ourspots.domain.backup

enum class BackupTable(val tableName: String) {
    PLACES("places"),
    EXPENSE_RECORDS("expense_records"),
    WEIGHT_RECORDS("weight_records"),
    LOGIN_ATTEMPTS("login_attempts"),
    FEEDBACKS("feedbacks")
}
