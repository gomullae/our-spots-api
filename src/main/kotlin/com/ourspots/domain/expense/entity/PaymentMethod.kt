package com.ourspots.domain.expense.entity

enum class PaymentMethod {
    WOW_CARD,         // 와우카드
    KB_CARD,          // 국민카드
    WOORI_CARD,       // 우리카드
    HYUNDAI_CARD,     // 현대카드
    CHOYOUNG_PAYMENT, // 초영결제 (배우자가 별도로 쓰는 결제수단)
    OTHER             // 기타 (카드 외 계좌이체 등도 포함하는 범용 값)
}
