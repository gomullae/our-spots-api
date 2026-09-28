package com.ourspots.common.exception

// isTokenExpired: 서명은 정상인데 유효기간만 지나서 막힌 경우 true — GlobalExceptionHandler가
// 이 경우엔 access_denied_logs엔 그대로 남기되 텔레그램 알림은 건너뜀(정상 로그인했던 세션이
// 그냥 만료된 것뿐이라 "비정상 접근"으로 보기 어려움)
class UnauthorizedException(message: String, val isTokenExpired: Boolean = false) : RuntimeException(message)
