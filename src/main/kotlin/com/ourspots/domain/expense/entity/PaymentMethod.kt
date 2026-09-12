package com.ourspots.domain.expense.entity

enum class PaymentMethod {
    WOW_CARD,         // 와우카드
    KB_CARD,          // 국민카드
    WOORI_CARD,       // 우리카드
    HYUNDAI_CARD,     // 현대카드
    JINWOO_IEUM_CARD,   // 진우이음카드 — 주간 정산 진우결제 합계에 포함(ExpenseService.categorySpend 참고)
    // 초영생활비통장(배우자가 별도로 쓰는 결제수단) — 상수명은 그대로 두고 화면 라벨만 "초영결제"에서
    // 변경(2026-09-12). DB엔 상수명 문자열이 저장되므로 라벨만 바뀌는 건 마이그레이션 불필요
    CHOYOUNG_PAYMENT,
    CHOYOUNG_IEUM_CARD, // 초영이음카드 — 주간 정산 초영결제(=CHOYOUNG_PAYMENT) 합계에 포함
    OTHER,            // 기타 (카드 외 계좌이체 등도 포함하는 범용 값)
    // 실제 결제수단이 아니라 할인/환급 등으로 "따로 받은 돈" — amount는 항상 양수로 저장하고
    // 합산 시점(ExpenseService)에 부호를 뒤집어 총액에서 차감함(ExpenseRecord.effectiveAmount() 참고)
    SUBSIDY           // 지원금
}
