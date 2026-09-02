package com.ourspots.domain.backup

enum class BackupTable(val tableName: String) {
    PLACES("places"),
    EXPENSE_RECORDS("expense_records"),
    WEIGHT_RECORDS("weight_records"),
    LOGIN_ATTEMPTS("login_attempts"),
    FEEDBACKS("feedbacks"),
    ERROR_LOGS("error_logs"),
    ACCESS_DENIED_LOGS("access_denied_logs"),
    SCHEDULE_EVENTS("schedule_events"),
    HOUSEHOLD_INCOMES("household_incomes"),
    HOUSEHOLD_BUDGET_ITEMS("household_budget_items"),
    HOUSEHOLD_HISTORY("household_history")
}
