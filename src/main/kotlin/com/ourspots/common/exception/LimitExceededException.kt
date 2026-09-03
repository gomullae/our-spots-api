package com.ourspots.common.exception

// 개수 상한처럼 Bean Validation으로는 표현 못 하는(DB 상태를 조회해야 아는) 제약 위반용 — 400 Bad Request
class LimitExceededException(message: String) : RuntimeException(message)
