package com.ourspots.domain.household.entity

// household_budget_items의 4개 섹션 — 원래 스프레드시트의 "월비용/자산/지출예정액/고정지출" 4개 표에
// 대응. 행 수가 적고(다 합쳐 수십 건) 공통 필드가 대부분이라 테이블 4개로 쪼개지 않고 이 enum 하나로 구분
enum class HouseholdSectionType { FIXED_COST, ASSET, PLANNED_EXPENSE, SUBSCRIPTION }

// sectionType=ASSET인 행에서만 사용(자산/부채 구분) — 순자산 = ASSET 합계 - LIABILITY 합계
enum class HouseholdAssetKind { ASSET, LIABILITY }

// 대상자 — 부부 개인 지출(JINWOO/CHOYOUNG) + 가족 공용 지출(FAMILY) 3개
enum class HouseholdPayer { JINWOO, CHOYOUNG, FAMILY }

enum class HouseholdHistoryAction { CREATE, UPDATE, DELETE, RESTORE }

// HouseholdHistory 한 테이블이 HouseholdIncome/HouseholdBudgetItem 두 종류를 다 기록하기 위한 구분자
enum class HouseholdHistoryItemType { INCOME, BUDGET_ITEM }

// household_budget_items.account(연결계좌) — 고정비가 실제로 빠져나가는 계좌. 원래 자유 텍스트였는데
// 프론트 계좌 소계 그룹핑(fixedCostGroups)이 이 값의 정확한 일치로 묶다 보니, 오타 하나로 소계 그룹이
// 조용히 갈라지는 문제가 있어 PaymentMethod(생활비 결제수단)와 동일한 패턴(고정 enum + OTHER)으로 전환
enum class HouseholdAccount {
    UTILITY_ACCOUNT, // 공과금통장
    JINWOO_ACCOUNT,  // 진우통장
    LIVING_ACCOUNT,  // 생활비통장
    OTHER            // 기타
}

// household_budget_items.autoDebitBank(자동이체 은행) — 고정비/구독료가 실제로 결제되는 수단. 은행뿐
// 아니라 카드/계좌도 섞여 있어서 필드명은 "은행"이지만 실제 값은 결제 소스 전반을 포괄. account와 동일한
// 이유로 자유 텍스트 대신 enum으로 전환(프론트 정렬 기준 키(sortByAutoDebitAndDebitDay)라 오타에 취약)
enum class HouseholdAutoDebitSource {
    SHINHAN_BANK,     // 신한은행
    WOORI_BANK,       // 우리은행
    CHOYOUNG_ACCOUNT, // 초영통장
    HYUNDAI_CARD,     // 현대카드
    KB_CARD,          // 국민카드
    OTHER             // 기타
}
